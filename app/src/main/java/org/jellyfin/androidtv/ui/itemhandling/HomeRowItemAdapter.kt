package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.homerows.HomeRowsApi
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * Items of one server home row (Custom Home Rows, see ui/home/serverrows/ServerHomeRows.kt). The first page is loaded
 * with the layout; [Retrieve] fetches it again on refresh. Lives in this package to access the protected members of
 * ItemRowAdapter.
 */
class HomeRowItemAdapter(
	context: Context,
	private val rowId: UUID,
	private val preferSeriesPoster: Boolean,
	presenter: Presenter,
	parent: MutableObjectAdapter<Row>,
	private val scope: CoroutineScope = ProcessLifecycleOwner.get().lifecycleScope,
) : ItemRowAdapter(context, QueryType.HomeRow, false, presenter, parent), KoinComponent {
	private val homeRowsApi by inject<HomeRowsApi>()

	/** Shows the page loaded with the layout, and counts it as a full retrieve for the refresh triggers. */
	fun setInitialItems(items: List<BaseItemDto>) {
		// The base Retrieve() stamps lastFullRetrieve and resets itemsLoaded; it does nothing else for QueryType.HomeRow.
		super.Retrieve()
		setItems(items = items, transform = ::toRowItem)
		notifyRetrieveFinished()
	}

	override fun Retrieve() {
		// Stamp lastFullRetrieve and reset itemsLoaded first, so ReRetrieveIfNeeded works and setItems replaces the row.
		super.Retrieve()
		scope.launch {
			runCatching {
				val items = withContext(Dispatchers.IO) { homeRowsApi.getItems(rowId) }
				val shown = if (items.error == null) items.items else emptyList()

				setItems(items = shown, transform = ::toRowItem)
				if (shown.isEmpty()) removeRow()
			}.fold(
				onSuccess = { notifyRetrieveFinished() },
				onFailure = { error -> notifyRetrieveFinished(error as? Exception) }
			)
		}
	}

	private fun toRowItem(item: BaseItemDto, @Suppress("UNUSED_PARAMETER") index: Int) =
		BaseItemDtoBaseRowItem(item, preferParentThumb, isStaticHeight, preferSeriesPoster = preferSeriesPoster)
}
