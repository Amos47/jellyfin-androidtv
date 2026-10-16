package org.jellyfin.androidtv.ui.settings.screen.home

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsHomeScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	val userSettingPreferences = koinInject<UserSettingPreferences>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_prefs)) },
			)
		}

		itemsIndexed(userSettingPreferences.homesections) { index, section ->
			ListButton(
				headingContent = { Text(stringResource(R.string.home_section_i, index + 1)) },
				captionContent = { Text(stringResource(userSettingPreferences[section].nameRes)) },
				onClick = { router.push(Routes.HOME_SECTION, mapOf("index" to index.toString())) },
				modifier = Modifier.focusKey("home_section_$index")
			)
		}

		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.lbl_next_up)) },
			)
		}

		item {
			val homeNextUpMaxDays by rememberPreference(userPreferences, UserPreferences.homeNextUpMaxDays)
			val options = getNextUpCutoffOptions()
			val selectedCaption = options.firstOrNull { it.first == homeNextUpMaxDays }?.second
				?: stringResource(R.string.home_next_up_max_days_disabled)

			ListButton(
				headingContent = { Text(stringResource(R.string.home_next_up_max_days)) },
				captionContent = { Text(selectedCaption) },
				onClick = { router.push(Routes.HOME_NEXT_UP_CUTOFF) },
				modifier = Modifier.focusKey(Routes.HOME_NEXT_UP_CUTOFF)
			)
		}

		item {
			var nextUpRewatchingEnabled by rememberPreference(userPreferences, UserPreferences.nextUpRewatchingEnabled)

			ListButton(
				headingContent = { Text(stringResource(R.string.next_up_rewatching_enabled)) },
				trailingContent = { Checkbox(checked = nextUpRewatchingEnabled) },
				captionContent = { Text(stringResource(R.string.next_up_rewatching_enabled_description)) },
				onClick = { nextUpRewatchingEnabled = !nextUpRewatchingEnabled },
				modifier = Modifier.focusKey("next_up_rewatching_enabled")
			)
		}

		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.lbl_new_releases)) },
			)
		}

		item {
			val homeNewReleasesMaxDays by rememberPreference(userPreferences, UserPreferences.homeNewReleasesMaxDays)
			val selectedCaption = getNewReleasesPeriodOptions().firstOrNull { it.first == homeNewReleasesMaxDays }?.second.orEmpty()

			ListButton(
				headingContent = { Text(stringResource(R.string.home_new_releases_max_days)) },
				captionContent = { Text(selectedCaption) },
				onClick = { router.push(Routes.HOME_NEW_RELEASES_PERIOD) },
				modifier = Modifier.focusKey(Routes.HOME_NEW_RELEASES_PERIOD)
			)
		}
	}
}
