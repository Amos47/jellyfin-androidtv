package org.jellyfin.androidtv.ui.playback.segment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SkipPressTests : FunSpec({
	test("Credits with a next episode go to the next episode") {
		skipPress(MediaSegmentType.OUTRO, hasNextItem = true) shouldBe SkipPress.NEXT_EPISODE
	}

	test("Credits without a next episode skip to the end of the credits") {
		skipPress(MediaSegmentType.OUTRO, hasNextItem = false) shouldBe SkipPress.SEEK
	}

	test("Intros always skip to the end of the intro") {
		skipPress(MediaSegmentType.INTRO, hasNextItem = true) shouldBe SkipPress.SEEK
	}

	test("Recaps and previews skip to their end") {
		skipPress(MediaSegmentType.RECAP, hasNextItem = true) shouldBe SkipPress.SEEK
		skipPress(MediaSegmentType.PREVIEW, hasNextItem = true) shouldBe SkipPress.SEEK
	}
})

class SkipPromptTests : FunSpec({
	val intro = SkipSegment(MediaSegmentType.INTRO, start = 10.seconds, end = 55.seconds)
	val credits = SkipSegment(MediaSegmentType.OUTRO, start = 20.minutes, end = 21.minutes + 30.seconds)
	val segments = listOf(intro, credits)

	fun prompt(position: Duration, hasNextItem: Boolean = true, dismissed: Duration? = null) =
		skipPrompt(position, segments, hasNextItem, dismissed)

	test("Nothing outside the segments") {
		prompt(5.seconds) shouldBe null
		prompt(10.minutes) shouldBe null
	}

	test("Shown from the start of the intro") {
		prompt(10.seconds) shouldBe SkipPrompt(target = 55.seconds, nextEpisode = false, segmentStart = 10.seconds)
	}

	test("Shown after resuming in the middle of the intro") {
		prompt(30.seconds)?.target shouldBe 55.seconds
	}

	test("Shown until the last second of the intro") {
		prompt(53.seconds)?.target shouldBe 55.seconds
		prompt(54.5.seconds) shouldBe null
	}

	test("Next episode through the whole credits") {
		prompt(20.minutes)?.nextEpisode shouldBe true
		prompt(21.minutes)?.nextEpisode shouldBe true
		prompt(21.minutes + 28.seconds)?.nextEpisode shouldBe true
	}

	test("Credits without a next episode skip to their end") {
		prompt(21.minutes, hasNextItem = false) shouldBe SkipPrompt(target = 21.minutes + 30.seconds, nextEpisode = false, segmentStart = 20.minutes)
	}

	test("A dismissed segment stays hidden, the next one shows") {
		prompt(30.seconds, dismissed = 10.seconds) shouldBe null
		prompt(21.minutes, dismissed = 10.seconds)?.nextEpisode shouldBe true
	}

	test("Overlapping segments prefer the one that started last") {
		val recap = SkipSegment(MediaSegmentType.RECAP, start = 0.seconds, end = 40.seconds)
		skipPrompt(20.seconds, listOf(recap, intro), true, null)?.target shouldBe 55.seconds
	}
})

class NextEpisodeFallbackTests : FunSpec({
	val intro = SkipSegment(MediaSegmentType.INTRO, start = 10.seconds, end = 55.seconds)
	val runtime = 22.minutes

	fun prompt(position: Duration, hasNextItem: Boolean = true, hasCreditsMarker: Boolean = false, dismissed: Duration? = null) =
		skipPrompt(position, listOf(intro), hasNextItem, dismissed, runtime, hasCreditsMarker)

	test("Next episode in the last 30 seconds without a credits marker") {
		prompt(runtime - 30.seconds) shouldBe SkipPrompt(target = runtime, nextEpisode = true, segmentStart = runtime - 30.seconds)
		prompt(runtime - 2.seconds)?.nextEpisode shouldBe true
	}

	test("Nothing before the last 30 seconds") {
		prompt(runtime - 31.seconds) shouldBe null
	}

	test("Nothing without a next episode") {
		prompt(runtime - 10.seconds, hasNextItem = false) shouldBe null
	}

	test("Nothing when the credits have a marker, even one the user doesn't get asked about") {
		prompt(runtime - 10.seconds, hasCreditsMarker = true) shouldBe null
	}

	test("Dismissing it keeps it hidden") {
		prompt(runtime - 10.seconds, dismissed = runtime - 30.seconds) shouldBe null
	}

	test("Nothing without a known runtime") {
		skipPrompt(21.minutes, listOf(intro), true, null, runtime = null, hasCreditsMarker = false) shouldBe null
	}
})

class SkipLabelTests : FunSpec({
	test("a skip is a skip") {
		skipLabel(nextEpisode = false, nextItemKind = BaseItemKind.EPISODE) shouldBe SkipLabel.SKIP
	}

	test("the next item being an episode says next episode") {
		skipLabel(nextEpisode = true, nextItemKind = BaseItemKind.EPISODE) shouldBe SkipLabel.NEXT_EPISODE
	}

	test("any other next item says next") {
		skipLabel(nextEpisode = true, nextItemKind = BaseItemKind.MOVIE) shouldBe SkipLabel.NEXT
		skipLabel(nextEpisode = true, nextItemKind = BaseItemKind.TRAILER) shouldBe SkipLabel.NEXT
		skipLabel(nextEpisode = true, nextItemKind = null) shouldBe SkipLabel.NEXT
	}
})
