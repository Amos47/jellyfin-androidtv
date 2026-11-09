package org.jellyfin.androidtv.data.itemactions

import androidx.annotation.DrawableRes
import org.jellyfin.androidtv.R
import java.util.UUID

const val ITEM_ACTION_KIND_PLAY_QUEUE = "play-queue"
const val ITEM_ACTION_WATCH_TRAILER = "watch-trailer"
const val ITEM_ACTION_ICON_TRAILER = "trailer"

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
 * Plans the item page buttons for the server's [actions]. Only `play-queue` actions are shown, other kinds are ignored
 * until the app knows them. A `watch-trailer` action replaces the stock trailer button ([hasLocalTrailers] is whether
 * the page would show it), so there is only one trailer button.
 */
fun actionButtonsFor(actions: List<ItemAction>, hasLocalTrailers: Boolean): ActionPlan {
	val buttons = actions
		.filter { it.kind == ITEM_ACTION_KIND_PLAY_QUEUE }
		.map { action ->
			val icon = when (action.icon) {
				ITEM_ACTION_ICON_TRAILER -> R.drawable.ic_trailer
				else -> R.drawable.ic_play
			}
			ItemActionButton(action, icon)
		}

	val hasServerTrailer = buttons.any { it.action.id == ITEM_ACTION_WATCH_TRAILER }
	return ActionPlan(buttons, showStockTrailer = hasLocalTrailers && !hasServerTrailer)
}

/**
 * Picks the item page button to focus after the buttons change: the [focused] button while it is still in [buttons]
 * and can take focus, otherwise the first one that can (Resume or Play for an owned film, Watch trailer for a virtual
 * one). Null when no button can take focus.
 */
fun <T> focusTargetFor(buttons: List<T>, focused: T?, canFocus: (T) -> Boolean): T? =
	focused?.takeIf { it in buttons && canFocus(it) } ?: buttons.firstOrNull(canFocus)

/**
 * Whether the item page moves focus to its default button. It does when nothing has focus, when the focused view has
 * left the window (stale focus crashes the next D-pad press), when focus is on a button row button that is going away,
 * or when the button row was just attached or re-added. Any other focus in the window is the user's choice.
 */
fun shouldTakeFocus(hasFocus: Boolean, focusAttached: Boolean, focusInRow: Boolean, rowRebuilt: Boolean): Boolean =
	!hasFocus || !focusAttached || focusInRow || rowRebuilt

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
