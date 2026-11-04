package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.homerows.HomeRowFieldKind
import org.jellyfin.androidtv.data.homerows.effectiveValue
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

/** Pick one (radio buttons, goes back on pick) or pick many (checkboxes, stays open) for a row setting. */
@Composable
fun SettingsHomeRowOptionScreen(key: String, setting: String) {
	val router = LocalRouter.current
	val editor = rememberHomeRowsEditor()
	val state by editor.state.collectAsState()
	val editorRow = state.row(key)
	val field = state.field(key, setting)

	LaunchedEffect(field == null) { if (field == null) router.back() }
	if (editorRow == null || field == null) return

	val value = field.effectiveValue(editorRow.row.config)
	val multi = field.kind == HomeRowFieldKind.MULTI_ENUM
	val selected = when (value) {
		is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
		is JsonPrimitive -> setOf(value.content)
		else -> emptySet()
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_rows_heading).uppercase()) },
				headingContent = { Text(field.label) },
				captionContent = state.validation(editorRow)[field.key]?.let { error -> { Text(error.text()) } },
			)
		}

		items(field.options, key = { it.value }) { option ->
			val checked = option.value in selected
			ListButton(
				headingContent = { Text(option.label) },
				trailingContent = { if (multi) Checkbox(checked = checked) else RadioButton(checked = checked) },
				onClick = {
					if (multi) {
						val next = if (checked) selected - option.value else selected + option.value
						// Keep the server's option order.
						val ordered = field.options.map { it.value }.filter { it in next }
						editor.setValue(key, setting, JsonArray(ordered.map(::JsonPrimitive)))
					} else {
						editor.setValue(key, setting, JsonPrimitive(option.value))
						router.back()
					}
				},
				modifier = Modifier.focusKey("option_${option.value}", initialFocus = !multi && checked),
			)
		}
	}
}
