package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences

class HomeConfigurationTests : FunSpec({
	fun configuration(
		sections: List<HomeSectionType> = listOf(HomeSectionType.RESUME, HomeSectionType.NEXT_UP),
		nextUpMaxDays: Int = 0,
		rewatching: Boolean = false,
		newReleasesMaxDays: Int = 30,
		seriesThumbnails: Boolean = true,
	): HomeConfiguration {
		val userSettingPreferences = mockk<UserSettingPreferences> {
			every { activeHomesections } returns sections
		}
		val userPreferences = mockk<UserPreferences> {
			every { this@mockk[UserPreferences.homeNextUpMaxDays] } returns nextUpMaxDays
			every { this@mockk[UserPreferences.nextUpRewatchingEnabled] } returns rewatching
			every { this@mockk[UserPreferences.homeNewReleasesMaxDays] } returns newReleasesMaxDays
			every { this@mockk[UserPreferences.seriesThumbnailsEnabled] } returns seriesThumbnails
		}
		return getHomeConfiguration(userSettingPreferences, userPreferences)
	}

	test("Unchanged settings are equal") {
		configuration() shouldBe configuration()
	}

	test("Changed sections are detected") {
		configuration() shouldNotBe configuration(sections = listOf(HomeSectionType.CONTINUE_WATCHING_COMBINED))
	}

	test("Reordered sections are detected") {
		configuration() shouldNotBe configuration(sections = listOf(HomeSectionType.NEXT_UP, HomeSectionType.RESUME))
	}

	test("Changed home preferences are detected") {
		configuration() shouldNotBe configuration(nextUpMaxDays = 30)
		configuration() shouldNotBe configuration(rewatching = true)
		configuration() shouldNotBe configuration(newReleasesMaxDays = 7)
		configuration() shouldNotBe configuration(seriesThumbnails = false)
	}
})
