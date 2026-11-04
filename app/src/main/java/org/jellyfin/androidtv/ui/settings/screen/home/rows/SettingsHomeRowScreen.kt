package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.homerows.HomeRowFieldKind
import org.jellyfin.androidtv.data.homerows.editorRowLabel
import org.jellyfin.androidtv.data.homerows.effectiveValue
import org.jellyfin.androidtv.data.homerows.fields
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

/** One row of the rows editor: order, visibility, title and the provider's settings. */
@Composable
fun SettingsHomeRowScreen(key: String) {
	val router = LocalRouter.current
	val editor = rememberHomeRowsEditor()
	val state by editor.state.collectAsState()
	val editorRow = state.row(key)

	// The row is gone (removed, discarded or reloaded): go back to the list.
	LaunchedEffect(editorRow == null) { if (editorRow == null) router.back() }
	if (editorRow == null) return

	val row = editorRow.row
	val provider = state.provider(row.provider)
	val index = state.rows.indexOf(editorRow)
	val localErrors = state.validation(editorRow)
	val serverErrors = state.errors[key].orEmpty()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_rows_heading).uppercase()) },
				headingContent = { Text(editorRowLabel(row, provider)) },
				captionContent = provider?.description?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_move_up)) },
				enabled = index > 0,
				onClick = { editor.moveUp(key) },
				modifier = Modifier.focusKey("move_up"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_move_down)) },
				enabled = index < state.rows.lastIndex,
				onClick = { editor.moveDown(key) },
				modifier = Modifier.focusKey("move_down"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_show)) },
				trailingContent = { Checkbox(checked = row.enabled) },
				onClick = { editor.setEnabled(key, !row.enabled) },
				modifier = Modifier.focusKey("enabled"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_title)) },
				captionContent = {
					Text(serverErrors[TITLE_SETTING] ?: row.title ?: stringResource(R.string.home_rows_title_default))
				},
				onClick = { router.push(Routes.HOME_ROW_TEXT, mapOf("key" to key, "setting" to TITLE_SETTING)) },
				modifier = Modifier.focusKey("title"),
			)
		}

		items(provider?.fields().orEmpty(), key = { it.key }) { field ->
			val value = field.effectiveValue(row.config)
			val error = serverErrors[field.key] ?: localErrors[field.key]?.text()

			ListButton(
				headingContent = { Text(field.label) },
				captionContent = { Text(error ?: field.valueText(value)) },
				trailingContent = if (field.kind == HomeRowFieldKind.BOOLEAN) {
					{ Checkbox(checked = (value as? JsonPrimitive)?.booleanOrNull == true) }
				} else null,
				onClick = {
					when (field.kind) {
						HomeRowFieldKind.BOOLEAN -> editor.setValue(
							key,
							field.key,
							JsonPrimitive((value as? JsonPrimitive)?.booleanOrNull != true)
						)

						HomeRowFieldKind.ENUM, HomeRowFieldKind.MULTI_ENUM ->
							router.push(Routes.HOME_ROW_OPTION, mapOf("key" to key, "setting" to field.key))

						HomeRowFieldKind.INTEGER -> router.push(Routes.HOME_ROW_NUMBER, mapOf("key" to key, "setting" to field.key))
						HomeRowFieldKind.STRING -> router.push(Routes.HOME_ROW_TEXT, mapOf("key" to key, "setting" to field.key))
					}
				},
				modifier = Modifier.focusKey("setting_${field.key}"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_remove)) },
				// The effect above goes back once the row is gone; popping here too would leave the list as well.
				onClick = { editor.remove(key) },
				modifier = Modifier.focusKey("remove"),
			)
		}
	}
}
