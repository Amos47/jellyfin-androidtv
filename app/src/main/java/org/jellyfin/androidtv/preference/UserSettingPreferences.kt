package org.jellyfin.androidtv.preference

import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.store.DisplayPreferencesStore
import org.jellyfin.preference.Preference
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.sdk.api.client.ApiClient

class UserSettingPreferences(
	api: ApiClient,
	private val userPreferences: UserPreferences,
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
	 * Personal fork: home section types the server can't store are kept on the device, see [LocalHomeSectionTypes].
	 */
	private var localHomeSections: Map<Int, HomeSectionType>
		get() = parseLocalHomeSections(userPreferences[UserPreferences.localHomeSections])
		set(value) {
			userPreferences[UserPreferences.localHomeSections] = value.toLocalHomeSectionsString()
		}

	override fun <T : Enum<T>> getEnum(preference: Preference<T>): T {
		val localType = homeSectionIndex(preference.key)?.let { index -> localHomeSections[index] }
		@Suppress("UNCHECKED_CAST")
		if (localType != null && preference.type == HomeSectionType::class) return localType as T

		return super.getEnum(preference)
	}

	override fun <V : Enum<V>> setEnum(preference: Preference<*>, value: Enum<V>) {
		val index = homeSectionIndex(preference.key)
		if (index == null || value !is HomeSectionType) return super.setEnum(preference, value)

		if (value in LocalHomeSectionTypes) {
			localHomeSections = localHomeSections + (index to value)
			super.setEnum(preference, HomeSectionType.NONE)
		} else {
			localHomeSections = localHomeSections - index
			super.setEnum(preference, value)
		}
	}

	val activeHomesections
		get() = homesections
			.map(::get)
			.filterNot { it == HomeSectionType.NONE }
}
