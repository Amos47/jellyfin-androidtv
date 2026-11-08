package org.jellyfin.androidtv.data.homerows

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.UUID

private const val PROVIDERS = """{"ApiVersion":1,"Providers":[
	{"Id":"genre","Name":"Genre","Description":"Items in a genre","Schema":{"type":"object","properties":{
		"genre":{"type":"string","title":"Genre","enum":["Comedy","Drama"]},
		"limit":{"type":"integer","minimum":1,"maximum":50}},"required":["genre"]},
	 "Defaults":{"limit":20}},
	{"Id":"next-up","Name":"Next Up","Description":"","Schema":{"type":"object","properties":{}},"Defaults":{}}
]}"""

/** A fake server: GET returns [rows], PUT stores them with fresh ids unless [putStatus] says otherwise. */
private class FakeServer(var rows: String) {
	var putStatus = 200
	var putBody = ""
	var lastPut: String? = null

	val transport = FakeHomeRowsTransport { method, path ->
		when {
			path == "HomeRows/Providers" -> HomeRowsHttpResult(200, PROVIDERS)
			method == "GET" && path == "HomeRows/Configuration" ->
				HomeRowsHttpResult(200, """{"ApiVersion":1,"Source":"User","IsCustomized":true,"Rows":$rows}""")

			method == "PUT" -> HomeRowsHttpResult(putStatus, putBody)
			method == "DELETE" -> HomeRowsHttpResult(204, "")
			else -> HomeRowsHttpResult(404, "")
		}
	}

	fun editor() = HomeRowsEditor(HomeRowsApi(object : HomeRowsTransport {
		override suspend fun send(method: String, path: String, query: Map<String, String>, body: String?): HomeRowsHttpResult {
			if (method == "PUT") {
				lastPut = body
				if (putStatus in 200..299 && path == "HomeRows/Configuration") {
					val sent = Json.parseToJsonElement(body!!).jsonObject["Rows"]!!.jsonArray
					rows = sent.joinToString(",", "[", "]") { row ->
						val obj = row.jsonObject
						val id = obj["Id"].toString().trim('"').let { if (it == EMPTY_ROW_ID.toString()) UUID.randomUUID().toString() else it }
						Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(obj + ("Id" to JsonPrimitive(id))))
					}
				}
			}
			return transport.send(method, path, query, body)
		}
	}) { "s" })
}

private fun row(id: UUID = UUID.randomUUID(), provider: String = "genre", config: String = """{"genre":"Comedy"}""", extra: String = "") =
	"""{"Id":"$id","Provider":"$provider","Title":null,"Config":$config,"Enabled":true$extra}"""

class HomeRowsEditorTests : FunSpec({
	test("editor loads the plain layout") {
		val server = FakeServer("[${row()}]")
		val editor = server.editor()

		editor.load()

		val calls = server.transport.calls.filter { it.path == "HomeRows/Configuration" }
		// The editor goes through its own wrapper transport, so check what reached the server fake.
		calls.isNotEmpty() shouldBe true
		calls.all { it.query.isEmpty() } shouldBe true
	}

	test("load has no pending edits") {
		val editor = FakeServer("[${row()},${row(provider = "next-up", config = "{}")}]").editor()

		editor.load()

		editor.state.value.loaded shouldBe true
		editor.state.value.rows.size shouldBe 2
		editor.state.value.providers.size shouldBe 2
		editor.state.value.hasPendingEdits shouldBe false
	}

	test("move, toggle, retitle and setValue make edits pending; moving back clears them") {
		val editor = FakeServer("[${row()},${row(provider = "next-up", config = "{}")}]").editor()
		editor.load()
		val first = editor.state.value.rows[0].key

		editor.moveDown(first)
		editor.state.value.rows[1].key shouldBe first
		editor.state.value.hasPendingEdits shouldBe true
		editor.moveUp(first)
		editor.state.value.hasPendingEdits shouldBe false

		editor.setEnabled(first, false)
		editor.state.value.hasPendingEdits shouldBe true
		editor.setEnabled(first, true)

		editor.setTitle(first, "Comfort")
		editor.state.value.rows[0].row.title shouldBe "Comfort"
		editor.setTitle(first, "  ")
		editor.state.value.rows[0].row.title shouldBe null
		editor.state.value.hasPendingEdits shouldBe false

		editor.setValue(first, "genre", JsonPrimitive("Drama"))
		editor.state.value.rows[0].row.config["genre"] shouldBe JsonPrimitive("Drama")
		editor.state.value.hasPendingEdits shouldBe true
	}

	test("titles are cut to 100 characters") {
		val editor = FakeServer("[${row()}]").editor()
		editor.load()
		val key = editor.state.value.rows[0].key

		editor.setTitle(key, "a".repeat(150))

		editor.state.value.rows[0].row.title shouldBe "a".repeat(100)
	}

	test("two added rows get distinct keys") {
		val editor = FakeServer("[]").editor()
		editor.load()

		val a = editor.add("genre")
		val b = editor.add("genre")

		a shouldNotBe null
		a shouldNotBe b
		editor.state.value.rows.map { it.row.id } shouldBe listOf(EMPTY_ROW_ID, EMPTY_ROW_ID)
		editor.state.value.rows[0].row.config["limit"] shouldBe JsonPrimitive(20)
	}

	test("add returns null at 20 rows") {
		val editor = FakeServer("[" + List(20) { row() }.joinToString(",") + "]").editor()
		editor.load()

		editor.add("genre") shouldBe null
		editor.state.value.rows.size shouldBe 20
	}

	test("add copies defaults deeply") {
		val editor = FakeServer("[]").editor()
		editor.load()
		val key = editor.add("genre")!!

		editor.setValue(key, "limit", JsonPrimitive(5))

		editor.state.value.providers.first { it.id == "genre" }.defaults["limit"] shouldBe JsonPrimitive(20)
	}

	test("save maps rows[3].Limit to the fourth row's key as \"limit\"") {
		val server = FakeServer("[${row()},${row()}]")
		val editor = server.editor()
		editor.load()
		editor.add("genre")
		val fourth = editor.add("genre")!!
		server.putStatus = 400
		server.putBody = """{"errors":{"rows[3].Limit":["Too big"],"Rows":["Too many"]}}"""

		editor.save() shouldBe false

		editor.state.value.errors[fourth] shouldBe mapOf("limit" to "Too big")
		editor.state.value.errors["*"] shouldBe mapOf("Rows" to "Too many")
	}

	test("save keeps edits on 400") {
		val server = FakeServer("[${row()}]")
		val editor = server.editor()
		editor.load()
		editor.add("next-up")
		server.putStatus = 400
		server.putBody = """{"errors":{}}"""

		editor.save() shouldBe false

		editor.state.value.rows.size shouldBe 2
		editor.state.value.hasPendingEdits shouldBe true
	}

	test("save on 500 sets generalError and keeps edits") {
		val server = FakeServer("[${row()}]")
		val editor = server.editor()
		editor.load()
		editor.add("next-up")
		server.putStatus = 500

		editor.save() shouldBe false

		editor.state.value.generalError shouldNotBe null
		editor.state.value.rows.size shouldBe 2
	}

	test("save success reloads and clears pending") {
		val editor = FakeServer("[${row()}]").editor()
		editor.load()
		editor.add("next-up")

		editor.save() shouldBe true

		editor.state.value.hasPendingEdits shouldBe false
		editor.state.value.rows.size shouldBe 2
		editor.state.value.rows[1].row.id shouldNotBe EMPTY_ROW_ID
	}

	test("discard restores the saved rows") {
		val editor = FakeServer("[${row()}]").editor()
		editor.load()
		editor.add("next-up")
		editor.remove(editor.state.value.rows[0].key)

		editor.discard()

		editor.state.value.rows.single().row.provider shouldBe "genre"
		editor.state.value.hasPendingEdits shouldBe false
	}

	test("unavailable row round-trips through save unchanged") {
		val gone = UUID.randomUUID()
		val server = FakeServer("[${row(id = gone, provider = "removed", config = """{"x":1}""", extra = ""","IsAvailable":false""")}]")
		val editor = server.editor()
		editor.load()
		editor.add("next-up")

		editor.save() shouldBe true

		val sent = Json.parseToJsonElement(server.lastPut!!).jsonObject["Rows"]!!.jsonArray[0].jsonObject
		sent["Id"] shouldBe JsonPrimitive(gone.toString())
		sent["Provider"] shouldBe JsonPrimitive("removed")
		sent["Config"] shouldBe Json.parseToJsonElement("""{"x":1}""")
	}

	test("saveAsDefault leaves pending state as it was") {
		val editor = FakeServer("[${row()}]").editor()
		editor.load()
		editor.add("next-up")

		editor.saveAsDefault() shouldBe true

		editor.state.value.hasPendingEdits shouldBe true
	}

	test("reset reloads the inherited layout and drops edits") {
		val server = FakeServer("[${row()}]")
		val editor = server.editor()
		editor.load()
		editor.add("next-up")
		server.rows = "[]"

		editor.reset() shouldBe true

		editor.state.value.rows.size shouldBe 0
		editor.state.value.hasPendingEdits shouldBe false
	}
})

class HomeRowsLeaveTests : FunSpec({
	test("Save leaves only when the save succeeds") {
		val server = FakeServer("[${row()}]")
		val editor = server.editor()
		editor.load()
		editor.add("next-up")

		server.putStatus = 400
		server.putBody = """{"errors":{}}"""
		editor.resolveLeave(LeaveChoice.SAVE) shouldBe false
		editor.state.value.hasPendingEdits shouldBe true

		server.putStatus = 200
		editor.resolveLeave(LeaveChoice.SAVE) shouldBe true
		editor.state.value.hasPendingEdits shouldBe false
	}

	test("Discard leaves and drops the edits; Keep editing stays") {
		val editor = FakeServer("[${row()}]").editor()
		editor.load()
		editor.add("next-up")

		editor.resolveLeave(LeaveChoice.KEEP_EDITING) shouldBe false
		editor.state.value.hasPendingEdits shouldBe true

		editor.resolveLeave(LeaveChoice.DISCARD) shouldBe true
		editor.state.value.hasPendingEdits shouldBe false
	}
})
