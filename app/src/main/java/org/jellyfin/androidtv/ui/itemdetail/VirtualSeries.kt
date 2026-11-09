package org.jellyfin.androidtv.ui.itemdetail

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType

/*
 * Personal fork: show discovery puts shows the user doesn't own on Home as virtual series: hidden items with no path, no
 * seasons or episodes and PlayAccess None. The server marks them with LocationType Virtual (iOS's `isMissing`). These
 * rules keep the series page and cards from offering what such a show doesn't have; the page's buttons come from the
 * server's actions (Watch trailer).
 */

/** A series the server reports as virtual: a discovery show that isn't in the library. */
val BaseItemDto.isVirtualSeries: Boolean
	get() = type == BaseItemKind.SERIES && locationType == LocationType.VIRTUAL

/**
 * Whether the series page asks for its episode rows (Next up, Seasons, Upcoming): not for a virtual series, and not when
 * the server says the series has no children. An unknown child count still asks.
 */
val BaseItemDto.hasSeriesChildRows: Boolean
	get() = !isVirtualSeries && childCount != 0

/** Whether the item page shows its season count: not for a virtual series, which has no seasons. */
val BaseItemDto.showsSeasonCount: Boolean
	get() = !isVirtualSeries

/** Whether the item page shows Runs and Ends: not for a virtual series, which has no episodes to time. */
val BaseItemDto.showsRunTime: Boolean
	get() = !isVirtualSeries

/**
 * Whether the item page offers Watched and Favorite, and cards show the watched badge. Not on a virtual series: there is
 * nothing to watch (the server reports a series without episodes as played), and iOS hides them for `isMissing` too.
 */
val BaseItemDto.hasWatchedState: Boolean
	get() = !isVirtualSeries
