package org.jellyfin.androidtv.ui.settings.screen.home

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.util.getQuantityString
import org.koin.compose.koinInject

@Suppress("MagicNumber")
private val NewReleasesMaxDaysOptions = listOf(7, 14, 30, 60, 90, 180, 365)

@Composable
fun getNewReleasesPeriodOptions(): List<Pair<Int, String>> {
	val context = LocalContext.current
	return NewReleasesMaxDaysOptions.map { days -> days to context.getQuantityString(R.plurals.days, days) }
}

@Composable
fun SettingsHomeNewReleasesPeriodScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var homeNewReleasesMaxDays by rememberPreference(userPreferences, UserPreferences.homeNewReleasesMaxDays)
	val options = getNewReleasesPeriodOptions()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_prefs).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_new_releases_max_days)) },
			)
		}

		items(options) { (days, label) ->
			ListButton(
				headingContent = { Text(label) },
				trailingContent = { RadioButton(checked = homeNewReleasesMaxDays == days) },
				onClick = {
					homeNewReleasesMaxDays = days
					router.back()
				},
				modifier = Modifier
					.focusKey("new_releases_period_$days", initialFocus = homeNewReleasesMaxDays == days)
			)
		}
	}
}
