package org.jellyfin.androidtv.ui.settings

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey

class HomeRowRoutesTests : FunSpec({
	test("all home row routes resolve") {
		for (route in listOf(Routes.HOME_ROW, Routes.HOME_ROW_ADD, Routes.HOME_ROW_OPTION, Routes.HOME_ROW_TEXT, Routes.HOME_ROW_NUMBER)) {
			routes shouldContainKey route
		}
	}
})
