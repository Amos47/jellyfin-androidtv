package org.jellyfin.androidtv.preference

import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.store.DisplayPreferencesStore
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.sdk.api.client.ApiClient

class UserSettingPreferences(
	api: ApiClient,
	private val userPreferences: UserPreferences,
	private val userRepository: UserRepository,
) : DisplayPreferencesStore(
	displayPreferencesId = "usersettings",
	api = api,
	app = "emby",
) {
	companion object {
		val skipBackLength = intPreference("skipBackLength", 10_000)
		val skipForwardLength = intPreference("skipForwardLength", 30_000)

		val homesection0 = enumPreference("homesection0", HomeSectionType.LIBRARY_TILES_SMALL)
		val homesection1 = enumPreference("homesection1", HomeSectionType.RESUME)
		val homesection2 = enumPreference("homesection2", HomeSectionType.RESUME_AUDIO)
		val homesection3 = enumPreference("homesection3", HomeSectionType.RESUME_BOOK)
		val homesection4 = enumPreference("homesection4", HomeSectionType.LIVE_TV)
		val homesection5 = enumPreference("homesection5", HomeSectionType.NEXT_UP)
		val homesection6 = enumPreference("homesection6", HomeSectionType.LATEST_MEDIA)
		val homesection7 = enumPreference("homesection7", HomeSectionType.NONE)
		val homesection8 = enumPreference("homesection8", HomeSectionType.NONE)
		val homesection9 = enumPreference("homesection9", HomeSectionType.NONE)
	}

	val homesections = listOf(
		homesection0,
		homesection1,
		homesection2,
		homesection3,
		homesection4,
		homesection5,
		homesection6,
		homesection7,
		homesection8,
		homesection9,
	)

	/**
	 * Personal fork: home section overrides of the current user on this device, see HomeSectionOverrides.kt.
	 */
	private val homeSectionOverridesPreference
		get() = userRepository.currentUser.value?.id?.let(::homeSectionOverridesPreference)

	val homeSectionOverrides: Map<Int, HomeSectionType>
		get() = homeSectionOverridesPreference?.let { parseHomeSectionOverrides(userPreferences[it]) }.orEmpty()

	/**
	 * Set the override for a home section slot on this device, or remove it with null.
	 */
	fun setHomeSectionOverride(index: Int, type: HomeSectionType?) {
		val preference = homeSectionOverridesPreference ?: return
		val overrides = when (type) {
			null -> homeSectionOverrides - index
			else -> homeSectionOverrides + (index to type)
		}
		userPreferences[preference] = overrides.toHomeSectionOverridesString()
	}

	val activeHomesections
		get() = resolveHomeSections(homesections.map(::get), homeSectionOverrides)
}
