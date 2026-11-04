package org.jellyfin.androidtv.ui.itemhandling

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jellyfin.androidtv.data.homerows.FakeHomeRowsTransport
import org.jellyfin.androidtv.data.homerows.HomeRowsApi
import org.jellyfin.androidtv.data.homerows.HomeRowsHttpResult
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID

class HomeRowItemAdapterTests : FunSpec({
	val rowId = UUID.randomUUID()
	val dataRefreshService = DataRefreshService()
	val transport = FakeHomeRowsTransport { _, _ ->
		HomeRowsHttpResult(200, """{"RowId":"$rowId","Items":[],"Presentation":{"CardShape":"Thumb","PreferredImageType":"Thumb"}}""")
	}

	beforeTest {
		startKoin {
			modules(module {
				single { HomeRowsApi(transport) { "s" } }
				single { dataRefreshService }
			})
		}
	}
	afterTest {
		stopKoin()
		transport.calls.clear()
	}

	fun adapter() = HomeRowItemAdapter(
		context = mockk(relaxed = true),
		rowId = rowId,
		preferSeriesPoster = false,
		presenter = mockk(relaxed = true),
		parent = MutableObjectAdapter(),
		scope = CoroutineScope(Dispatchers.Unconfined),
	).apply { setReRetrieveTriggers(arrayOf(org.jellyfin.androidtv.constant.ChangeTriggerType.TvPlayback)) }

	// Leanback adapters can't notify observers on the JVM (android.database.Observable is a stub), so these use an empty
	// row; item replacement is checked on the device.
	test("refreshing after playback does not crash") {
		val adapter = adapter()
		adapter.setInitialItems(emptyList())
		dataRefreshService.lastTvPlayback = Instant.now().plusSeconds(60)

		adapter.ReRetrieveIfNeeded() shouldBe true
		transport.calls.size shouldBe 1
	}

	test("a refresh resets the loaded item count") {
		val adapter = adapter()
		adapter.setInitialItems(emptyList())
		adapter.setItemsLoaded(5)

		adapter.Retrieve()

		adapter.itemsLoaded shouldBe 0
	}
})
