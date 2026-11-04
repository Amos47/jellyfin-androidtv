package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalShapes
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.design.Tokens

/**
 * A small confirmation dialog with one button per action (personal fork, Custom Home Rows). Focus starts on the last
 * action, so the safe choice goes last. Back dismisses it.
 */
@Composable
fun SettingsConfirmDialog(
	visible: Boolean,
	title: String,
	message: String?,
	actions: List<Pair<String, () -> Unit>>,
	onDismissRequest: () -> Unit,
) {
	DialogBase(visible = visible, onDismissRequest = onDismissRequest) {
		val focusRequester = remember { FocusRequester() }

		Column(
			modifier = Modifier
				.width(400.dp)
				.clip(LocalShapes.current.large)
				.background(JellyfinTheme.colorScheme.surface)
				.padding(Tokens.Space.spaceSm)
		) {
			ListSection(
				headingContent = { Text(title) },
				captionContent = message?.let { { Text(it) } },
			)

			actions.forEachIndexed { index, (label, action) ->
				ListButton(
					headingContent = { Text(label) },
					onClick = action,
					modifier = if (index == actions.lastIndex) Modifier.focusRequester(focusRequester) else Modifier,
				)
			}
		}

		LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
	}
}
