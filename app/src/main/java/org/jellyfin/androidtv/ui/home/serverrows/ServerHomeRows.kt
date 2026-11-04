package org.jellyfin.androidtv.ui.home.serverrows

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.data.homerows.HomeRowInstance
import org.jellyfin.androidtv.data.homerows.HomeRowItems
import org.jellyfin.androidtv.data.homerows.HomeRowPresentation
import org.jellyfin.androidtv.data.homerows.HomeRowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * Home screen rows from the personal fork server's HomeRows API (Custom Home Rows). Rows are generic: the server
 * decides what is in them and how they look. See docs/home-rows.md.
 */

enum class ServerRowStyle { THUMB, POSTER, SQUARE, LIBRARY_BUTTONS, LIBRARY_TILES }

private val libraryKinds = setOf(BaseItemKind.COLLECTION_FOLDER, BaseItemKind.USER_VIEW)

/** How to draw a row, from its presentation hint and items. Libraries get the library rows, as on the web. */
fun serverRowStyle(presentation: HomeRowPresentation, items: List<BaseItemDto>): ServerRowStyle = when {
	presentation.cardShape == "Button" -> ServerRowStyle.LIBRARY_BUTTONS
	items.isNotEmpty() && items.all { it.type in libraryKinds } -> ServerRowStyle.LIBRARY_TILES
	presentation.cardShape == "Poster" -> ServerRowStyle.POSTER
	presentation.cardShape == "Square" -> ServerRowStyle.SQUARE
	else -> ServerRowStyle.THUMB
}

/** Hidden rows take no space: disabled, unavailable, failed, needing configuration, or empty. */
fun isRowShown(row: HomeRowInstance, items: HomeRowItems?): Boolean =
	row.enabled && row.isAvailable && items != null && items.error == null && items.items.isNotEmpty()

fun serverRowTitle(row: HomeRowInstance, items: HomeRowItems): String =
	row.title?.trim()?.takeIf { it.isNotEmpty() } ?: items.title?.takeIf { it.isNotBlank() } ?: row.provider

data class LoadedServerRow(
	val row: HomeRowInstance,
	val items: HomeRowItems,
	val title: String,
	val style: ServerRowStyle,
)

/**
 * Loads the server layout and every shown row's first page, in parallel. Returns null on a stock server, or when the
 * layout can't be loaded, so the caller falls back to the stock sections. A row that fails or takes longer than
 * [timeout] is left out.
 */
suspend fun buildServerRows(api: HomeRowsApi, timeout: Duration = 10.seconds): List<LoadedServerRow>? {
	if (!api.isSupported()) return null

	val configuration = runCatching { api.getConfiguration() }
		.onFailure { Timber.w(it, "Could not load the home rows layout") }
		.getOrNull() ?: return null

	return coroutineScope {
		configuration.rows
			.filter { it.enabled && it.isAvailable }
			.map { row ->
				async {
					val items = withTimeoutOrNull(timeout) {
						runCatching { api.getItems(row.id) }
							.onFailure { Timber.w(it, "Could not load home row ${row.id}") }
							.getOrNull()
					}
					if (items == null || !isRowShown(row, items)) null
					else LoadedServerRow(row, items, serverRowTitle(row, items), serverRowStyle(items.presentation, items.items))
				}
			}
			.awaitAll()
			.filterNotNull()
	}
}

/** The row published to the TV launcher: the first shown row that isn't a library row (personal fork). */
fun launcherRow(rows: List<LoadedServerRow>): LoadedServerRow? = rows.firstOrNull {
	it.style != ServerRowStyle.LIBRARY_BUTTONS && it.style != ServerRowStyle.LIBRARY_TILES
}
