package org.jellyfin.androidtv.data.itemactions

import androidx.annotation.DrawableRes
import org.jellyfin.androidtv.R
import java.util.UUID

const val ITEM_ACTION_KIND_PLAY_QUEUE = "play-queue"
const val ITEM_ACTION_KIND_TOGGLE = "toggle"
const val ITEM_ACTION_KIND_REQUEST = "request"
const val ITEM_ACTION_WATCH_TRAILER = "watch-trailer"
const val ITEM_ACTION_ICON_TRAILER = "trailer"
const val ITEM_ACTION_ICON_BOOKMARK = "bookmark"
const val ITEM_ACTION_ICON_DOWNLOAD = "download"
private const val ITEM_ACTION_STATE_ON = "on"
private val shownKinds = setOf(ITEM_ACTION_KIND_PLAY_QUEUE, ITEM_ACTION_KIND_TOGGLE, ITEM_ACTION_KIND_REQUEST)

/** A server action to show as a button on the item page. */
data class ItemActionButton(
	val action: ItemAction,
	@DrawableRes val icon: Int,
)

/** Which server buttons the item page shows, in server order, and whether the stock trailer button stays. */
data class ActionPlan(
	val buttons: List<ItemActionButton>,
	val showStockTrailer: Boolean,
)

/**
 * Plans the item page buttons for the server's [actions]. `play-queue` (Watch trailer), `toggle` (Save) and `request`
 * (Download) actions are shown, other kinds are ignored until the app knows them. A `watch-trailer` action replaces the
 * stock trailer button ([hasLocalTrailers] is whether the page would show it), so there is only one trailer button.
 */
fun actionButtonsFor(actions: List<ItemAction>, hasLocalTrailers: Boolean): ActionPlan {
	val buttons = actions
		.filter { it.kind in shownKinds }
		.map { action -> ItemActionButton(action, actionIcon(action)) }

	val hasServerTrailer = buttons.any { it.action.id == ITEM_ACTION_WATCH_TRAILER }
	return ActionPlan(buttons, showStockTrailer = hasLocalTrailers && !hasServerTrailer)
}

/** The button icon for [action]: a bookmark is filled while its toggle is on. */
@DrawableRes
fun actionIcon(action: ItemAction): Int = when (action.icon) {
	ITEM_ACTION_ICON_TRAILER -> R.drawable.ic_trailer
	ITEM_ACTION_ICON_BOOKMARK -> if (action.state == ITEM_ACTION_STATE_ON) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
	ITEM_ACTION_ICON_DOWNLOAD -> R.drawable.ic_download
	else -> R.drawable.ic_play
}

/** Whether a press on [action] does anything: false only when the server disabled it (a film already requested). */
fun isPressable(action: ItemAction): Boolean = action.enabled != false

/**
 * Picks the item page button to focus after the buttons change: the [focused] button while it is still in [buttons]
 * and can take focus, otherwise the first one that can (Resume or Play for an owned film, Watch trailer for a virtual
 * one). Null when no button can take focus.
 */
fun <T> focusTargetFor(buttons: List<T>, focused: T?, canFocus: (T) -> Boolean): T? =
	focused?.takeIf { it in buttons && canFocus(it) } ?: buttons.firstOrNull(canFocus)

/**
 * Whether the item page sends focus to the rows below its buttons once the server has answered: when none of [buttons]
 * can take focus (a virtual series without a trailer has none). Focus can't enter the rows from the toolbar otherwise,
 * because the list only lets it in through its selected row, which is the button row.
 */
fun <T> focusRowsBelowButtons(buttons: List<T>, canFocus: (T) -> Boolean): Boolean = buttons.none(canFocus)

/**
 * Whether the item page moves focus to its default button. It does when nothing has focus, when the focused view has
 * left the window (stale focus crashes the next D-pad press), when focus is on a button row button that is going away,
 * or when the button row was just attached or re-added. Any other focus in the window is the user's choice.
 */
fun shouldTakeFocus(hasFocus: Boolean, focusAttached: Boolean, focusInRow: Boolean, rowRebuilt: Boolean): Boolean =
	!hasFocus || !focusAttached || focusInRow || rowRebuilt

/**
 * Pairs each of [buttons] with its new action after a press: the action with the same id in [refreshed] (the page's
 * actions fetched again after the POST), or [posted] (the POST's answer) for the action that was pressed. One press can
 * change other actions too (a Download also saves the title, so Save turns on). Buttons whose action isn't answered,
 * or that have no action ([actionOf] null), are left out and keep what they show.
 */
fun <B> actionUpdatesById(
	buttons: List<B>,
	actionOf: (B) -> ItemAction?,
	refreshed: List<ItemAction>,
	posted: ItemAction,
): List<Pair<B, ItemAction>> {
	val byId = refreshed.associateBy { it.id } + (posted.id to posted)
	return buttons.mapNotNull { button ->
		val id = actionOf(button)?.id ?: return@mapNotNull null
		byId[id]?.let { button to it }
	}
}

/** Puts [items] in the order of [ids]; ids without an item (not returned by the server) are skipped. */
fun <T> inQueueOrder(ids: List<UUID>, items: List<T>, id: (T) -> UUID): List<T> {
	val byId = items.associateBy(id)
	return ids.mapNotNull { byId[it] }
}

/** Ignores further presses of a server action while its queue fetch is in flight. */
class ActionPressGate {
	private var inFlight = false

	/** True when this press may start; false while an earlier one is still in flight. */
	fun tryStart(): Boolean {
		if (inFlight) return false
		inFlight = true
		return true
	}

	fun finish() {
		inFlight = false
	}
}
