package org.jellyfin.androidtv.ui.playback.segment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.MediaSegmentType

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
