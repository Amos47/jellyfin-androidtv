package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.time.LocalDateTime
import java.util.UUID

private val now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 20, 0)

private fun userData(lastPlayed: LocalDateTime?, played: Boolean = false, position: Long = 0) = UserItemDataDto(
	playbackPositionTicks = position,
	playCount = 0,
	isFavorite = false,
	played = played,
	key = "",
	itemId = UUID.randomUUID(),
	lastPlayedDate = lastPlayed,
)

private fun episode(
	name: String,
	series: UUID,
	season: Int = 1,
	index: Int = 1,
	lastPlayed: LocalDateTime? = null,
	premiere: LocalDateTime? = null,
) = BaseItemDto(
	id = UUID.randomUUID(),
	name = name,
	type = BaseItemKind.EPISODE,
	seriesId = series,
	parentIndexNumber = season,
	indexNumber = index,
	premiereDate = premiere,
	userData = userData(lastPlayed),
)

private fun movie(name: String, lastPlayed: LocalDateTime? = null, premiere: LocalDateTime? = null) = BaseItemDto(
	id = UUID.randomUUID(),
	name = name,
	type = BaseItemKind.MOVIE,
	premiereDate = premiere,
	userData = userData(lastPlayed),
)

private fun List<BaseItemDto>.names() = map { it.name }

class CustomHomeRowsTests : FunSpec({
	val svu = UUID.randomUUID()
	val office = UUID.randomUUID()
	val bigBang = UUID.randomUUID()

	context("combineContinueWatching") {
		test("In-progress episode replaces the next up episode of the same series") {
			val inProgress = episode("SVU S04E08", svu, 4, 8, lastPlayed = now.minusHours(1))
			val next = episode("SVU S04E09", svu, 4, 9)

			combineContinueWatching(
				resume = listOf(inProgress),
				nextUp = listOf(next),
				recentlyPlayed = listOf(inProgress),
			).names() shouldBe listOf("SVU S04E08")
		}

		test("Series without an in-progress episode use next up") {
			val next = episode("Office S02E03", office, 2, 3)
			val lastWatched = episode("Office S02E02", office, 2, 2, lastPlayed = now.minusDays(1))

			combineContinueWatching(
				resume = emptyList(),
				nextUp = listOf(next),
				recentlyPlayed = listOf(lastWatched),
			).names() shouldBe listOf("Office S02E03")
		}

		test("Partially watched movies are included") {
			combineContinueWatching(
				resume = listOf(movie("Movie", lastPlayed = now.minusDays(2))),
				nextUp = emptyList(),
				recentlyPlayed = emptyList(),
			).names() shouldBe listOf("Movie")
		}

		test("Items are ordered by most recent activity") {
			val movie = movie("Movie", lastPlayed = now.minusDays(3))
			val svuInProgress = episode("SVU S04E08", svu, 4, 8, lastPlayed = now.minusDays(2))
			val officeNext = episode("Office S02E03", office, 2, 3)
			val officeLastWatched = episode("Office S02E02", office, 2, 2, lastPlayed = now.minusHours(2))

			combineContinueWatching(
				resume = listOf(svuInProgress, movie),
				nextUp = listOf(officeNext),
				recentlyPlayed = listOf(officeLastWatched, svuInProgress),
			).names() shouldBe listOf("Office S02E03", "SVU S04E08", "Movie")
		}

		test("Stale in-progress episode is replaced by next up when a later episode was finished afterwards") {
			val staleInProgress = episode("SVU S04E08", svu, 4, 8, lastPlayed = now.minusDays(60))
			val finishedLater = episode("SVU S04E10", svu, 4, 10, lastPlayed = now.minusDays(1))
			val next = episode("SVU S04E11", svu, 4, 11)

			combineContinueWatching(
				resume = listOf(staleInProgress),
				nextUp = listOf(next),
				recentlyPlayed = listOf(finishedLater),
			).names() shouldBe listOf("SVU S04E11")
		}

		test("Series appearing twice in next up (new episode and rewatch) get one card") {
			val rewatchNext = episode("Big Bang S06E03", bigBang, 6, 3)
			val newEpisode = episode("Big Bang S12E24", bigBang, 12, 24)
			val lastWatched = episode("Big Bang S06E02", bigBang, 6, 2, lastPlayed = now.minusHours(5))

			combineContinueWatching(
				resume = emptyList(),
				nextUp = listOf(rewatchNext, newEpisode),
				recentlyPlayed = listOf(lastWatched),
			).names() shouldBe listOf("Big Bang S06E03")
		}

		test("Next up without known activity keeps server order after dated items") {
			val movie = movie("Movie", lastPlayed = now.minusDays(1))
			val officeNext = episode("Office S02E03", office, 2, 3)
			val svuNext = episode("SVU S04E09", svu, 4, 9)

			combineContinueWatching(
				resume = listOf(movie),
				nextUp = listOf(officeNext, svuNext),
				recentlyPlayed = emptyList(),
			).names() shouldBe listOf("Movie", "Office S02E03", "SVU S04E09")
		}

		test("Only the most recent in-progress episode of a series is shown") {
			val recent = episode("SVU S04E08", svu, 4, 8, lastPlayed = now.minusHours(1))
			val older = episode("SVU S02E01", svu, 2, 1, lastPlayed = now.minusDays(10))

			combineContinueWatching(
				resume = listOf(recent, older),
				nextUp = emptyList(),
				recentlyPlayed = emptyList(),
			).names() shouldBe listOf("SVU S04E08")
		}
	}

	context("collapseNewReleases") {
		test("One card per show using the earliest unwatched new episode") {
			val e6 = episode("SVU S26E06", svu, 26, 6, premiere = now.minusDays(2))
			val e5 = episode("SVU S26E05", svu, 26, 5, premiere = now.minusDays(9))

			collapseNewReleases(listOf(e6, e5)).names() shouldBe listOf("SVU S26E05")
		}

		test("Season released at once starts at the first episode") {
			val sameDay = now.minusDays(3)
			val episodes = (8 downTo 1).map { episode("Office S10E0$it", office, 10, it, premiere = sameDay) }

			collapseNewReleases(episodes).names() shouldBe listOf("Office S10E01")
		}

		test("Shows and movies are ordered by their newest release") {
			val svuNew = episode("SVU S26E06", svu, 26, 6, premiere = now.minusDays(1))
			val movie = movie("Movie", premiere = now.minusDays(4))
			val svuOlder = episode("SVU S26E05", svu, 26, 5, premiere = now.minusDays(8))
			val officeOld = episode("Office S10E01", office, 10, 1, premiere = now.minusDays(20))

			collapseNewReleases(listOf(svuNew, movie, svuOlder, officeOld)).names() shouldBe
				listOf("SVU S26E05", "Movie", "Office S10E01")
		}
	}

	context("createNewReleasesRequest") {
		test("Filters on release date window, unwatched and available items") {
			val request = createNewReleasesRequest(maxDays = 30, now = now)

			request.minPremiereDate shouldBe now.minusDays(30)
			request.maxPremiereDate shouldBe now
			request.isPlayed shouldBe false
			request.isMissing shouldBe false
			request.recursive shouldBe true
			request.includeItemTypes shouldBe setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE)
			request.sortBy shouldBe setOf(ItemSortBy.PREMIERE_DATE)
			request.sortOrder shouldBe setOf(SortOrder.DESCENDING)
		}

		test("Uses the configured window") {
			createNewReleasesRequest(maxDays = 7, now = now).minPremiereDate shouldBe now.minusDays(7)
		}
	}
})
