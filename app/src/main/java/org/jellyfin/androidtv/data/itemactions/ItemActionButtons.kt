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

/** Puts [items] in the order of [ids]; ids without an item (not returned by the server) are skipped. */
fun <T> inQueueOrder(ids: List<UUID>, items: List<T>, id: (T) -> UUID): List<T> {
	val byId = items.associateBy(id)
	return ids.mapNotNull { byId[it] }
}
