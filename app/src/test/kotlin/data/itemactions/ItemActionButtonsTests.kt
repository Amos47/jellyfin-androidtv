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

class ActionPressGateTests : FunSpec({
	test("a second press while one is in flight is ignored") {
		val gate = ActionPressGate()

		gate.tryStart() shouldBe true
		gate.tryStart() shouldBe false
	}

	test("a finished press lets the next one start") {
		val gate = ActionPressGate()

		gate.tryStart()
		gate.finish()

		gate.tryStart() shouldBe true
	}

})

class FocusTargetTests : FunSpec({
	val shown = setOf("play", "watch-trailer", "favorite", "more")
	val canFocus = { button: String -> button in shown }

	test("a virtual film focuses Watch trailer, the first button that can take focus") {
		focusTargetFor(listOf("watch-trailer", "favorite", "more"), focused = null, canFocus) shouldBe "watch-trailer"
	}

	test("an owned film focuses Play when Resume is hidden") {
		focusTargetFor(listOf("resume", "play", "watch-trailer", "favorite"), focused = null, canFocus) shouldBe "play"
	}

	test("focus on a button that stays is kept") {
		focusTargetFor(listOf("play", "watch-trailer", "favorite"), focused = "favorite", canFocus) shouldBe "favorite"
	}

	test("focus on a removed button moves to the default") {
		focusTargetFor(listOf("watch-trailer", "favorite"), focused = "stock-trailer", canFocus) shouldBe "watch-trailer"
	}

	test("focus on a hidden button moves to the default") {
		focusTargetFor(listOf("watch-trailer", "resume"), focused = "resume", canFocus) shouldBe "watch-trailer"
	}

	test("no button can take focus") {
		focusTargetFor(listOf("resume"), focused = null, canFocus) shouldBe null
		focusTargetFor(emptyList(), focused = null, canFocus) shouldBe null
	}
})

class FocusRowsBelowButtonsTests : FunSpec({
	val canFocus = { button: String -> button != "more" }

	test("a button row with a focusable button keeps focus") {
		focusRowsBelowButtons(listOf("watch-trailer", "more"), canFocus) shouldBe false
	}

	test("a button row with nothing focusable sends focus to the rows below") {
		focusRowsBelowButtons(listOf("more"), canFocus) shouldBe true
		focusRowsBelowButtons(emptyList(), canFocus) shouldBe true
	}
})

class ShouldTakeFocusTests : FunSpec({
	test("nothing focused: take focus") {
		shouldTakeFocus(hasFocus = false, focusAttached = false, focusInRow = false, rowRebuilt = false) shouldBe true
	}

	test("focus on a view that left the window: take focus") {
		shouldTakeFocus(hasFocus = true, focusAttached = false, focusInRow = false, rowRebuilt = false) shouldBe true
	}

	test("focus on a button row button that is going away: take focus") {
		shouldTakeFocus(hasFocus = true, focusAttached = true, focusInRow = true, rowRebuilt = false) shouldBe true
	}

	test("focus the user moved outside the button row (toolbar, rows below) is left alone") {
		shouldTakeFocus(hasFocus = true, focusAttached = true, focusInRow = false, rowRebuilt = false) shouldBe false
	}

	test("a button row just attached or re-added takes focus") {
		shouldTakeFocus(hasFocus = true, focusAttached = true, focusInRow = false, rowRebuilt = true) shouldBe true
	}
})
