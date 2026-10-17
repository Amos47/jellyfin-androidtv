package org.jellyfin.androidtv.preference

import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.preference.stringPreference
import java.util.UUID

/*
 * Personal fork: per-user home section overrides for this device. The server layout (shared with other clients) stays
 * untouched, and a slot can show a fork section or be hidden on this device instead.
 */

/**
 * Home section types that only exist in the fork. The server can't store these (it replaces unknown values with the
 * slot default), so they can only be used as an override.
 */
val LocalOnlyHomeSectionTypes = setOf(
	HomeSectionType.CONTINUE_WATCHING_COMBINED,
	HomeSectionType.NEW_RELEASES,
)

/**
 * Types that can be used as an override, in display order. [HomeSectionType.NONE] hides the slot on this device.
 */
val HomeSectionOverrideTypes = listOf(
	HomeSectionType.CONTINUE_WATCHING_COMBINED,
	HomeSectionType.NEW_RELEASES,
	HomeSectionType.NONE,
)

/**
 * The local preference holding the overrides of a user, stored as `index=serializedName` pairs.
 */
fun homeSectionOverridesPreference(userId: UUID) = stringPreference("home_section_overrides_$userId", "")

/**
 * Parse overrides stored as `index=serializedName` pairs, ignoring invalid entries and types that can't be overrides.
 */
fun parseHomeSectionOverrides(value: String): Map<Int, HomeSectionType> = value
	.split(',')
	.mapNotNull { entry ->
		val (index, name) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
		val type = HomeSectionType.entries.find { it.serializedName == name }?.takeIf { it in HomeSectionOverrideTypes }
		val slot = index.toIntOrNull()
		if (slot == null || type == null) null else slot to type
	}
	.toMap()

fun Map<Int, HomeSectionType>.toHomeSectionOverridesString(): String = entries
	.sortedBy { it.key }
	.joinToString(",") { (index, type) -> "$index=${type.serializedName}" }

/**
 * Combine the server layout with the overrides of this device and drop empty or hidden slots.
 */
fun resolveHomeSections(
	serverSections: List<HomeSectionType>,
	overrides: Map<Int, HomeSectionType>,
): List<HomeSectionType> = serverSections
	.mapIndexed { index, type -> overrides[index] ?: type }
	.filterNot { it == HomeSectionType.NONE }
