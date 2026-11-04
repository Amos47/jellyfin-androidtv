package org.jellyfin.androidtv.data.homerows

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/*
 * Provider schemas (a JSON schema subset) to settings fields, the same rules as the web client's homeRowsSchema.ts.
 * Knows nothing about particular providers.
 */

enum class HomeRowFieldKind { BOOLEAN, INTEGER, STRING, ENUM, MULTI_ENUM }

data class HomeRowOption(val value: String, val label: String)

/** One setting of a provider, ready to become a settings input. */
data class HomeRowField(
	val key: String,
	val kind: HomeRowFieldKind,
	val label: String,
	val required: Boolean,
	val min: Int?,
	val max: Int?,
	val maxLength: Int?,
	val minItems: Int?,
	val options: List<HomeRowOption>,
	val defaultValue: JsonElement?,
)

enum class HomeRowFieldError { REQUIRED, OUT_OF_RANGE, TOO_LONG, UNKNOWN_OPTION, PICK_AT_LEAST_ONE }

private val JsonElement.stringOrNull get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.string(key: String) = this[key]?.stringOrNull
private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.strings(key: String) = (this[key] as? JsonArray)?.mapNotNull { it.stringOrNull }
private fun JsonObject.obj(key: String) = this[key] as? JsonObject

private fun kindOf(property: JsonObject) = when (property.string("type")) {
	"boolean" -> HomeRowFieldKind.BOOLEAN
	"integer" -> HomeRowFieldKind.INTEGER
	"array" -> HomeRowFieldKind.MULTI_ENUM
	else -> if (property.strings("enum") != null) HomeRowFieldKind.ENUM else HomeRowFieldKind.STRING
}

private fun optionsOf(property: JsonObject, kind: HomeRowFieldKind): List<HomeRowOption> {
	val values = if (kind == HomeRowFieldKind.MULTI_ENUM) property.obj("items")?.strings("enum") else property.strings("enum")
	val labels = property.strings("x-enumLabels").orEmpty()
	return values.orEmpty().mapIndexed { i, value -> HomeRowOption(value, labels.getOrNull(i) ?: value) }
}

/** The provider's settings, in schema property order. Unknown types become strings. */
fun HomeRowProvider.fields(): List<HomeRowField> {
	val required = schema.strings("required").orEmpty()
	val properties = schema.obj("properties") ?: return emptyList()

	return properties.mapNotNull { (key, value) ->
		val property = value as? JsonObject ?: return@mapNotNull null
		val kind = kindOf(property)
		HomeRowField(
			key = key,
			kind = kind,
			label = property.string("title") ?: key,
			required = key in required,
			min = property.int("minimum"),
			max = property.int("maximum"),
			maxLength = property.int("maxLength"),
			minItems = property.int("minItems"),
			options = optionsOf(property, kind),
			defaultValue = defaults[key] ?: property["default"],
		)
	}
}

/** The value a setting has for a row: its config value, else the provider default. */
fun HomeRowField.effectiveValue(config: JsonObject): JsonElement? =
	config[key]?.takeUnless { it is JsonNull } ?: defaultValue?.takeUnless { it is JsonNull }

private fun HomeRowField.isOption(value: JsonElement) = value.stringOrNull?.let { v -> options.any { it.value == v } } == true

private fun HomeRowField.validateInteger(value: JsonElement): HomeRowFieldError? {
	val primitive = value as? JsonPrimitive
	val number = primitive?.takeUnless { it.isString }?.contentOrNull?.toLongOrNull()
	val inRange = number != null && (min == null || number >= min) && (max == null || number <= max)
	return if (inRange) null else HomeRowFieldError.OUT_OF_RANGE
}

private fun HomeRowField.validateMultiEnum(value: JsonElement): HomeRowFieldError? {
	val values = value as? JsonArray ?: JsonArray(emptyList())
	if (minItems != null && values.size < minItems) return HomeRowFieldError.PICK_AT_LEAST_ONE
	return if (values.all { isOption(it) }) null else HomeRowFieldError.UNKNOWN_OPTION
}

private fun HomeRowField.validate(value: JsonElement?): HomeRowFieldError? {
	if (value == null || value.stringOrNull == "") return if (required) HomeRowFieldError.REQUIRED else null

	return when (kind) {
		HomeRowFieldKind.INTEGER -> validateInteger(value)
		HomeRowFieldKind.STRING -> {
			val length = (value as? JsonPrimitive)?.content?.length ?: 0
			if (maxLength != null && length > maxLength) HomeRowFieldError.TOO_LONG else null
		}

		HomeRowFieldKind.ENUM -> if (isOption(value)) null else HomeRowFieldError.UNKNOWN_OPTION
		HomeRowFieldKind.MULTI_ENUM -> validateMultiEnum(value)
		HomeRowFieldKind.BOOLEAN -> null
	}
}

/** A row's setting problems, setting key → error. Empty when valid. */
fun validateRowConfig(fields: List<HomeRowField>, config: JsonObject): Map<String, HomeRowFieldError> =
	fields.mapNotNull { field -> field.validate(field.effectiveValue(config))?.let { field.key to it } }.toMap()

/**
 * The editor's label for a row: its custom title, else the provider name plus the labels of its required pick-one
 * settings ("Recently Added · Shows"), else the provider id when the provider is gone.
 */
fun editorRowLabel(row: HomeRowInstance, provider: HomeRowProvider?): String {
	row.title?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
	if (provider == null) return row.provider

	val choices = provider.fields()
		.filter { it.kind == HomeRowFieldKind.ENUM && it.required }
		.mapNotNull { field ->
			val value = (field.effectiveValue(row.config) as? JsonPrimitive)?.contentOrNull
			field.options.firstOrNull { it.value == value }?.label
		}

	return (listOf(provider.name) + choices).joinToString(" · ")
}
