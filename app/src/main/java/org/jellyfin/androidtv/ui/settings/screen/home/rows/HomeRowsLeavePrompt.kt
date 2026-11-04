package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.homerows.LeaveChoice
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.composable.SettingsConfirmDialog

/**
 * Asks Save / Discard / Keep editing when leaving the rows editor, or closing the settings, with unsaved edits.
 * Shown by the settings overlay so it covers both. A failed save keeps the editor open with its errors.
 */
@Composable
fun HomeRowsLeavePrompt(onCloseSettings: () -> Unit) {
	val router = LocalRouter.current
	val editor = rememberHomeRowsEditor()
	val target by editor.leavePrompt.collectAsState()

	fun leave(to: LeaveTarget) = when (to) {
		LeaveTarget.BACK -> router.back()
		LeaveTarget.CLOSE_SETTINGS -> {
			editor.clear()
			onCloseSettings()
		}
	}

	SettingsConfirmDialog(
		visible = target != null,
		title = stringResource(R.string.home_rows_unsaved_title),
		message = stringResource(R.string.home_rows_unsaved_message),
		actions = listOf(
			stringResource(R.string.home_rows_save) to { editor.chooseLeave(LeaveChoice.SAVE, ::leave) },
			stringResource(R.string.home_rows_discard) to { editor.chooseLeave(LeaveChoice.DISCARD, ::leave) },
			stringResource(R.string.home_rows_keep_editing) to { editor.cancelLeave() },
		),
		onDismissRequest = { editor.cancelLeave() },
	)
}
