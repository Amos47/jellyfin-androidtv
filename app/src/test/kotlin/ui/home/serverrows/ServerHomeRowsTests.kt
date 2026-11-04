package org.jellyfin.androidtv.ui.home.serverrows

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.data.homerows.FakeHomeRowsTransport
import org.jellyfin.androidtv.data.homerows.HomeRowInstance
import org.jellyfin.androidtv.data.homerows.HomeRowItems
import org.jellyfin.androidtv.data.homerows.HomeRowPresentation
import org.jellyfin.androidtv.data.homerows.HomeRowsApi
import org.jellyfin.androidtv.data.homerows.HomeRowsHttpResult
import org.jellyfin.androidtv.data.homerows.HomeRowsTransport
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

private fun item(type: BaseItemKind) = BaseItemDto(id = UUID.randomUUID(), type = type)
private fun presentation(shape: String) = HomeRowPresentation(shape, "Primary")
private fun row(enabled: Boolean = true, available: Boolean = true, title: String? = null, provider: String = "genre") =
	HomeRowInstance(UUID.randomUUID(), provider, title, enabled = enabled, isAvailable = available)

private fun items(
	rowId: UUID = UUID.randomUUID(),
	list: List<BaseItemDto> = listOf(item(BaseItemKind.EPISODE)),
	error: String? = null,
	title: String? = null,
) = HomeRowItems(rowId, title, list, presentation = presentation("Thumb"), error = error)

private fun itemsJson(rowId: String) =
	"""{"RowId":"$rowId","Title":"Row $rowId","Items":[{"Id":"${UUID.randomUUID()}","Type":"Episode"}],
	"Presentation":{"CardShape":"Thumb","PreferredImageType":"Thumb"}}"""

private fun configurationJson(vararg rows: Pair<UUID, Boolean>) =
	"""{"ApiVersion":1,"Source":"User","IsCustomized":true,"Rows":[${
		rows.joinToString(",") { (id, enabled) -> """{"Id":"$id","Provider":"genre","Config":{},"Enabled":$enabled}""" }
	}]}"""

class ServerHomeRowsTests : FunSpec({
	beforeTest { HomeRowsApi.clearSupportCache() }

	test("Button shape is library buttons") {
		serverRowStyle(presentation("Button"), listOf(item(BaseItemKind.COLLECTION_FOLDER))) shouldBe ServerRowStyle.LIBRARY_BUTTONS
	}

	test("all CollectionFolder items are library tiles") {
		val libraries = listOf(item(BaseItemKind.COLLECTION_FOLDER), item(BaseItemKind.USER_VIEW))

		serverRowStyle(presentation("Thumb"), libraries) shouldBe ServerRowStyle.LIBRARY_TILES
	}

	test("empty items with Thumb shape stay THUMB") {
		serverRowStyle(presentation("Thumb"), emptyList()) shouldBe ServerRowStyle.THUMB
	}

	test("Poster and Square map") {
		val episodes = listOf(item(BaseItemKind.SERIES))

		serverRowStyle(presentation("Poster"), episodes) shouldBe ServerRowStyle.POSTER
		serverRowStyle(presentation("Square"), episodes) shouldBe ServerRowStyle.SQUARE
	}

	test("unknown shape falls back to THUMB") {
		serverRowStyle(presentation("Hexagon"), listOf(item(BaseItemKind.MOVIE))) shouldBe ServerRowStyle.THUMB
	}

	test("isRowShown hides disabled, unavailable, failed, needs-configuration and empty rows") {
		isRowShown(row(), items()) shouldBe true
		isRowShown(row(enabled = false), items()) shouldBe false
		isRowShown(row(available = false), items()) shouldBe false
		isRowShown(row(), null) shouldBe false
		isRowShown(row(), items(error = "needs-configuration")) shouldBe false
		isRowShown(row(), items(list = emptyList())) shouldBe false
	}

	test("serverRowTitle prefers the custom title, then items title, then provider id") {
		serverRowTitle(row(title = " Comfort "), items(title = "Genre: Comedy")) shouldBe "Comfort"
		serverRowTitle(row(title = "  "), items(title = "Genre: Comedy")) shouldBe "Genre: Comedy"
		serverRowTitle(row(title = null, provider = "genre"), items(title = null)) shouldBe "genre"
	}

	test("buildServerRows returns null on a stock server") {
		val transport = FakeHomeRowsTransport { _, _ -> HomeRowsHttpResult(404, "") }

		buildServerRows(HomeRowsApi(transport) { "s" }) shouldBe null
	}

	test("buildServerRows keeps order and drops failed rows") {
		val ids = List(3) { UUID.randomUUID() }
		val transport = FakeHomeRowsTransport { _, path ->
			when (path) {
				"HomeRows/Configuration" -> HomeRowsHttpResult(200, configurationJson(*ids.map { it to true }.toTypedArray()))
				"HomeRows/${ids[1]}/Items" -> HomeRowsHttpResult(500, "")
				else -> HomeRowsHttpResult(200, itemsJson(path.split('/')[1]))
			}
		}

		val rows = buildServerRows(HomeRowsApi(transport) { "s" })!!

		rows.map { it.row.id } shouldBe listOf(ids[0], ids[2])
		rows[0].title shouldBe "Row ${ids[0]}"
	}

	test("buildServerRows drops rows that time out") {
		val ids = List(2) { UUID.randomUUID() }
		val transport = object : HomeRowsTransport {
			override suspend fun send(method: String, path: String, query: Map<String, String>, body: String?): HomeRowsHttpResult {
				if (path == "HomeRows/Configuration") return HomeRowsHttpResult(200, configurationJson(*ids.map { it to true }.toTypedArray()))
				if (path == "HomeRows/${ids[0]}/Items") delay(1000)
				return HomeRowsHttpResult(200, itemsJson(path.split('/')[1]))
			}
		}

		val rows = buildServerRows(HomeRowsApi(transport) { "s" }, timeout = 100.milliseconds)!!

		rows.map { it.row.id } shouldBe listOf(ids[1])
	}

	test("buildServerRows skips disabled rows without requesting items") {
		val enabled = UUID.randomUUID()
		val disabled = UUID.randomUUID()
		val transport = FakeHomeRowsTransport { _, path ->
			if (path == "HomeRows/Configuration") HomeRowsHttpResult(200, configurationJson(enabled to true, disabled to false))
			else HomeRowsHttpResult(200, itemsJson(path.split('/')[1]))
		}

		val rows = buildServerRows(HomeRowsApi(transport) { "s" })!!

		rows.map { it.row.id } shouldBe listOf(enabled)
		transport.calls.none { it.path == "HomeRows/$disabled/Items" } shouldBe true
	}

	context("launcherRow") {
		fun loaded(style: ServerRowStyle, title: String) =
			LoadedServerRow(row(), items(title = title), title, style)

		test("is the first row that isn't a library row") {
			val rows = listOf(
				loaded(ServerRowStyle.LIBRARY_BUTTONS, "My Media"),
				loaded(ServerRowStyle.LIBRARY_TILES, "Libraries"),
				loaded(ServerRowStyle.THUMB, "Continue Watching"),
				loaded(ServerRowStyle.POSTER, "Comedy"),
			)

			launcherRow(rows)?.title shouldBe "Continue Watching"
		}

		test("is null when there are only library rows") {
			launcherRow(listOf(loaded(ServerRowStyle.LIBRARY_BUTTONS, "My Media"))) shouldBe null
			launcherRow(emptyList()) shouldBe null
		}
	}
})
