package org.jellyfin.androidtv.data.itemactions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.data.homerows.FakeHomeRowsTransport
import org.jellyfin.androidtv.data.homerows.HomeRowsHttpResult
import java.io.IOException
import java.util.UUID

class ItemActionsApiTests : FunSpec({
	val itemId = UUID.fromString("11111111-2222-3333-4444-555555555555")

	test("actions 404 gives empty") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(404, "") }

		ItemActionsApi(transport).getActions(itemId).shouldBeEmpty()
		transport.calls.single().method shouldBe "GET"
		transport.calls.single().path shouldBe "Items/$itemId/Actions"
	}

	test("actions failures give empty") {
		ItemActionsApi(FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(500, "") }).getActions(itemId).shouldBeEmpty()
		ItemActionsApi(FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(200, "not json") }).getActions(itemId).shouldBeEmpty()
		ItemActionsApi(FakeHomeRowsTransport { _, _ -> throw IOException("offline") }).getActions(itemId).shouldBeEmpty()
	}

	test("actions decode in server order") {
		val transport = FakeHomeRowsTransport { _, _ ->
			HomeRowsHttpResult(
				200,
				"""[{"Id":"watch-trailer","Label":"Watch trailer","Icon":"trailer","Kind":"play-queue"},
				{"Id":"other","Label":"Other","Icon":null,"Kind":"something-new","Extra":1}]"""
			)
		}

		ItemActionsApi(transport).getActions(itemId) shouldBe listOf(
			ItemAction("watch-trailer", "Watch trailer", "trailer", "play-queue"),
			ItemAction("other", "Other", null, "something-new"),
		)
	}

	test("queue keeps server order") {
		val a = UUID.randomUUID()
		val b = UUID.randomUUID()
		val c = UUID.randomUUID()
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(200, """{"ItemIds":["$c","$a","$b"]}""") }

		ItemActionsApi(transport).getQueue(itemId, "watch-trailer") shouldBe listOf(c, a, b)
		transport.calls.single().path shouldBe "Items/$itemId/Actions/watch-trailer/Queue"
	}

	test("queue failure throws") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(404, "") }

		shouldThrow<ItemActionsException> { ItemActionsApi(transport).getQueue(itemId, "watch-trailer") }.status shouldBe 404
	}

	test("actions decode state, labels and enabled") {
		val transport = FakeHomeRowsTransport { _, _ ->
			HomeRowsHttpResult(
				200,
				"""[{"Id":"save","Label":"Save","Icon":"bookmark","Kind":"toggle","State":"off","Labels":{"on":"Saved","off":"Save"},"Enabled":true},
				{"Id":"download","Label":"Requested","Icon":"download","Kind":"request","State":"requested","Enabled":false}]"""
			)
		}

		ItemActionsApi(transport).getActions(itemId) shouldBe listOf(
			ItemAction("save", "Save", "bookmark", "toggle", state = "off", labels = mapOf("on" to "Saved", "off" to "Save"), enabled = true),
			ItemAction("download", "Requested", "download", "request", state = "requested", enabled = false),
		)
	}

	test("post sends the action and answers the updated one") {
		val transport = FakeHomeRowsTransport { _, _ ->
			HomeRowsHttpResult(200, """{"Id":"save","Label":"Saved","Icon":"bookmark","Kind":"toggle","State":"on","Enabled":true}""")
		}

		ItemActionsApi(transport).post(itemId, "save") shouldBe
			ItemAction("save", "Saved", "bookmark", "toggle", state = "on", enabled = true)
		transport.calls.single().method shouldBe "POST"
		transport.calls.single().path shouldBe "Items/$itemId/Actions/save"
	}

	test("post failures give null") {
		ItemActionsApi(FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(400, "") }).post(itemId, "download") shouldBe null
		ItemActionsApi(FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(200, "not json") }).post(itemId, "save") shouldBe null
		ItemActionsApi(FakeHomeRowsTransport { _, _ -> throw IOException("offline") }).post(itemId, "save") shouldBe null
	}
})
