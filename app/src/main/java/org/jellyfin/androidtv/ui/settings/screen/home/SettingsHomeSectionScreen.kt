package org.jellyfin.androidtv.ui.settings.screen.home

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.HomeSectionOverrideTypes
import org.jellyfin.androidtv.preference.LocalOnlyHomeSectionTypes
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListMessage
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsHomeSectionScreen(index: Int) {
	val router = LocalRouter.current
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val sectionPreference = userSettingPreferences.homesections.getOrNull(index)

	if (sectionPreference == null) {
		ListMessage {
			Text("Unknown section $index")
		}

		return
	}

	var sectionType by rememberPreference(userSettingPreferences, sectionPreference)
	// Personal fork: per-user override for this device, see HomeSectionOverrides.kt
	var sectionOverride by remember { mutableStateOf(userSettingPreferences.homeSectionOverrides[index]) }

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_prefs).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_section_i, index + 1)) },
			)
		}

		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.home_section_all_devices)) },
				captionContent = { Text(stringResource(R.string.home_section_all_devices_description)) },
			)
		}

		items(HomeSectionType.entries.filterNot { it in LocalOnlyHomeSectionTypes }) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = sectionType == entry) },
				onClick = {
					sectionType = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("section_type_${entry.name}", initialFocus = sectionOverride == null && sectionType == entry)
			)
		}

		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.home_section_this_device)) },
				captionContent = { Text(stringResource(R.string.home_section_this_device_description)) },
			)
		}

		items(listOf<HomeSectionType?>(null) + HomeSectionOverrideTypes) { entry ->
			ListButton(
				headingContent = {
					Text(
						when (entry) {
							null -> stringResource(R.string.home_section_override_none)
							HomeSectionType.NONE -> stringResource(R.string.home_section_override_hidden)
							else -> stringResource(entry.nameRes)
						}
					)
				},
				trailingContent = { RadioButton(checked = sectionOverride == entry) },
				onClick = {
					userSettingPreferences.setHomeSectionOverride(index, entry)
					sectionOverride = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("section_override_${entry?.name ?: "server"}", initialFocus = sectionOverride != null && sectionOverride == entry)
			)
		}
	}
}
