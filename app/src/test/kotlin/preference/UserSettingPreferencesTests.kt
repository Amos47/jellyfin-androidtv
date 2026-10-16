package org.jellyfin.androidtv.preference

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.preference.stringPreference

// The raw value stored in the server display preferences
private val serverHomesection3 = stringPreference("homesection3", "")

class UserSettingPreferencesTests : FunSpec({
	fun createPreferences(): Pair<UserSettingPreferences, () -> String> {
		var localValue = ""
		val userPreferences = mockk<UserPreferences> {
			every { this@mockk[UserPreferences.localHomeSections] } answers { localValue }
			val value = slot<String>()
			every { this@mockk[UserPreferences.localHomeSections] = capture(value) } answers { localValue = value.captured }
		}

		return UserSettingPreferences(mockk(relaxed = true), userPreferences) to { localValue }
	}

	test("Local section types are kept on the device and stored as none on the server") {
		val (preferences, localValue) = createPreferences()

		preferences[UserSettingPreferences.homesection3] = HomeSectionType.CONTINUE_WATCHING_COMBINED

		preferences[UserSettingPreferences.homesection3] shouldBe HomeSectionType.CONTINUE_WATCHING_COMBINED
		preferences[serverHomesection3] shouldBe HomeSectionType.NONE.serializedName
		localValue() shouldBe "3=custom_continuewatching"
	}

	test("Server section types clear the local section") {
		val (preferences, localValue) = createPreferences()

		preferences[UserSettingPreferences.homesection3] = HomeSectionType.NEW_RELEASES
		preferences[UserSettingPreferences.homesection3] = HomeSectionType.NEXT_UP

		preferences[UserSettingPreferences.homesection3] shouldBe HomeSectionType.NEXT_UP
		preferences[serverHomesection3] shouldBe HomeSectionType.NEXT_UP.serializedName
		localValue() shouldBe ""
	}

	test("Active sections include local sections") {
		val (preferences, _) = createPreferences()

		preferences[UserSettingPreferences.homesection1] = HomeSectionType.CONTINUE_WATCHING_COMBINED

		preferences.activeHomesections[1] shouldBe HomeSectionType.CONTINUE_WATCHING_COMBINED
	}
})
