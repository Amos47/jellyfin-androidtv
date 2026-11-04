package org.jellyfin.androidtv.ui.settings.screen.home.rows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import org.jellyfin.androidtv.data.homerows.HomeRowsApi
import org.jellyfin.androidtv.data.homerows.HomeRowsEditor
import org.jellyfin.androidtv.data.homerows.HomeRowsEditorState
import org.jellyfin.androidtv.data.homerows.LeaveChoice
import timber.log.Timber

/** Where the user was going when they were asked about unsaved edits. */
enum class LeaveTarget { BACK, CLOSE_SETTINGS }

/**
 * Activity-scoped holder of the rows editor, shared by the Settings → Home screens and the settings overlay, which
 * asks before closing with pending edits.
 */
class HomeRowsEditorViewModel(private val api: HomeRowsApi) : ViewModel() {
	private var editor = HomeRowsEditor(api)
	private val _state = MutableStateFlow(editor.state.value)
	val state: StateFlow<HomeRowsEditorState> = _state.asStateFlow()

	private val _loadFailed = MutableStateFlow(false)
	val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

	private val _busy = MutableStateFlow(false)
	val busy: StateFlow<Boolean> = _busy.asStateFlow()

	private val _leavePrompt = MutableStateFlow<LeaveTarget?>(null)
	val leavePrompt: StateFlow<LeaveTarget?> = _leavePrompt.asStateFlow()

	private fun sync() {
		_state.value = editor.state.value
	}

	private fun edit(action: HomeRowsEditor.() -> Unit) {
		editor.action()
		sync()
	}

	/** Loads the layout unless it's already loaded (the row screens share the list's copy). */
	fun ensureLoaded() {
		if (_state.value.loaded || _busy.value) return
		run({ runCatching { load() }.onFailure { Timber.w(it, "Could not load home rows") }.isSuccess }) { _loadFailed.value = !it }
	}

	fun moveUp(key: String) = edit { moveUp(key) }
	fun moveDown(key: String) = edit { moveDown(key) }
	fun remove(key: String) = edit { remove(key) }
	fun add(providerId: String): String? = editor.add(providerId).also { sync() }
	fun setEnabled(key: String, enabled: Boolean) = edit { setEnabled(key, enabled) }
	fun setTitle(key: String, title: String?) = edit { setTitle(key, title) }
	fun setValue(key: String, settingKey: String, value: JsonElement?) = edit { setValue(key, settingKey, value) }
	fun discard() = edit { discard() }

	fun save(onDone: (Boolean) -> Unit = {}) = run({ save() }, onDone)
	fun reset(onDone: (Boolean) -> Unit = {}) = run({ reset() }, onDone)
	fun saveAsDefault(onDone: (Boolean) -> Unit = {}) = run({ saveAsDefault() }, onDone)
	fun removeDefault(onDone: (Boolean) -> Unit = {}) = run({ removeDefault() }, onDone)

	private fun run(action: suspend HomeRowsEditor.() -> Boolean, onDone: (Boolean) -> Unit) {
		if (_busy.value) return
		_busy.value = true
		viewModelScope.launch {
			val result = editor.action()
			sync()
			_busy.value = false
			onDone(result)
		}
	}

	/**
	 * Applies a prompt choice once: further presses are ignored while it runs, and [leave] gets the target only if the
	 * prompt is still open when it finishes.
	 */
	fun chooseLeave(choice: LeaveChoice, leave: (LeaveTarget) -> Unit) {
		val target = _leavePrompt.value ?: return
		run({ resolveLeave(choice) }) { mayLeave ->
			val stillAsked = _leavePrompt.value == target
			_leavePrompt.value = null
			if (mayLeave && stillAsked) leave(target)
		}
	}

	fun requestLeave(target: LeaveTarget) {
		_leavePrompt.value = target
	}

	fun cancelLeave() {
		_leavePrompt.value = null
	}

	/** Drops the working copy, so the next visit loads fresh. Called when the settings close. */
	fun clear() {
		editor = HomeRowsEditor(api)
		_leavePrompt.value = null
		_loadFailed.value = false
		sync()
	}
}
