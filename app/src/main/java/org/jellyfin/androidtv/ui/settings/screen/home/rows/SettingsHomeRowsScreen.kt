package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.homerows.GENERAL_ERRORS_KEY
import org.jellyfin.androidtv.data.homerows.MAX_HOME_ROWS
import org.jellyfin.androidtv.data.homerows.editorRowLabel
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListMessage
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.settings.composable.SettingsConfirmDialog
import org.koin.compose.koinInject

private enum class PendingConfirm { RESET, SAVE_DEFAULT, REMOVE_DEFAULT }

/** Settings → Home on a server with HomeRows: the rows list, Save, and the layout actions. */
@Composable
fun SettingsHomeRowsScreen() {
	val router = LocalRouter.current
	val editor = rememberHomeRowsEditor()
	val state by editor.state.collectAsState()
	val loadFailed by editor.loadFailed.collectAsState()
	val busy by editor.busy.collectAsState()
	val userRepository = koinInject<UserRepository>()
	val userPreferences = koinInject<UserPreferences>()
	val isAdmin = userRepository.currentUser.collectAsState().value?.policy?.isAdministrator == true

	var confirm by remember { mutableStateOf<PendingConfirm?>(null) }
	var saved by remember { mutableStateOf(false) }

	LaunchedEffect(Unit) { editor.ensureLoaded() }

	val messages = listOfNotNull(state.generalError?.let { stringResource(R.string.home_rows_action_failed, it) }) +
		state.errors[GENERAL_ERRORS_KEY]?.values.orEmpty()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_prefs).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_rows_heading)) },
				captionContent = if (state.hasPendingEdits) {
					{ Text(stringResource(R.string.home_rows_unsaved)) }
				} else null,
			)
		}

		if (!state.loaded) {
			item {
				if (loadFailed) ListButton(
					headingContent = { Text(stringResource(R.string.home_rows_load_failed)) },
					onClick = { editor.ensureLoaded() },
				) else ListMessage { Text(stringResource(R.string.home_rows_loading)) }
			}
			return@SettingsColumn
		}

		items(state.rows, key = { it.key }) { editorRow ->
			val provider = state.provider(editorRow.row.provider)
			val serverError = state.errors[editorRow.key]?.values?.firstOrNull()
			val caption = when {
				!editorRow.row.isAvailable || provider == null -> stringResource(R.string.home_rows_status_unavailable)
				serverError != null -> serverError
				state.validation(editorRow).isNotEmpty() -> stringResource(R.string.home_rows_status_needs_settings)
				!editorRow.row.enabled -> stringResource(R.string.home_rows_status_hidden)
				else -> null
			}

			ListButton(
				headingContent = { Text(editorRowLabel(editorRow.row, provider)) },
				captionContent = caption?.let { { Text(it) } },
				onClick = { router.push(Routes.HOME_ROW, mapOf("key" to editorRow.key)) },
				modifier = Modifier.focusKey("home_row_${editorRow.key}"),
			)
		}

		items(messages) { message -> ListMessage { Text(message) } }

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_add)) },
				captionContent = if (state.rows.size >= MAX_HOME_ROWS) {
					{ Text(stringResource(R.string.home_rows_add_full)) }
				} else null,
				enabled = state.rows.size < MAX_HOME_ROWS,
				onClick = { router.push(Routes.HOME_ROW_ADD) },
				modifier = Modifier.focusKey("home_rows_add"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_save)) },
				captionContent = when {
					state.errors.isNotEmpty() -> {
						{ Text(stringResource(R.string.home_rows_save_failed)) }
					}

					saved && !state.hasPendingEdits -> {
						{ Text(stringResource(R.string.home_rows_saved)) }
					}

					else -> null
				},
				enabled = state.hasPendingEdits && !busy,
				onClick = { editor.save { saved = it } },
				modifier = Modifier.focusKey("home_rows_save"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_reset)) },
				enabled = !busy,
				onClick = { confirm = PendingConfirm.RESET },
				modifier = Modifier.focusKey("home_rows_reset"),
			)
		}

		if (isAdmin) {
			item {
				ListButton(
					headingContent = { Text(stringResource(R.string.home_rows_save_default)) },
					enabled = !busy,
					onClick = { confirm = PendingConfirm.SAVE_DEFAULT },
					modifier = Modifier.focusKey("home_rows_save_default"),
				)
			}

			item {
				ListButton(
					headingContent = { Text(stringResource(R.string.home_rows_remove_default)) },
					enabled = !busy,
					onClick = { confirm = PendingConfirm.REMOVE_DEFAULT },
					modifier = Modifier.focusKey("home_rows_remove_default"),
				)
			}
		}

		item {
			ListSection(headingContent = { Text(stringResource(R.string.lbl_next_up)) })
		}

		item {
			var nextUpRewatchingEnabled by rememberPreference(userPreferences, UserPreferences.nextUpRewatchingEnabled)

			ListButton(
				headingContent = { Text(stringResource(R.string.next_up_rewatching_enabled)) },
				trailingContent = { Checkbox(checked = nextUpRewatchingEnabled) },
				captionContent = { Text(stringResource(R.string.home_rows_rewatching_caption)) },
				onClick = { nextUpRewatchingEnabled = !nextUpRewatchingEnabled },
				modifier = Modifier.focusKey("next_up_rewatching_enabled"),
			)
		}
	}

	val pending = confirm
	SettingsConfirmDialog(
		visible = pending != null,
		title = when (pending) {
			PendingConfirm.SAVE_DEFAULT -> stringResource(R.string.home_rows_save_default)
			PendingConfirm.REMOVE_DEFAULT -> stringResource(R.string.home_rows_remove_default)
			else -> stringResource(R.string.home_rows_reset)
		},
		message = when (pending) {
			PendingConfirm.SAVE_DEFAULT -> stringResource(R.string.home_rows_save_default_confirm)
			PendingConfirm.REMOVE_DEFAULT -> stringResource(R.string.home_rows_remove_default_confirm)
			else -> stringResource(R.string.home_rows_reset_confirm)
		},
		actions = listOf(
			stringResource(R.string.home_rows_confirm) to {
				confirm = null
				when (pending) {
					PendingConfirm.RESET -> editor.reset()
					PendingConfirm.SAVE_DEFAULT -> editor.saveAsDefault()
					PendingConfirm.REMOVE_DEFAULT -> editor.removeDefault()
					null -> Unit
				}
			},
			stringResource(R.string.home_rows_cancel) to { confirm = null },
		),
		onDismissRequest = { confirm = null },
	)
}
