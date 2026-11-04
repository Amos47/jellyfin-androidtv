package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.homerows.effectiveValue
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RangeControl
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListControl
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.design.Tokens
import kotlin.math.roundToInt

/** A whole number setting: a slider within the schema's bounds (left/right), or −/+ when it has none. */
@Composable
fun SettingsHomeRowNumberScreen(key: String, setting: String) {
	val router = LocalRouter.current
	val editor = rememberHomeRowsEditor()
	val state by editor.state.collectAsState()
	val editorRow = state.row(key)
	val field = state.field(key, setting)

	LaunchedEffect(field == null) { if (field == null) router.back() }
	if (editorRow == null || field == null) return

	val min = field.min
	val max = field.max
	val value = (field.effectiveValue(editorRow.row.config) as? JsonPrimitive)?.intOrNull ?: min ?: 0
	fun set(next: Int) {
		val clamped = next.coerceIn(min ?: Int.MIN_VALUE, max ?: Int.MAX_VALUE)
		editor.setValue(key, setting, JsonPrimitive(clamped))
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_rows_heading).uppercase()) },
				headingContent = { Text(field.label) },
				captionContent = if (min != null && max != null) {
					{ Text(stringResource(R.string.home_rows_range, min, max)) }
				} else null,
			)
		}

		if (min != null && max != null && max > min) {
			item {
				val interactionSource = remember { MutableInteractionSource() }
				ListControl(
					headingContent = { Text(field.label) },
					interactionSource = interactionSource,
					modifier = Modifier.focusKey("number_range", initialFocus = true),
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						RangeControl(
							modifier = Modifier
								.height(4.dp)
								.weight(1f),
							interactionSource = interactionSource,
							min = min.toFloat(),
							max = max.toFloat(),
							stepForward = 1f,
							value = value.toFloat(),
							onValueChange = { set(it.roundToInt()) },
						)

						Spacer(Modifier.width(Tokens.Space.spaceSm))

						Box(modifier = Modifier.sizeIn(minWidth = 32.dp), contentAlignment = Alignment.CenterEnd) {
							Text(value.toString())
						}
					}
				}
			}
		} else {
			item {
				ListSection(headingContent = { Text(value.toString()) })
			}

			item {
				ListButton(
					headingContent = { Text(stringResource(R.string.home_rows_increase)) },
					enabled = max == null || value < max,
					onClick = { set(value + 1) },
					modifier = Modifier.focusKey("number_increase", initialFocus = true),
				)
			}

			item {
				ListButton(
					headingContent = { Text(stringResource(R.string.home_rows_decrease)) },
					enabled = min == null || value > min,
					onClick = { set(value - 1) },
					modifier = Modifier.focusKey("number_decrease"),
				)
			}
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_rows_done)) },
				onClick = { router.back() },
				modifier = Modifier.focusKey("number_done"),
			)
		}
	}
}
