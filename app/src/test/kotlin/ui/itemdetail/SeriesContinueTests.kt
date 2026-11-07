package org.jellyfin.androidtv.ui.itemdetail

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.time.LocalDateTime
import java.util.UUID

private val now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 20, 0)
private val seriesId: UUID = UUID.randomUUID()

private fun episode(name: String, lastPlayed: LocalDateTime? = null, played: Boolean = false, position: Long = 0) = BaseItemDto(
	id = UUID.randomUUID(),
	name = name,
	type = BaseItemKind.EPISODE,
	seriesId = seriesId,
	userData = UserItemDataDto(
		playbackPositionTicks = position,
		playCount = if (played) 1 else 0,
		isFavorite = false,
		played = played,
		key = "",
		itemId = UUID.randomUUID(),
		lastPlayedDate = lastPlayed,
	),
)

class SeriesContinueTests : FunSpec({
	test("Never watched series has nothing to continue") {
		selectSeriesContinueEpisode(nextUp = emptyList(), resume = emptyList()) shouldBe null
	}

	test("The server's next up wins over a half-watched rewatch") {
		// The server picks one episode per series: a new episode always beats a rewatch
		val newEpisode = episode("S27E08")
		val rewatch = episode("S03E05", lastPlayed = now.minusHours(1), played = true, position = 1000)

		selectSeriesContinueEpisode(nextUp = listOf(newEpisode), resume = listOf(rewatch))?.name shouldBe "S27E08"
	}

	test("A half-watched episode from next up is played as is") {
		val halfWatched = episode("S03E05", lastPlayed = now.minusHours(1), played = true, position = 1000)

		selectSeriesContinueEpisode(nextUp = listOf(halfWatched), resume = listOf(halfWatched))?.name shouldBe "S03E05"
	}

	test("Without next up the most recent in-progress episode is resumed") {
		val inProgress = episode("S01E04", lastPlayed = now.minusHours(1), position = 1000)

		selectSeriesContinueEpisode(nextUp = emptyList(), resume = listOf(inProgress))?.name shouldBe "S01E04"
	}

	test("Requests are limited to the series") {
		val resume = createSeriesResumeRequest(seriesId)
		resume.parentId shouldBe seriesId
		resume.mediaTypes shouldBe setOf(MediaType.VIDEO)

		val nextUp = createSeriesNextUpRequest(seriesId)
		nextUp.seriesId shouldBe seriesId
		// Half-watched episodes stay in so the server's pick is used as is
		nextUp.enableResumable shouldNotBe false
	}
})
