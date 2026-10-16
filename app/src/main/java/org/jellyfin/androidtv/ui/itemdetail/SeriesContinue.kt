package org.jellyfin.androidtv.ui.itemdetail

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.androidtv.data.querying.withUserPreferences
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.home.combineContinueWatching
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import java.util.UUID

/*
 * Personal fork: the episode to continue a series with, used by the series resume button. Uses the same selection as
 * the combined continue watching home row so the app has a single notion of "the next episode".
 */

private const val SERIES_RESUME_LIMIT = 10

/**
 * Select the episode to continue a series with: the in-progress episode, otherwise next up (including rewatches when
 * enabled). Returns null when there is nothing to continue, like for a never watched or finished series.
 */
fun selectSeriesContinueEpisode(
	resume: List<BaseItemDto>,
	nextUp: List<BaseItemDto>,
	recentlyPlayed: List<BaseItemDto>,
): BaseItemDto? = combineContinueWatching(resume, nextUp, recentlyPlayed).firstOrNull()

fun createSeriesResumeRequest(seriesId: UUID) = GetResumeItemsRequest(
	parentId = seriesId,
	mediaTypes = setOf(MediaType.VIDEO),
	includeItemTypes = setOf(BaseItemKind.EPISODE),
	fields = ItemRepository.itemFields,
	limit = SERIES_RESUME_LIMIT,
	enableTotalRecordCount = false,
)

fun createSeriesNextUpRequest(seriesId: UUID) = GetNextUpRequest(
	seriesId = seriesId,
	fields = ItemRepository.itemFields,
	limit = 1,
	enableTotalRecordCount = false,
)

fun createSeriesRecentlyPlayedRequest(seriesId: UUID) = GetItemsRequest(
	parentId = seriesId,
	recursive = true,
	includeItemTypes = setOf(BaseItemKind.EPISODE),
	isPlayed = true,
	sortBy = setOf(ItemSortBy.DATE_PLAYED),
	sortOrder = setOf(SortOrder.DESCENDING),
	limit = 1,
	enableUserData = true,
	enableImages = false,
	enableTotalRecordCount = false,
)

suspend fun ApiClient.getSeriesContinueEpisode(
	seriesId: UUID,
	userPreferences: UserPreferences,
): BaseItemDto? = coroutineScope {
	val resume = async { itemsApi.getResumeItems(createSeriesResumeRequest(seriesId)).content.items }
	val nextUp = async {
		tvShowsApi.getNextUp(createSeriesNextUpRequest(seriesId).withUserPreferences(userPreferences)).content.items
	}
	val recentlyPlayed = async { itemsApi.getItems(createSeriesRecentlyPlayedRequest(seriesId)).content.items }

	selectSeriesContinueEpisode(resume.await(), nextUp.await(), recentlyPlayed.await())
}
