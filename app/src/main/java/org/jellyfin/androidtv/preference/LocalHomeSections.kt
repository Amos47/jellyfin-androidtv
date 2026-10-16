package org.jellyfin.androidtv.preference

import org.jellyfin.androidtv.constant.HomeSectionType

/*
 * Personal fork: home section types the server can't store. The server replaces unknown home section values with the
 * default section for that slot, so these are kept on the device per slot while the server stores "none".
 */

/**
 * Home section types that are stored on the device instead of on the server.
 */
val LocalHomeSectionTypes = setOf(
	HomeSectionType.CONTINUE_WATCHING_COMBINED,
	HomeSectionType.NEW_RELEASES,
)

private val homeSectionKeyRegex = Regex("^homesection(\\d+)$")

/**
 * The slot index of a home section preference key (`homesection3` → 3), or null for other keys.
 */
fun homeSectionIndex(key: String): Int? = homeSectionKeyRegex.matchEntire(key)?.groupValues?.get(1)?.toIntOrNull()

/**
 * Parse local home sections stored as `index=serializedName` pairs, ignoring invalid or non-local entries.
 */
fun parseLocalHomeSections(value: String): Map<Int, HomeSectionType> = value
	.split(',')
	.mapNotNull { entry ->
		val (index, name) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
		val type = HomeSectionType.entries.find { it.serializedName == name }?.takeIf { it in LocalHomeSectionTypes }
		val slot = index.toIntOrNull()
		if (slot == null || type == null) null else slot to type
	}
	.toMap()

fun Map<Int, HomeSectionType>.toLocalHomeSectionsString(): String = entries
	.sortedBy { it.key }
	.joinToString(",") { (index, type) -> "$index=${type.serializedName}" }
