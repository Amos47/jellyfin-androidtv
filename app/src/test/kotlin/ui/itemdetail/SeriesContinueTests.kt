package org.jellyfin.androidtv.ui.itemdetail

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
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
		selectSeriesContinueEpisode(resume = emptyList(), nextUp = emptyList(), recentlyPlayed = emptyList()) shouldBe null
	}

	test("Partially watched series continues with next up") {
		val next = episode("S01E04")
		val lastWatched = episode("S01E03", lastPlayed = now.minusDays(1), played = true)

		selectSeriesContinueEpisode(listOf(), listOf(next), listOf(lastWatched))?.name shouldBe "S01E04"
	}

	test("In-progress episode is resumed") {
		val inProgress = episode("S01E04", lastPlayed = now.minusHours(1), position = 1000)
		val lastWatched = episode("S01E03", lastPlayed = now.minusDays(1), played = true)

		selectSeriesContinueEpisode(listOf(inProgress), listOf(), listOf(lastWatched))?.name shouldBe "S01E04"
	}

	test("Rewatch in progress resumes the episode instead of the one after it") {
		// During a rewatch the in-progress episode is still marked played, and next up returns the following episode
		val inProgress = episode("S04E08", lastPlayed = now.minusHours(1), played = true, position = 1000)
		val rewatchNext = episode("S04E09")

		selectSeriesContinueEpisode(listOf(inProgress), listOf(rewatchNext), listOf(inProgress))?.name shouldBe "S04E08"
	}

	test("Rewatch between episodes continues with the next episode") {
		val finished = episode("S04E08", lastPlayed = now.minusHours(1), played = true)
		val rewatchNext = episode("S04E09")

		selectSeriesContinueEpisode(listOf(), listOf(rewatchNext), listOf(finished))?.name shouldBe "S04E09"
	}

	test("Finished series without a rewatch has nothing to continue") {
		val finale = episode("S10E18", lastPlayed = now.minusDays(30), played = true)

		selectSeriesContinueEpisode(listOf(), listOf(), listOf(finale)) shouldBe null
	}

	test("Requests are limited to the series") {
		val resume = createSeriesResumeRequest(seriesId)
		resume.parentId shouldBe seriesId
		resume.mediaTypes shouldBe setOf(MediaType.VIDEO)

		createSeriesNextUpRequest(seriesId).seriesId shouldBe seriesId

		val recentlyPlayed = createSeriesRecentlyPlayedRequest(seriesId)
		recentlyPlayed.parentId shouldBe seriesId
		recentlyPlayed.recursive shouldBe true
		recentlyPlayed.isPlayed shouldBe true
		recentlyPlayed.sortBy shouldBe setOf(ItemSortBy.DATE_PLAYED)
		recentlyPlayed.sortOrder shouldBe setOf(SortOrder.DESCENDING)
	}
})
