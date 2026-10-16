package org.jellyfin.androidtv.ui.itemhandling

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.querying.withUserPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.home.CONTINUE_WATCHING_LIMIT
import org.jellyfin.androidtv.ui.home.NEW_RELEASES_LIMIT
import org.jellyfin.androidtv.ui.home.collapseNewReleases
import org.jellyfin.androidtv.ui.home.combineContinueWatching
import org.jellyfin.androidtv.ui.home.createContinueWatchingNextUpRequest
import org.jellyfin.androidtv.ui.home.createContinueWatchingResumeRequest
import org.jellyfin.androidtv.ui.home.createNewReleasesRequest
import org.jellyfin.androidtv.ui.home.createRecentlyPlayedEpisodesRequest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import java.time.LocalDateTime

/*
 * Loading for the personal fork home rows, see ui/home/CustomHomeRows.kt. These live in this package to access the
 * protected members of ItemRowAdapter.
 */

fun ItemRowAdapter.retrieveContinueWatching(api: ApiClient, userPreferences: UserPreferences) {
	ProcessLifecycleOwner.get().lifecycleScope.launch {
		runCatching {
			val nextUpMaxDays = userPreferences[UserPreferences.homeNextUpMaxDays]
			val nextUpRequest = createContinueWatchingNextUpRequest(nextUpMaxDays, LocalDateTime.now())
				.withUserPreferences(userPreferences)

			val items = withContext(Dispatchers.IO) {
				val resume = async { api.itemsApi.getResumeItems(createContinueWatchingResumeRequest()).content.items }
				val nextUp = async { api.tvShowsApi.getNextUp(nextUpRequest).content.items }
				val recentlyPlayed = async { api.itemsApi.getItems(createRecentlyPlayedEpisodesRequest()).content.items }

				combineContinueWatching(resume.await(), nextUp.await(), recentlyPlayed.await())
					.take(CONTINUE_WATCHING_LIMIT)
			}

			setItems(
				items = items,
				transform = { item, _ -> BaseItemDtoBaseRowItem(item, preferParentThumb, isStaticHeight) }
			)

			if (items.isEmpty()) removeRow()
		}.fold(
			onSuccess = { notifyRetrieveFinished() },
			onFailure = { error -> notifyRetrieveFinished(error as? Exception) }
		)
	}
}

fun ItemRowAdapter.retrieveNewReleases(api: ApiClient, userPreferences: UserPreferences) {
	ProcessLifecycleOwner.get().lifecycleScope.launch {
		runCatching {
			val maxDays = userPreferences[UserPreferences.homeNewReleasesMaxDays]
			val request = createNewReleasesRequest(maxDays, LocalDateTime.now())

			val items = withContext(Dispatchers.IO) {
				val response = api.itemsApi.getItems(request).content
				collapseNewReleases(response.items).take(NEW_RELEASES_LIMIT)
			}

			setItems(
				items = items,
				transform = { item, _ -> BaseItemDtoBaseRowItem(item, preferParentThumb, isStaticHeight) }
			)

			if (items.isEmpty()) removeRow()
		}.fold(
			onSuccess = { notifyRetrieveFinished() },
			onFailure = { error -> notifyRetrieveFinished(error as? Exception) }
		)
	}
}
