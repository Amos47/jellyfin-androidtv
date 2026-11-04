package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.homerows.EditorRow
import org.jellyfin.androidtv.data.homerows.HomeRowField
import org.jellyfin.androidtv.data.homerows.HomeRowFieldError
import org.jellyfin.androidtv.data.homerows.HomeRowFieldKind
import org.jellyfin.androidtv.data.homerows.HomeRowsApi
import org.jellyfin.androidtv.data.homerows.HomeRowsEditorState
import org.jellyfin.androidtv.data.homerows.fields
import org.jellyfin.androidtv.data.homerows.validateRowConfig
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

/*
 * Shared pieces of the Settings → Home rows editor (personal fork, Custom Home Rows).
 */

/** Setting key the text screen uses for a row's custom title. */
const val TITLE_SETTING = "title"

@Composable
fun rememberHomeRowsEditor() = koinActivityViewModel<HomeRowsEditorViewModel>()

/** Whether the server has the HomeRows API: null while asking. */
@Composable
fun rememberHomeRowsSupported(): Boolean? {
	val api = koinInject<HomeRowsApi>()
	return produceState<Boolean?>(null, api) { value = api.isSupported() }.value
}

fun HomeRowsEditorState.row(key: String?): EditorRow? = rows.firstOrNull { it.key == key }

fun HomeRowsEditorState.field(key: String?, setting: String?): HomeRowField? =
	row(key)?.let { provider(it.row.provider) }?.fields()?.firstOrNull { it.key == setting }

/** Local validation of a row against its provider's schema; empty for unavailable rows. */
fun HomeRowsEditorState.validation(row: EditorRow): Map<String, HomeRowFieldError> =
	provider(row.row.provider)?.let { validateRowConfig(it.fields(), row.row.config) }.orEmpty()

@Composable
fun HomeRowFieldError.text() = stringResource(
	when (this) {
		HomeRowFieldError.REQUIRED -> R.string.home_rows_error_required
		HomeRowFieldError.OUT_OF_RANGE -> R.string.home_rows_error_out_of_range
		HomeRowFieldError.TOO_LONG -> R.string.home_rows_error_too_long
		HomeRowFieldError.UNKNOWN_OPTION -> R.string.home_rows_error_unknown_option
		HomeRowFieldError.PICK_AT_LEAST_ONE -> R.string.home_rows_error_pick_at_least_one
	}
)

private val JsonElement.stringOrNull get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A short text for a setting's current value, shown as the setting's caption. */
@Composable
fun HomeRowField.valueText(value: JsonElement?): String {
	val none = stringResource(R.string.home_rows_value_none)
	return when (kind) {
		HomeRowFieldKind.BOOLEAN -> stringResource(
			if ((value as? JsonPrimitive)?.booleanOrNull == true) R.string.home_rows_value_on else R.string.home_rows_value_off
		)

		HomeRowFieldKind.INTEGER -> (value as? JsonPrimitive)?.intOrNull?.toString() ?: none
		HomeRowFieldKind.STRING -> value?.stringOrNull?.takeIf { it.isNotEmpty() } ?: none
		HomeRowFieldKind.ENUM -> value?.stringOrNull?.let { v -> options.firstOrNull { it.value == v }?.label ?: v } ?: none
		HomeRowFieldKind.MULTI_ENUM -> (value as? JsonArray)
			?.mapNotNull { element -> element.stringOrNull?.let { v -> options.firstOrNull { it.value == v }?.label ?: v } }
			?.takeIf { it.isNotEmpty() }
			?.joinToString(", ")
			?: none
	}
}
