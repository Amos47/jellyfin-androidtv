package org.jellyfin.androidtv.ui.home.serverrows

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.querying.GetUserViewsRequest
import org.jellyfin.androidtv.ui.home.HomeFragmentRow
import org.jellyfin.androidtv.ui.itemhandling.HomeRowItemAdapter
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

/** One server home row on the home screen, drawn with the TV's own cards. */
class HomeFragmentServerRow(
	private val loaded: LoadedServerRow,
) : HomeFragmentRow {
	private companion object {
		val thumbPresenter = CardPresenter(true, ImageType.THUMB, 150)
		val posterPresenter = CardPresenter(true, ImageType.POSTER, 150)
		val squarePresenter = CardPresenter(true, ImageType.POSTER, 150, true)
		val libraryButtonsPresenter = CardPresenter(true, 75)
		val libraryTilesPresenter = CardPresenter(true, 126)

		val changeTriggers = arrayOf(ChangeTriggerType.LibraryUpdated, ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback)
	}

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val header = HeaderItem(loaded.title)

		val rowAdapter = when (loaded.style) {
			ServerRowStyle.LIBRARY_BUTTONS -> ItemRowAdapter(context, GetUserViewsRequest, libraryButtonsPresenter, rowsAdapter)
			ServerRowStyle.LIBRARY_TILES -> ItemRowAdapter(context, GetUserViewsRequest, libraryTilesPresenter, rowsAdapter)
			ServerRowStyle.THUMB, ServerRowStyle.POSTER, ServerRowStyle.SQUARE -> {
				val presenter = when (loaded.style) {
					ServerRowStyle.POSTER -> posterPresenter
					ServerRowStyle.SQUARE -> squarePresenter
					else -> thumbPresenter
				}
				HomeRowItemAdapter(context, loaded.row.id, loaded.style == ServerRowStyle.POSTER, presenter, rowsAdapter).apply {
					setReRetrieveTriggers(changeTriggers)
				}
			}
		}

		val row = ListRow(header, rowAdapter)
		rowAdapter.setRow(row)
		if (rowAdapter is HomeRowItemAdapter) rowAdapter.setInitialItems(loaded.items.items)
		else rowAdapter.Retrieve()
		rowsAdapter.add(row)
	}
}
