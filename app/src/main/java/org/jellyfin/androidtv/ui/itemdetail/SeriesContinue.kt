package org.jellyfin.androidtv.ui.itemdetail

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.androidtv.data.querying.withUserPreferences
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import java.util.UUID

/*
 * Personal fork: the episode to continue a series with, used by the series resume button. The server decides through
 * Next Up (one card per series: a new episode wins, then the rewatch marker), like the phone and Home.
 */

private const val SERIES_RESUME_LIMIT = 1

/**
 * Select the episode to continue a series with: the server's next up, otherwise the most recent in-progress episode.
 * Returns null when there is nothing to continue, like for a never watched or finished series.
 */
fun selectSeriesContinueEpisode(
	nextUp: List<BaseItemDto>,
	resume: List<BaseItemDto>,
): BaseItemDto? = nextUp.firstOrNull() ?: resume.firstOrNull()

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

suspend fun ApiClient.getSeriesContinueEpisode(
	seriesId: UUID,
	userPreferences: UserPreferences,
): BaseItemDto? = coroutineScope {
	val nextUp = async {
		tvShowsApi.getNextUp(createSeriesNextUpRequest(seriesId).withUserPreferences(userPreferences)).content.items
	}
	val resume = async { itemsApi.getResumeItems(createSeriesResumeRequest(seriesId)).content.items }

	selectSeriesContinueEpisode(nextUp.await(), resume.await())
}
