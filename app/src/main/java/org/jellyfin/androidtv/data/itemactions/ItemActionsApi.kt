package org.jellyfin.androidtv.data.itemactions

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.data.homerows.HomeRowsTransport
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.util.UUID

/*
 * Client for the personal fork server's item actions API: the buttons the server offers on an item page. See
 * docs/discovery-api.md in the server repository. Stock servers answer 404, which means no extra buttons. Calls share
 * the HomeRows transport (OkHttp with the SDK's authorization header).
 */

/** One action the server offers on an item page (server `ItemActionDto`). */
@Serializable
data class ItemAction(
	@SerialName("Id") val id: String,
	@SerialName("Label") val label: String,
	@SerialName("Icon") val icon: String? = null,
	@SerialName("Kind") val kind: String,
	@SerialName("State") val state: String? = null,
	@SerialName("Labels") val labels: Map<String, String>? = null,
	@SerialName("Enabled") val enabled: Boolean? = null,
)

@Serializable
data class ItemActionQueue(
	@SerialName("ItemIds") val itemIds: List<@Serializable(with = UUIDSerializer::class) UUID> = emptyList(),
)

class ItemActionsException(val status: Int) : Exception("Item actions request failed with status $status")

class ItemActionsApi(private val transport: HomeRowsTransport) {
	private val json: Json = ApiSerializer.json

	/** The actions to offer for [itemId], in server order. Empty on a 404 (stock server, missing item) or any failure. */
	suspend fun getActions(itemId: UUID): List<ItemAction> = try {
		val result = transport.send("GET", "Items/$itemId/Actions")
		if (result.status in 200..299) json.decodeFromString<List<ItemAction>>(result.body)
		else emptyList()
	} catch (error: CancellationException) {
		throw error
	} catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
		Timber.d(error, "Failed to get item actions for $itemId")
		emptyList()
	}

	/** Runs [actionId] (a `toggle` or `request` action) on [itemId] and answers the updated action; null on any failure. */
	suspend fun post(itemId: UUID, actionId: String): ItemAction? = try {
		val result = transport.send("POST", "Items/$itemId/Actions/$actionId")
		if (result.status in 200..299) json.decodeFromString<ItemAction>(result.body)
		else null
	} catch (error: CancellationException) {
		throw error
	} catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
		Timber.w(error, "Failed to run item action $actionId for $itemId")
		null
	}

	/** The item ids [actionId] plays for [itemId], in play order. Throws [ItemActionsException] on a non-2xx answer. */
	suspend fun getQueue(itemId: UUID, actionId: String): List<UUID> {
		val result = transport.send("GET", "Items/$itemId/Actions/$actionId/Queue")
		if (result.status !in 200..299) throw ItemActionsException(result.status)
		return json.decodeFromString<ItemActionQueue>(result.body).itemIds
	}
}
