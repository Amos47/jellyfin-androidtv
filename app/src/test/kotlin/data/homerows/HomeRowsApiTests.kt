package org.jellyfin.androidtv.data.homerows

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class FakeHomeRowsTransport(
	private val respond: (method: String, path: String) -> HomeRowsHttpResult,
) : HomeRowsTransport {
	data class Call(val method: String, val path: String, val query: Map<String, String>, val body: String?)

	val calls = mutableListOf<Call>()

	override suspend fun send(method: String, path: String, query: Map<String, String>, body: String?): HomeRowsHttpResult {
		calls += Call(method, path, query, body)
		return respond(method, path)
	}
}

const val CONFIGURATION_V1 = """{"ApiVersion":1,"Source":"User","IsCustomized":true,"Rows":[]}"""

class HomeRowsApiTests : FunSpec({
	beforeTest { HomeRowsApi.clearSupportCache() }

	fun api(transport: HomeRowsTransport, server: String = "http://a") = HomeRowsApi(transport) { server }

	test("isSupported is true for ApiVersion 1") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(200, CONFIGURATION_V1) }

		api(transport).isSupported() shouldBe true
	}

	test("isSupported is false for ApiVersion 2") {
		val transport = FakeHomeRowsTransport { _, _ ->
			HomeRowsHttpResult(200, """{"ApiVersion":2,"Source":"User","IsCustomized":true,"Rows":[]}""")
		}

		api(transport).isSupported() shouldBe false
	}

	test("isSupported caches a 404 per server") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(404, "") }

		api(transport).isSupported() shouldBe false
		api(transport).isSupported() shouldBe false
		transport.calls.size shouldBe 1

		api(transport, server = "http://b").isSupported() shouldBe false
		transport.calls.size shouldBe 2
	}

	test("isSupported does not cache a 500") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(500, "") }

		api(transport).isSupported() shouldBe false
		api(transport).isSupported() shouldBe false
		transport.calls.size shouldBe 2
	}

	test("getItems decodes BaseItemDto and presentation") {
		val rowId = UUID.randomUUID()
		val itemId = UUID.randomUUID()
		val transport = FakeHomeRowsTransport { _, _ ->
			HomeRowsHttpResult(
				200,
				"""{"RowId":"$rowId","Title":"Next Up","Items":[{"Id":"$itemId","Name":"Pilot","Type":"Episode"}],
				"TotalRecordCount":1,"StartIndex":0,"Presentation":{"CardShape":"Thumb","PreferredImageType":"Thumb"},
				"Error":null}"""
			)
		}

		val items = api(transport).getItems(rowId)

		items.items[0].name shouldBe "Pilot"
		items.items[0].id shouldBe itemId
		items.presentation.cardShape shouldBe "Thumb"
		transport.calls.single().path shouldBe "HomeRows/$rowId/Items"
		transport.calls.single().query["limit"] shouldBe "20"
	}

	test("saveConfiguration sends PUT with Rows and keeps unknown config keys") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(200, CONFIGURATION_V1) }
		val config = buildJsonObject {
			put("genre", "Comedy")
			put("somethingNew", buildJsonObject { put("nested", 3) })
		}
		val row = HomeRowInstance(EMPTY_ROW_ID, "genre", null, config, enabled = true)

		api(transport).saveConfiguration(listOf(row)) shouldBe HomeRowsSaveResult.Saved

		val call = transport.calls.single()
		call.method shouldBe "PUT"
		call.path shouldBe "HomeRows/Configuration"
		val sent = Json.parseToJsonElement(call.body!!).jsonObject["Rows"]!!.jsonArray.single().jsonObject
		sent["Config"] shouldBe config
		sent["Provider"] shouldBe JsonPrimitive("genre")
	}

	test("saveConfiguration sends Enabled and Title even when they are defaults") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(200, CONFIGURATION_V1) }
		val row = HomeRowInstance(EMPTY_ROW_ID, "genre", null, emptyConfig(), enabled = true)

		api(transport).saveConfiguration(listOf(row))

		val sent = Json.parseToJsonElement(transport.calls.single().body!!).jsonObject["Rows"]!!.jsonArray.single().jsonObject
		sent["Enabled"] shouldBe JsonPrimitive(true)
		sent["Config"] shouldBe emptyConfig()
	}

	test("saveConfiguration maps 400 errors") {
		val transport = FakeHomeRowsTransport { _, _ ->
			HomeRowsHttpResult(400, """{"title":"One or more validation errors occurred.","errors":{"rows[2].limit":["Too big"]}}""")
		}

		api(transport).saveConfiguration(emptyList()) shouldBe
			HomeRowsSaveResult.Invalid(mapOf("rows[2].limit" to listOf("Too big")))
	}

	test("revision increments only on success") {
		var status = 400
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(status, """{"errors":{}}""") }
		val api = api(transport)

		api.saveConfiguration(emptyList())
		api.revision.value shouldBe 0

		status = 204
		api.resetConfiguration()
		api.revision.value shouldBe 1
	}

	test("getConfiguration throws HomeRowsException with the status on 500") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(500, "") }

		shouldThrow<HomeRowsException> { api(transport).getConfiguration() }.status shouldBe 500
	}
})

fun emptyConfig() = JsonObject(emptyMap())
