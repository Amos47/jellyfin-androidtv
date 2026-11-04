package org.jellyfin.androidtv.data.homerows

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/*
 * The Settings → Home rows editor's working copy (Custom Home Rows). Edits stay pending in memory until save().
 */

const val MAX_HOME_ROWS = 20
const val MAX_HOME_ROW_TITLE = 100

/** Errors that name no row are kept under this key. */
const val GENERAL_ERRORS_KEY = "*"

/** A row in the editor. [key] is client-only and stable while editing; new rows all share [EMPTY_ROW_ID]. */
data class EditorRow(val key: String, val row: HomeRowInstance)

data class HomeRowsEditorState(
	val loaded: Boolean = false,
	val rows: List<EditorRow> = emptyList(),
	val providers: List<HomeRowProvider> = emptyList(),
	val savedRows: List<HomeRowInstance> = emptyList(),
	val source: String = "",
	/** Row key → setting key → message, from the server's validation errors. */
	val errors: Map<String, Map<String, String>> = emptyMap(),
	val generalError: String? = null,
) {
	val hasPendingEdits: Boolean get() = rows.map { it.row } != savedRows

	fun provider(id: String) = providers.firstOrNull { it.id == id }
}

private val INDEXED_KEY = Regex("""^rows\[(\d+)]\.(.+)$""", RegexOption.IGNORE_CASE)

/** Maps a 400's errors to rows: "rows[2].Limit" → the third row's key → { limit: message }. */
fun mapSaveErrors(rows: List<EditorRow>, errors: Map<String, List<String>>): Map<String, Map<String, String>> {
	val mapped = mutableMapOf<String, MutableMap<String, String>>()
	for ((name, messages) in errors) {
		val message = messages.firstOrNull().orEmpty()
		val match = INDEXED_KEY.find(name)
		val row = match?.let { rows.getOrNull(it.groupValues[1].toInt()) }
		if (match != null && row != null) {
			val setting = match.groupValues[2].replaceFirstChar { it.lowercaseChar() }
			mapped.getOrPut(row.key) { mutableMapOf() }[setting] = message
		} else {
			mapped.getOrPut(GENERAL_ERRORS_KEY) { mutableMapOf() }[name] = message
		}
	}
	return mapped
}

class HomeRowsEditor(private val api: HomeRowsApi) {
	private val _state = MutableStateFlow(HomeRowsEditorState())
	val state: StateFlow<HomeRowsEditorState> = _state.asStateFlow()

	private var nextKey = 0
	private fun newKey() = "r${++nextKey}"
	private fun toEditorRows(rows: List<HomeRowInstance>) = rows.map { EditorRow(newKey(), it) }

	suspend fun load() = coroutineScope {
		val configuration = async { api.getConfiguration() }
		val providers = async { api.getProviders() }
		applyLoaded(configuration.await(), providers.await())
	}

	private fun applyLoaded(configuration: HomeRowConfiguration, providers: List<HomeRowProvider> = _state.value.providers) {
		_state.value = HomeRowsEditorState(
			loaded = true,
			rows = toEditorRows(configuration.rows),
			providers = providers,
			savedRows = configuration.rows,
			source = configuration.source,
		)
	}

	private fun updateRows(transform: (List<EditorRow>) -> List<EditorRow>) =
		_state.update { it.copy(rows = transform(it.rows)) }

	private fun updateRow(key: String, transform: (HomeRowInstance) -> HomeRowInstance) = updateRows { rows ->
		rows.map { if (it.key == key) it.copy(row = transform(it.row)) else it }
	}

	private fun move(key: String, delta: Int) = updateRows { rows ->
		val from = rows.indexOfFirst { it.key == key }
		val to = from + delta
		if (from < 0 || to !in rows.indices) rows
		else rows.toMutableList().apply { add(to, removeAt(from)) }
	}

	fun moveUp(key: String) = move(key, -1)
	fun moveDown(key: String) = move(key, 1)

	fun remove(key: String) = updateRows { rows -> rows.filterNot { it.key == key } }

	/** Appends a new row of the provider with its defaults. Returns its key, or null when the layout is full. */
	fun add(providerId: String): String? {
		val current = _state.value
		val provider = current.provider(providerId) ?: return null
		if (current.rows.size >= MAX_HOME_ROWS) return null

		val key = newKey()
		// JsonObject is immutable, so sharing the defaults is a deep copy in effect.
		val row = HomeRowInstance(EMPTY_ROW_ID, provider.id, null, JsonObject(provider.defaults.toMap()), enabled = true)
		updateRows { it + EditorRow(key, row) }
		return key
	}

	fun setEnabled(key: String, enabled: Boolean) = updateRow(key) { it.copy(enabled = enabled) }

	fun setTitle(key: String, title: String?) = updateRow(key) {
		it.copy(title = title?.trim()?.take(MAX_HOME_ROW_TITLE)?.takeIf { t -> t.isNotEmpty() })
	}

	/** Sets one setting; null removes it so the provider default applies. */
	fun setValue(key: String, settingKey: String, value: JsonElement?) = updateRow(key) { row ->
		row.copy(config = JsonObject(if (value == null) row.config - settingKey else row.config + (settingKey to value)))
	}

	/** Sends the rows. Returns true when saved; otherwise the edits stay, with the errors set. */
	suspend fun save(): Boolean {
		val current = _state.value
		val result = runCatching { api.saveConfiguration(current.rows.map { it.row }) }.getOrElse { error ->
			Timber.w(error, "Could not save the home rows")
			_state.update { it.copy(errors = emptyMap(), generalError = error.message ?: error.toString()) }
			return false
		}

		return when (result) {
			HomeRowsSaveResult.Saved -> reload()
			is HomeRowsSaveResult.Invalid -> {
				_state.update { it.copy(errors = mapSaveErrors(it.rows, result.errors), generalError = null) }
				false
			}
		}
	}

	fun discard() = _state.update { it.copy(rows = toEditorRows(it.savedRows), errors = emptyMap(), generalError = null) }

	/** Removes the user's own layout and shows the inherited one. Pending edits are dropped. */
	suspend fun reset(): Boolean = act { api.resetConfiguration() } && reload()

	/** Saves the current rows, saved or not, as the household default. The user's own layout is unchanged. */
	suspend fun saveAsDefault(): Boolean {
		val result = runCatching { api.saveDefault(_state.value.rows.map { it.row }) }.getOrElse { error ->
			_state.update { it.copy(generalError = error.message ?: error.toString()) }
			return false
		}
		return when (result) {
			HomeRowsSaveResult.Saved -> true.also { _state.update { it.copy(errors = emptyMap(), generalError = null) } }
			is HomeRowsSaveResult.Invalid -> false.also {
				_state.update { it.copy(errors = mapSaveErrors(it.rows, result.errors), generalError = null) }
			}
		}
	}

	suspend fun removeDefault(): Boolean = act { api.removeDefault() }

	private suspend fun act(action: suspend () -> Unit): Boolean = runCatching { action() }.fold(
		onSuccess = { true },
		onFailure = { error ->
			Timber.w(error, "Home rows action failed")
			_state.update { it.copy(generalError = error.message ?: error.toString()) }
			false
		}
	)

	private suspend fun reload(): Boolean = runCatching { api.getConfiguration() }.fold(
		onSuccess = { applyLoaded(it); true },
		onFailure = { error ->
			_state.update { it.copy(generalError = error.message ?: error.toString()) }
			false
		}
	)
}
