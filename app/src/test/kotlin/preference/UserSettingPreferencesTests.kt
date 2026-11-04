package org.jellyfin.androidtv.preference

import android.content.Context
import android.content.SharedPreferences
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.sdk.model.api.UserDto
import java.util.UUID

class UserSettingPreferencesTests : FunSpec({
	class Setup {
		private val sharedPreferences = InMemorySharedPreferences()
		val currentUser = MutableStateFlow<UserDto?>(user(UUID.randomUUID()))

		/** The per-user override values stored on this device. */
		val localValues get() = sharedPreferences.values.filterKeys { it.startsWith("home_section_overrides") }

		// A real UserPreferences over in-memory SharedPreferences. Mocking its final generic get/set was flaky: mockk
		// sometimes ran the real method while recording the stub.
		val userPreferences = UserPreferences(mockk<Context>(relaxed = true) {
			every { packageName } returns "test"
			every { getSharedPreferences(any(), any()) } returns sharedPreferences
		})
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

/** Just enough of SharedPreferences for UserPreferences: values are applied immediately. */
private class InMemorySharedPreferences : SharedPreferences {
	val values = mutableMapOf<String, Any?>()

	override fun getAll(): Map<String, *> = values.toMap()
	override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
	override fun getStringSet(key: String, defValues: Set<String>?) = @Suppress("UNCHECKED_CAST") (values[key] as? Set<String>) ?: defValues
	override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
	override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
	override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
	override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
	override fun contains(key: String) = key in values
	override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
	override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

	override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
		override fun putString(key: String, value: String?) = apply { values[key] = value }
		override fun putStringSet(key: String, values: Set<String>?) = apply { this@InMemorySharedPreferences.values[key] = values }
		override fun putInt(key: String, value: Int) = apply { values[key] = value }
		override fun putLong(key: String, value: Long) = apply { values[key] = value }
		override fun putFloat(key: String, value: Float) = apply { values[key] = value }
		override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
		override fun remove(key: String) = apply { values.remove(key) }
		override fun clear() = apply { values.clear() }
		override fun commit() = true
		override fun apply() = Unit
	}
}
