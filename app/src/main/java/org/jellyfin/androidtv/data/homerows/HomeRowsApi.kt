package org.jellyfin.androidtv.data.homerows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Client for the personal fork server's HomeRows API (Custom Home Rows). The SDK has no HomeRows endpoints, can't send
 * PUT, and drops error bodies, so calls go straight through OkHttp with the SDK's authorization header.
 */

data class HomeRowsHttpResult(val status: Int, val body: String)

/** Sends one HomeRows request. Throws only on network failure. */
interface HomeRowsTransport {
	suspend fun send(
		method: String,
		path: String,
		query: Map<String, String> = emptyMap(),
		body: String? = null,
	): HomeRowsHttpResult
}

/**
 * The OkHttp body for a request: [body] as JSON, or an empty body for a POST, PUT or PATCH without one (OkHttp refuses
 * those methods without a body, such as an item action's POST), or none.
 */
internal fun requestBodyFor(method: String, body: String?): RequestBody? = when {
	body != null -> body.toRequestBody("application/json".toMediaType())
	method in methodsNeedingBody -> ByteArray(0).toRequestBody()
	else -> null
}

private val methodsNeedingBody = setOf("POST", "PUT", "PATCH")

class OkHttpHomeRowsTransport(
	private val api: ApiClient,
	private val okHttpFactory: OkHttpFactory,
) : HomeRowsTransport {
	private val client by lazy { okHttpFactory.createClient(api.httpClientOptions) }

	override suspend fun send(method: String, path: String, query: Map<String, String>, body: String?): HomeRowsHttpResult {
		val baseUrl = requireNotNull(api.baseUrl) { "No server" }
		val url = "${baseUrl.trimEnd('/')}/$path".toHttpUrl().newBuilder().apply {
			for ((key, value) in query) addQueryParameter(key, value)
		}.build()
		val authorization = AuthorizationHeaderBuilder.buildHeader(
			api.clientInfo.name,
			api.clientInfo.version,
			api.deviceInfo.id,
			api.deviceInfo.name,
			api.accessToken,
		)
		val request = Request.Builder()
			.url(url)
			.header("Authorization", authorization)
			.header("Accept", "application/json")
			.method(method, requestBodyFor(method, body))
			.build()

		// Enqueued so that cancelling the coroutine (such as a row timeout) cancels the request.
		val call = client.newCall(request)
		return suspendCancellableCoroutine { continuation ->
			continuation.invokeOnCancellation { call.cancel() }
			call.enqueue(object : Callback {
				override fun onFailure(call: Call, e: IOException) {
					continuation.resumeWithException(e)
				}

				override fun onResponse(call: Call, response: Response) {
					val result = runCatching { response.use { HomeRowsHttpResult(it.code, it.body?.string().orEmpty()) } }
					result.fold(continuation::resume, continuation::resumeWithException)
				}
			})
		}
	}
}

class HomeRowsException(val status: Int) : Exception("HomeRows request failed with status $status")

sealed interface HomeRowsSaveResult {
	data object Saved : HomeRowsSaveResult
	data class Invalid(val errors: Map<String, List<String>>) : HomeRowsSaveResult
}

class HomeRowsApi(
	private val transport: HomeRowsTransport,
	private val serverKey: () -> String,
) {
	companion object {
		const val API_VERSION = 1

		private val supportCache = mutableMapOf<String, Boolean>()
		private val supportMutex = Mutex()

		/** Clears the support cache. For tests. */
		fun clearSupportCache() = supportCache.clear()
	}

	// The SDK's Json skips default values; the server reads a missing Enabled as false.
	private val json: Json = Json(ApiSerializer.json) { encodeDefaults = true }
	private val _revision = MutableStateFlow(0)

	/** Incremented after every successful change to the user's layout or the household default. */
	val revision: StateFlow<Int> = _revision.asStateFlow()

	/** Whether this server has the fork's HomeRows API. Only a 404 is remembered; other failures are asked again. */
	suspend fun isSupported(): Boolean = supportMutex.withLock {
		val key = serverKey()
		supportCache[key]?.let { return it }

		val result = runCatching { transport.send("GET", "HomeRows/Configuration") }.getOrNull() ?: return false
		when {
			result.status == 404 -> false.also { supportCache[key] = it }
			result.status !in 200..299 -> false
			else -> {
				val supported = runCatching { decode<HomeRowConfiguration>(result.body).apiVersion == API_VERSION }.getOrDefault(false)
				supported.also { supportCache[key] = it }
			}
		}
	}

	/**
	 * Loads the layout. [expand] asks the server to include derived rows (Home screen only; the editor keeps the plain
	 * layout, since the server rejects saving derived ids). Servers that don't know the parameter ignore it.
	 */
	suspend fun getConfiguration(expand: Boolean = false): HomeRowConfiguration = decode(
		get("HomeRows/Configuration", if (expand) mapOf("expand" to "true") else emptyMap())
	)

	suspend fun getProviders(): List<HomeRowProvider> = decode<HomeRowProviders>(get("HomeRows/Providers")).providers

	suspend fun getItems(rowId: UUID, limit: Int = 20): HomeRowItems = decode(
		get(
			"HomeRows/$rowId/Items",
			mapOf(
				"startIndex" to "0",
				"limit" to limit.toString(),
				"fields" to "PrimaryImageAspectRatio,Overview,PlayAccess",
				"imageTypeLimit" to "1",
				"enableImageTypes" to "Primary,Backdrop,Thumb,Banner",
			),
		)
	)

	/** Saves the user's own layout. */
	suspend fun saveConfiguration(rows: List<HomeRowInstance>) = saveRows("HomeRows/Configuration", rows)

	/** Removes the user's own layout, so they inherit the household default again. */
	suspend fun resetConfiguration() = delete("HomeRows/Configuration")

	/** Saves the household default. Admins only. */
	suspend fun saveDefault(rows: List<HomeRowInstance>) = saveRows("HomeRows/DefaultConfiguration", rows)

	/** Removes the household default. Admins only. */
	suspend fun removeDefault() = delete("HomeRows/DefaultConfiguration")

	private suspend fun get(path: String, query: Map<String, String> = emptyMap()): String {
		val result = transport.send("GET", path, query)
		if (result.status !in 200..299) throw HomeRowsException(result.status)
		return result.body
	}

	private suspend fun delete(path: String) {
		val result = transport.send("DELETE", path)
		if (result.status !in 200..299) throw HomeRowsException(result.status)
		_revision.update { it + 1 }
	}

	private suspend fun saveRows(path: String, rows: List<HomeRowInstance>): HomeRowsSaveResult {
		val body = json.encodeToString(HomeRowsSaveRequest.serializer(), HomeRowsSaveRequest(rows))
		val result = transport.send("PUT", path, body = body)
		return when {
			result.status == 400 -> HomeRowsSaveResult.Invalid(
				runCatching { decode<HomeRowsValidationProblem>(result.body).errors }.getOrDefault(emptyMap())
			)

			result.status !in 200..299 -> throw HomeRowsException(result.status)
			else -> HomeRowsSaveResult.Saved.also { _revision.update { it + 1 } }
		}
	}

	private inline fun <reified T> decode(body: String): T = json.decodeFromString(body)
}
