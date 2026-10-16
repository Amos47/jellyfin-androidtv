package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences

/**
 * Snapshot of the settings that determine the home screen rows. The home screen is rebuilt when it changes while the
 * settings are open, because the settings overlay doesn't pause the home screen.
 */
data class HomeConfiguration(
	val sections: List<HomeSectionType>,
	val preferences: List<Any>,
)

fun getHomeConfiguration(
	userSettingPreferences: UserSettingPreferences,
	userPreferences: UserPreferences,
) = HomeConfiguration(
	sections = userSettingPreferences.activeHomesections,
	preferences = listOf(
		userPreferences[UserPreferences.homeNextUpMaxDays],
		userPreferences[UserPreferences.nextUpRewatchingEnabled],
		userPreferences[UserPreferences.homeNewReleasesMaxDays],
		userPreferences[UserPreferences.seriesThumbnailsEnabled],
	),
)
