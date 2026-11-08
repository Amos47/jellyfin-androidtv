package org.jellyfin.androidtv.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.StillWatchingBehavior
import org.jellyfin.androidtv.ui.playback.PlaybackControllerContainer
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ExtraType
import java.util.UUID

private fun item(type: BaseItemKind, extraType: ExtraType? = null) = BaseItemDto(
	id = UUID.randomUUID(),
	type = type,
	extraType = extraType,
)

private val episode get() = item(BaseItemKind.EPISODE)
private val queuedTrailer get() = item(BaseItemKind.VIDEO, ExtraType.TRAILER)
private val trailerKind get() = item(BaseItemKind.TRAILER)

private fun tracker(): InteractionTrackerViewModel {
	val prefs = mockk<UserPreferences>(relaxed = true)
	every { prefs[UserPreferences.stillWatchingBehavior] } returns StillWatchingBehavior.SHORT
	every { prefs[UserPreferences.screensaverInAppEnabled] } returns false
	return InteractionTrackerViewModel(prefs, mockk<PlaybackControllerContainer>(relaxed = true))
}

class InteractionTrackerTests : FunSpec({
	test("predicate counts episodes, trailer kind and queued trailers only") {
		countsForStillWatching(episode) shouldBe true
		countsForStillWatching(trailerKind) shouldBe true
		countsForStillWatching(queuedTrailer) shouldBe true
		countsForStillWatching(item(BaseItemKind.MOVIE)) shouldBe false
		countsForStillWatching(item(BaseItemKind.VIDEO, ExtraType.CLIP)) shouldBe false
		countsForStillWatching(item(BaseItemKind.VIDEO)) shouldBe false
	}

	test("trailer queue starts a session") {
		val t = tracker()
		t.notifyStartSession(episode, listOf(episode)) // nothing
		t.onEpisodeWatched()
		t.onEpisodeWatched() // stale count of 2 would show the prompt
		t.getShowStillWatching() shouldBe true
		val trailers = listOf(queuedTrailer, queuedTrailer, queuedTrailer)
		t.notifyStartSession(trailers.first(), trailers)
		t.getShowStillWatching() shouldBe false // session reset
	}

	test("single trailer does not start a session") {
		val t = tracker()
		t.onEpisodeWatched()
		t.onEpisodeWatched()
		t.notifyStartSession(queuedTrailer, listOf(queuedTrailer))
		t.getShowStillWatching() shouldBe true // untouched
	}

	test("trailers count toward the episode limit") {
		val t = tracker()
		val trailers = listOf(queuedTrailer, queuedTrailer, queuedTrailer)
		t.notifyStartSession(trailers.first(), trailers)
		t.notifyStart(trailers[0])
		t.onEpisodeWatched()
		t.getShowStillWatching() shouldBe false
		t.notifyStart(trailers[1])
		t.onEpisodeWatched()
		t.getShowStillWatching() shouldBe true
	}

	test("click resets") {
		val t = tracker()
		val trailers = listOf(queuedTrailer, queuedTrailer, queuedTrailer)
		t.notifyStartSession(trailers.first(), trailers)
		t.notifyStart(trailers[0])
		t.onEpisodeWatched()
		t.notifyInteraction(canCancel = false, userInitiated = true)
		t.onEpisodeWatched() // interrupted episode does not count
		t.getShowStillWatching() shouldBe false
	}

	test("episodes unchanged") {
		val t = tracker()
		val eps = listOf(episode, episode, episode)
		t.notifyStartSession(eps.first(), eps)
		t.notifyStart(eps[0])
		t.onEpisodeWatched()
		t.getShowStillWatching() shouldBe false
		t.onEpisodeWatched()
		t.getShowStillWatching() shouldBe true
		t.notifyStartSession(episode, listOf(episode))
		t.getShowStillWatching() shouldBe true // single episode: no reset
	}

	test("after item: trailers skip Next Up but not Still watching") {
		for (trailer in listOf(queuedTrailer, trailerKind)) {
			afterItemComplete(trailer, true, true, false) shouldBe AfterItem.PLAY_NEXT
			afterItemComplete(trailer, true, true, true) shouldBe AfterItem.STILL_WATCHING
			afterItemComplete(trailer, false, true, false) shouldBe AfterItem.PLAY_NEXT
		}
	}

	test("after item: episodes unchanged") {
		afterItemComplete(episode, true, true, false) shouldBe AfterItem.NEXT_UP
		afterItemComplete(episode, true, true, true) shouldBe AfterItem.STILL_WATCHING
		afterItemComplete(episode, false, true, true) shouldBe AfterItem.NEXT_UP
		afterItemComplete(episode, true, false, false) shouldBe AfterItem.END
		afterItemComplete(episode, false, false, false) shouldBe AfterItem.PLAY_NEXT
	}
})
