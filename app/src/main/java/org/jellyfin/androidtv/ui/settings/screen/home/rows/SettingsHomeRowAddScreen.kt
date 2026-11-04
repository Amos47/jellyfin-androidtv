package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

/** The row types the server offers. Picking one adds a row and opens it. */
@Composable
fun SettingsHomeRowAddScreen() {
	val router = LocalRouter.current
	val editor = rememberHomeRowsEditor()
	val state by editor.state.collectAsState()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_rows_heading).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_rows_add)) },
			)
		}

		items(state.providers, key = { it.id }) { provider ->
			ListButton(
				headingContent = { Text(provider.name) },
				captionContent = provider.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
				onClick = {
					val key = editor.add(provider.id)
					if (key == null) router.back()
					else router.replace(Routes.HOME_ROW, mapOf("key" to key))
				},
				modifier = Modifier.focusKey("provider_${provider.id}"),
			)
		}
	}
}
