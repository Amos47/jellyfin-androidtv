package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.util.UUID

/*
 * Home rows added by the personal fork:
 * - Continue watching (combined): in-progress items and next up episodes, one card per series.
 * - New releases: unwatched movies and episodes by release date, one card per series.
 */

internal const val CONTINUE_WATCHING_LIMIT = 50
private const val CONTINUE_WATCHING_QUERY_LIMIT = 50
private const val RECENTLY_PLAYED_QUERY_LIMIT = 100
internal const val NEW_RELEASES_LIMIT = 50
private const val NEW_RELEASES_QUERY_LIMIT = 200

/**
 * Home row using one of the custom query types ([QueryType.ContinueWatching] or [QueryType.NewReleases]).
 */
class HomeFragmentCustomRow(
	private val title: String,
	private val queryType: QueryType,
	private val changeTriggers: Array<ChangeTriggerType>,
) : HomeFragmentRow, KoinComponent {
	private val userPreferences by inject<UserPreferences>()

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val preferParentThumb = userPreferences[UserPreferences.seriesThumbnailsEnabled]
		val rowAdapter = ItemRowAdapter(context, queryType, preferParentThumb, cardPresenter, rowsAdapter)
		rowAdapter.setReRetrieveTriggers(changeTriggers)
		val row = ListRow(HeaderItem(title), rowAdapter)
		rowAdapter.setRow(row)
		rowAdapter.Retrieve()
		rowsAdapter.add(row)
	}
}

// Recently added

/**
 * Replace episodes and seasons with their series so recently added rows show series posters with unwatched counts. The
 * server returns a single new episode instead of its series. Items keep the position of their first occurrence, and
 * items whose series is not in [series] are kept as-is.
 */
fun replaceWithSeries(items: List<BaseItemDto>, series: List<BaseItemDto>): List<BaseItemDto> {
	val seriesById = series.associateBy { it.id }
	return items
		.map { item -> item.latestSeriesId?.let(seriesById::get) ?: item }
		.distinctBy { it.id }
}

/**
 * The series id of an episode or season in a recently added row, or null for other items.
 */
val BaseItemDto.latestSeriesId: UUID?
	get() = seriesId.takeIf { type == BaseItemKind.EPISODE || type == BaseItemKind.SEASON }

// Continue watching

/**
 * Keep one item per series, using the first occurrence. Items that are not part of a series are kept as-is. Used to
 * show a single in-progress episode per series in the resume row, which is ordered by last played.
 */
fun List<BaseItemDto>.distinctBySeries(): List<BaseItemDto> = distinctBy { item -> item.seriesId ?: item.id }

/**
 * Combine in-progress items with next up episodes into a single list with one entry per series, ordered by most
 * recent activity.
 *
 * Next up never contains an episode that is in progress (the server skips resumable episodes, including during a
 * rewatch), so the in-progress episode from [resume] is shown for a series instead of its next up episode. When an
 * episode of the series was finished after the in-progress one was last played, the in-progress episode is considered
 * abandoned and next up is used instead.
 *
 * Next up does not include when a series was last watched, so that is taken from [recentlyPlayed], a list of recently
 * played episodes. Entries without any known activity keep their server order at the end.
 */
fun combineContinueWatching(
	resume: List<BaseItemDto>,
	nextUp: List<BaseItemDto>,
	recentlyPlayed: List<BaseItemDto>,
): List<BaseItemDto> {
	val seriesActivity: Map<UUID, LocalDateTime> = recentlyPlayed
		.mapNotNull { item ->
			val seriesId = item.seriesId ?: return@mapNotNull null
			val lastPlayed = item.userData?.lastPlayedDate ?: return@mapNotNull null
			seriesId to lastPlayed
		}
		.groupBy({ it.first }, { it.second })
		.mapValues { (_, dates) -> dates.max() }

	// The first item per series wins: resume is ordered by last played and next up by series activity
	val nextUpBySeries = nextUp
		.filter { it.seriesId != null }
		.distinctBy { it.seriesId }
		.associateBy { it.seriesId!! }

	val entries = mutableListOf<Pair<BaseItemDto, LocalDateTime?>>()
	val handledSeries = mutableSetOf<UUID>()

	for (item in resume) {
		val seriesId = item.seriesId
		if (seriesId == null) {
			entries += item to item.userData?.lastPlayedDate
			continue
		}

		if (!handledSeries.add(seriesId)) continue

		val resumeDate = item.userData?.lastPlayedDate
		val playedDate = seriesActivity[seriesId]
		val nextUpItem = nextUpBySeries[seriesId]
		entries += when {
			nextUpItem != null && resumeDate != null && playedDate != null && playedDate > resumeDate -> nextUpItem to playedDate
			else -> item to (resumeDate ?: playedDate)
		}
	}

	for ((seriesId, item) in nextUpBySeries) {
		if (handledSeries.add(seriesId)) entries += item to seriesActivity[seriesId]
	}

	// Stable sort, items without a date keep their order at the end
	return entries
		.sortedWith(compareByDescending(nullsFirst()) { it.second })
		.map { it.first }
}

fun createContinueWatchingResumeRequest() = GetResumeItemsRequest(
	limit = CONTINUE_WATCHING_QUERY_LIMIT,
	fields = ItemRepository.browseFields,
	imageTypeLimit = 1,
	enableTotalRecordCount = false,
	mediaTypes = setOf(MediaType.VIDEO),
	excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
)

fun createContinueWatchingNextUpRequest(maxDays: Int, now: LocalDateTime) = GetNextUpRequest(
	limit = CONTINUE_WATCHING_QUERY_LIMIT,
	fields = ItemRepository.browseFields,
	imageTypeLimit = 1,
	enableResumable = false,
	enableTotalRecordCount = false,
	nextUpDateCutoff = maxDays.takeIf { it > 0 }?.let { now.minusDays(it.toLong()) },
)

fun createRecentlyPlayedEpisodesRequest() = GetItemsRequest(
	recursive = true,
	includeItemTypes = setOf(BaseItemKind.EPISODE),
	isPlayed = true,
	sortBy = setOf(ItemSortBy.DATE_PLAYED),
	sortOrder = setOf(SortOrder.DESCENDING),
	limit = RECENTLY_PLAYED_QUERY_LIMIT,
	enableUserData = true,
	enableImages = false,
	enableTotalRecordCount = false,
)

// New releases

/**
 * Collapse new releases to one entry per series, keeping movies as-is. A series shows its earliest unwatched new
 * episode so it can be watched in order, and is positioned by its newest release. [items] must be ordered by release
 * date, newest first.
 */
fun collapseNewReleases(items: List<BaseItemDto>): List<BaseItemDto> = items
	.groupBy { item -> item.seriesId.takeIf { item.type == BaseItemKind.EPISODE } ?: item.id }
	.values
	.map { group ->
		group.minWith(
			compareBy<BaseItemDto>({ it.premiereDate }, { it.parentIndexNumber }, { it.indexNumber })
		)
	}

fun createNewReleasesRequest(maxDays: Int, now: LocalDateTime) = GetItemsRequest(
	recursive = true,
	includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
	minPremiereDate = now.minusDays(maxDays.toLong()),
	maxPremiereDate = now,
	isPlayed = false,
	isMissing = false,
	sortBy = setOf(ItemSortBy.PREMIERE_DATE),
	sortOrder = setOf(SortOrder.DESCENDING),
	fields = ItemRepository.browseFields,
	imageTypeLimit = 1,
	enableTotalRecordCount = false,
	limit = NEW_RELEASES_QUERY_LIMIT,
)

