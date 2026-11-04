package org.jellyfin.androidtv.ui.settings.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.androidtv.ui.navigation.ProvideRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsDialog
import org.jellyfin.androidtv.ui.settings.composable.SettingsRouterContent
import org.jellyfin.androidtv.ui.settings.routes
import org.jellyfin.androidtv.ui.settings.screen.home.rows.HomeRowsLeavePrompt
import org.jellyfin.androidtv.ui.settings.screen.home.rows.LeaveTarget
import org.jellyfin.androidtv.ui.settings.screen.home.rows.rememberHomeRowsEditor
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun MainActivitySettings() {
	val viewModel = koinActivityViewModel<SettingsViewModel>()
	val visible by viewModel.visible.collectAsState()
	// Personal fork: ask before closing with unsaved home row edits, see screen/home/rows
	val homeRowsEditor = rememberHomeRowsEditor()

	ProvideRouter(routes, Routes.MAIN) {
		SettingsDialog(
			visible = visible,
			onDismissRequest = {
				if (homeRowsEditor.state.value.hasPendingEdits) homeRowsEditor.requestLeave(LeaveTarget.CLOSE_SETTINGS)
				else {
					homeRowsEditor.clear()
					viewModel.hide()
				}
			}
		) {
			SettingsRouterContent()
			HomeRowsLeavePrompt(onCloseSettings = { viewModel.hide() })
		}
	}
}
