package org.jellyfin.androidtv.preference

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.preference.Preference
import org.jellyfin.sdk.model.api.UserDto
import java.util.UUID

class UserSettingPreferencesTests : FunSpec({
	class Setup {
		val localValues = mutableMapOf<String, String>()
		val currentUser = MutableStateFlow<UserDto?>(user(UUID.randomUUID()))

		val userPreferences = mockk<UserPreferences> {
			every { this@mockk[any<Preference<String>>()] } answers { localValues[firstArg<Preference<String>>().key] ?: "" }
			every { this@mockk[any<Preference<String>>()] = any<String>() } answers {
				localValues[firstArg<Preference<String>>().key] = secondArg()
			}
		}
		val userRepository = mockk<UserRepository> {
			every { currentUser } returns this@Setup.currentUser
		}
		val preferences = UserSettingPreferences(mockk(relaxed = true), userPreferences, userRepository).apply {
			this[UserSettingPreferences.homesection0] = HomeSectionType.RESUME
			this[UserSettingPreferences.homesection1] = HomeSectionType.NEXT_UP
			this[UserSettingPreferences.homesection2] = HomeSectionType.LATEST_MEDIA
			for (section in homesections.drop(3)) this[section] = HomeSectionType.NONE
		}
	}

	test("Override replaces a section on this device without changing the server layout") {
		val setup = Setup()

		setup.preferences.setHomeSectionOverride(0, HomeSectionType.CONTINUE_WATCHING_COMBINED)

		setup.preferences[UserSettingPreferences.homesection0] shouldBe HomeSectionType.RESUME
		setup.preferences.activeHomesections shouldBe listOf(
			HomeSectionType.CONTINUE_WATCHING_COMBINED,
			HomeSectionType.NEXT_UP,
			HomeSectionType.LATEST_MEDIA,
		)
	}

	test("Hidden override removes the section on this device") {
		val setup = Setup()

		setup.preferences.setHomeSectionOverride(1, HomeSectionType.NONE)

		setup.preferences.activeHomesections shouldBe listOf(HomeSectionType.RESUME, HomeSectionType.LATEST_MEDIA)
	}

	test("Removing an override restores the server section") {
		val setup = Setup()

		setup.preferences.setHomeSectionOverride(1, HomeSectionType.NONE)
		setup.preferences.setHomeSectionOverride(1, null)

		setup.preferences.homeSectionOverrides shouldBe emptyMap()
		setup.preferences.activeHomesections[1] shouldBe HomeSectionType.NEXT_UP
	}

	test("Overrides are per user") {
		val setup = Setup()
		val userA = setup.currentUser.value
		setup.preferences.setHomeSectionOverride(0, HomeSectionType.NEW_RELEASES)

		setup.currentUser.value = user(UUID.randomUUID())
		setup.preferences.homeSectionOverrides shouldBe emptyMap()
		setup.preferences.activeHomesections[0] shouldBe HomeSectionType.RESUME

		setup.currentUser.value = userA
		setup.preferences.homeSectionOverrides shouldBe mapOf(0 to HomeSectionType.NEW_RELEASES)
	}

	test("Without a signed in user there are no overrides") {
		val setup = Setup()
		setup.currentUser.value = null

		setup.preferences.setHomeSectionOverride(0, HomeSectionType.NEW_RELEASES)

		setup.preferences.homeSectionOverrides shouldBe emptyMap()
		setup.localValues shouldBe emptyMap()
	}
})

private fun user(id: UUID) = mockk<UserDto> {
	every { this@mockk.id } returns id
}
