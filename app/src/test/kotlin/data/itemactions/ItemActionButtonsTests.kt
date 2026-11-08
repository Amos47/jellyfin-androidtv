package org.jellyfin.androidtv.data.itemactions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.R
import java.util.UUID

class ItemActionButtonsTests : FunSpec({
	val watchTrailer = ItemAction("watch-trailer", "Watch trailer", "trailer", "play-queue")

	test("unknown kinds ignored") {
		val other = ItemAction("other", "Other", "trailer", "open-url")
		val extras = ItemAction("extras", "Extras", "extras", "play-queue")

		val plan = actionButtonsFor(listOf(other, extras, watchTrailer), hasLocalTrailers = false)

		plan.buttons.map { it.action } shouldBe listOf(extras, watchTrailer)
	}

	test("watch-trailer hides stock trailer button") {
		actionButtonsFor(listOf(watchTrailer), hasLocalTrailers = true).showStockTrailer shouldBe false
	}

	test("stock trailer button stays without a watch-trailer action") {
		actionButtonsFor(emptyList(), hasLocalTrailers = true).showStockTrailer shouldBe true
		actionButtonsFor(emptyList(), hasLocalTrailers = false).showStockTrailer shouldBe false
		val unknownTrailer = watchTrailer.copy(kind = "something-new")
		actionButtonsFor(listOf(unknownTrailer), hasLocalTrailers = true).showStockTrailer shouldBe true
	}

	test("icon mapping") {
		val plan = actionButtonsFor(
			listOf(watchTrailer, watchTrailer.copy(id = "a", icon = "extras"), watchTrailer.copy(id = "b", icon = null)),
			hasLocalTrailers = false,
		)

		plan.buttons.map { it.icon } shouldBe listOf(R.drawable.ic_trailer, R.drawable.ic_play, R.drawable.ic_play)
	}

	test("items are put in queue order") {
		val a = UUID.randomUUID()
		val b = UUID.randomUUID()
		val c = UUID.randomUUID()
		val missing = UUID.randomUUID()

		inQueueOrder(listOf(c, missing, a, b), listOf(a, b, c)) { it } shouldBe listOf(c, a, b)
	}
})
