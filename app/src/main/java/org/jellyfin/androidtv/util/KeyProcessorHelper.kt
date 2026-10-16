package org.jellyfin.androidtv.util

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.itemdetail.getSeriesContinueEpisode
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID

fun FragmentActivity.playFirstUnwatchedItem(parentId: UUID) {
	val api by inject<ApiClient>()
	val playbackHelper by inject<PlaybackHelper>()

	lifecycleScope.launch(Dispatchers.IO) {
		runCatching {
			api.itemsApi.getItems(
				parentId = parentId,
				recursive = true,
				isMissing = false,
				sortBy = setOf(ItemSortBy.SORT_NAME),
				sortOrder = setOf(SortOrder.ASCENDING),
				limit = 1,
				excludeItemTypes = setOf(),
				filters = setOf(ItemFilter.IS_UNPLAYED),
			)
		}.fold(
			onSuccess = { response ->
				val item = response.content.items?.firstOrNull()
				withContext(Dispatchers.Main) {
					if (item == null) {
						Toast.makeText(
							this@playFirstUnwatchedItem,
							R.string.msg_no_items,
							Toast.LENGTH_LONG
						).show()
					} else {
						playbackHelper.retrieveAndPlay(item.id, false, this@playFirstUnwatchedItem)
					}
				}
			},
			onFailure = { error ->
				Timber.e(error, "Error trying to play first unwatched")
				Toast.makeText(
					this@playFirstUnwatchedItem,
					R.string.msg_video_playback_error,
					Toast.LENGTH_LONG
				).show()
			},
		)
	}
}

/**
 * Personal fork: continue a series where the user left off (the in-progress episode, otherwise next up). Runs
 * [onNothingToContinue] when there is no episode to continue with, like for a never watched or finished series.
 */
fun FragmentActivity.playSeriesContinueEpisode(seriesId: UUID, onNothingToContinue: Runnable) {
	val api by inject<ApiClient>()
	val userPreferences by inject<UserPreferences>()
	val playbackHelper by inject<PlaybackHelper>()

	lifecycleScope.launch {
		val episode = runCatching {
			withContext(Dispatchers.IO) { api.getSeriesContinueEpisode(seriesId, userPreferences) }
		}.onFailure { error ->
			Timber.w(error, "Failed to get episode to continue series with")
		}.getOrNull()

		if (episode != null) playbackHelper.retrieveAndPlay(episode.id, false, this@playSeriesContinueEpisode)
		else onNothingToContinue.run()
	}
}
