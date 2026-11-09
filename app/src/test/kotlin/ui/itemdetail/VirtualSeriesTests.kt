package org.jellyfin.androidtv.ui.itemdetail

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.PlayAccess
import java.util.UUID

private fun item(
	type: BaseItemKind,
	locationType: LocationType? = LocationType.FILE_SYSTEM,
	childCount: Int? = null,
) = BaseItemDto(
	id = UUID.randomUUID(),
	type = type,
	locationType = locationType,
	childCount = childCount,
	playAccess = if (locationType == LocationType.VIRTUAL) PlayAccess.NONE else PlayAccess.FULL,
)

class VirtualSeriesTests : FunSpec({
	test("A virtual series is a discovery show") {
		item(BaseItemKind.SERIES, LocationType.VIRTUAL, childCount = 0).isVirtualSeries shouldBe true
	}

	test("Owned series, virtual films and missing episodes are not virtual series") {
		item(BaseItemKind.SERIES, LocationType.FILE_SYSTEM, childCount = 3).isVirtualSeries shouldBe false
		item(BaseItemKind.SERIES, locationType = null).isVirtualSeries shouldBe false
		item(BaseItemKind.MOVIE, LocationType.VIRTUAL).isVirtualSeries shouldBe false
		item(BaseItemKind.EPISODE, LocationType.VIRTUAL).isVirtualSeries shouldBe false
	}

	test("A virtual series has no episode rows, season count, run time, Watched or Favorite") {
		val show = item(BaseItemKind.SERIES, LocationType.VIRTUAL, childCount = 0)

		show.hasSeriesChildRows shouldBe false
		show.showsSeasonCount shouldBe false
		show.showsRunTime shouldBe false
		show.hasWatchedState shouldBe false
	}

	test("A virtual series without a child count still has no episode rows") {
		item(BaseItemKind.SERIES, LocationType.VIRTUAL, childCount = null).hasSeriesChildRows shouldBe false
	}

	test("An owned series keeps its page") {
		val show = item(BaseItemKind.SERIES, LocationType.FILE_SYSTEM, childCount = 2)

		show.hasSeriesChildRows shouldBe true
		show.showsSeasonCount shouldBe true
		show.showsRunTime shouldBe true
		show.hasWatchedState shouldBe true
	}

	test("An owned series with no children skips the episode rows, an unknown count asks") {
		item(BaseItemKind.SERIES, LocationType.FILE_SYSTEM, childCount = 0).hasSeriesChildRows shouldBe false
		item(BaseItemKind.SERIES, LocationType.FILE_SYSTEM, childCount = null).hasSeriesChildRows shouldBe true
	}

	test("Virtual films keep Watched, Favorite and their run time") {
		item(BaseItemKind.MOVIE, LocationType.VIRTUAL).hasWatchedState shouldBe true
		item(BaseItemKind.MOVIE, LocationType.VIRTUAL).showsRunTime shouldBe true
	}
})
