package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonPrimitive
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.homerows.MAX_HOME_ROW_TITLE
import org.jellyfin.androidtv.data.homerows.effectiveValue
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.design.Tokens

/** Text entry for a row's title ([TITLE_SETTING]) or a string setting. Empty means the default. */
@Composable
fun SettingsHomeRowTextScreen(key: String, setting: String) {
	val router = LocalRouter.current
	val editor = rememberHomeRowsEditor()
	val state by editor.state.collectAsState()
	val editorRow = state.row(key)
	val field = state.field(key, setting)
	val isTitle = setting == TITLE_SETTING

	LaunchedEffect(editorRow == null) { if (editorRow == null) router.back() }
	if (editorRow == null || (!isTitle && field == null)) return

	val label = if (isTitle) stringResource(R.string.home_rows_title) else field!!.label
	val maxLength = if (isTitle) MAX_HOME_ROW_TITLE else field?.maxLength
	val initial = if (isTitle) editorRow.row.title.orEmpty()
	else (field?.effectiveValue(editorRow.row.config) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
	var text by remember(key, setting) { mutableStateOf(initial) }

	fun apply() {
		val value = text.trim().let { if (maxLength != null) it.take(maxLength) else it }
		if (isTitle) editor.setTitle(key, value)
		else editor.setValue(key, setting, value.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive))
		router.back()
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_rows_heading).uppercase()) },
				headingContent = { Text(label) },
			)
		}

		item {
			val interactionSource = remember { MutableInteractionSource() }
			val focused by interactionSource.collectIsFocusedAsState()
			val color = if (focused) JellyfinTheme.colorScheme.inputFocused to JellyfinTheme.colorScheme.onInputFocused
			else JellyfinTheme.colorScheme.input to JellyfinTheme.colorScheme.onInput

			ProvideTextStyle(LocalTextStyle.current.copy(color = color.second, fontSize = 16.sp)) {
				BasicTextField(
					value = text,
					onValueChange = { text = if (maxLength != null) it.take(maxLength) else it },
					singleLine = true,
					interactionSource = interactionSource,
					keyboardActions = KeyboardActions { apply() },
					keyboardOptions = KeyboardOptions.Default.copy(
						keyboardType = KeyboardType.Text,
						imeAction = ImeAction.Done,
						showKeyboardOnFocus = true,
					),
					textStyle = LocalTextStyle.current,
					cursorBrush = SolidColor(color.first),
					decorationBox = { inner ->
						Box(
							Modifier
								.fillMaxWidth()
								.border(2.dp, color.first, RoundedCornerShape(percent = 30))
								.padding(12.dp)
						) { inner() }
					},
					modifier = Modifier
						.padding(horizontal = Tokens.Space.spaceSm, vertical = Tokens.Space.spaceXs)
						.focusKey("text_input", initialFocus = true),
				)
			}
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_done)) },
				onClick = { apply() },
				modifier = Modifier.focusKey("text_done"),
			)
		}
	}
}
