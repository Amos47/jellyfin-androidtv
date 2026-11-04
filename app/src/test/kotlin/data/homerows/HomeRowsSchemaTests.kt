package org.jellyfin.androidtv.data.homerows

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.UUID

private val provider = HomeRowProvider(
	id = "latest-media",
	name = "Recently Added",
	description = "The latest items added to a library.",
	schema = Json.parseToJsonElement(
		"""{"type":"object","properties":{
		"showWatched":{"type":"boolean","title":"Show watched"},
		"limit":{"type":"integer","title":"Limit","minimum":1,"maximum":50},
		"note":{"type":"string","title":"Note","maxLength":200},
		"library":{"type":"string","title":"Library","enum":["a1","b2"],"x-enumLabels":["Shows","Recordings"]},
		"kinds":{"type":"array","title":"Kinds","items":{"type":"string","enum":["Movie","Series"]},"x-enumLabels":["Movies"],"minItems":1},
		"cardStyle":{"type":"string","title":"Card style","enum":["poster","thumb"],"x-enumLabels":["Poster","Thumb"]}
		},"required":["library"]}"""
	).jsonObject,
	defaults = Json.parseToJsonElement("""{"showWatched":false,"limit":20,"kinds":["Movie"],"cardStyle":"poster"}""").jsonObject,
)

private val valid = Json.parseToJsonElement(
	"""{"showWatched":true,"limit":10,"note":"hi","library":"a1","kinds":["Series"],"cardStyle":"thumb"}"""
).jsonObject

private fun JsonObject.with(key: String, value: JsonElement?) = JsonObject(
	if (value == null) this - key else this + (key to value)
)

class HomeRowsSchemaTests : FunSpec({
	val fields = provider.fields()

	test("fields are one per property, in order, with kinds") {
		fields.map { it.key to it.kind } shouldBe listOf(
			"showWatched" to HomeRowFieldKind.BOOLEAN,
			"limit" to HomeRowFieldKind.INTEGER,
			"note" to HomeRowFieldKind.STRING,
			"library" to HomeRowFieldKind.ENUM,
			"kinds" to HomeRowFieldKind.MULTI_ENUM,
			"cardStyle" to HomeRowFieldKind.ENUM,
		)
	}

	test("fields take labels, bounds, required and defaults from the schema") {
		val (showWatched, limit, note, library, kinds) = fields

		showWatched.label shouldBe "Show watched"
		showWatched.required shouldBe false
		showWatched.defaultValue shouldBe JsonPrimitive(false)
		limit.min shouldBe 1
		limit.max shouldBe 50
		limit.defaultValue shouldBe JsonPrimitive(20)
		note.maxLength shouldBe 200
		library.required shouldBe true
		library.options shouldBe listOf(HomeRowOption("a1", "Shows"), HomeRowOption("b2", "Recordings"))
		kinds.minItems shouldBe 1
		kinds.options shouldBe listOf(HomeRowOption("Movie", "Movies"), HomeRowOption("Series", "Series"))
	}

	test("unknown types are strings and unknown ui hints are ignored") {
		val odd = provider.copy(
			schema = Json.parseToJsonElement("""{"type":"object","properties":{"x":{"type":"number","x-ui":"slider"}}}""").jsonObject,
			defaults = JsonObject(emptyMap()),
		)

		val field = odd.fields().single()
		field.key shouldBe "x"
		field.kind shouldBe HomeRowFieldKind.STRING
		field.label shouldBe "x"
	}

	test("effectiveValue uses the config value, else the default") {
		val limit = fields[1]

		limit.effectiveValue(JsonObject(mapOf("limit" to JsonPrimitive(5)))) shouldBe JsonPrimitive(5)
		limit.effectiveValue(JsonObject(emptyMap())) shouldBe JsonPrimitive(20)
	}

	test("null config value uses the default") {
		fields[1].effectiveValue(JsonObject(mapOf("limit" to JsonNull))) shouldBe JsonPrimitive(20)
	}

	test("a valid config has no errors") {
		validateRowConfig(fields, valid).shouldBeEmpty()
	}

	test("required settings without a default are required") {
		validateRowConfig(fields, valid.with("library", null)) shouldBe mapOf("library" to HomeRowFieldError.REQUIRED)
	}

	test("integers out of range or with fractions are out of range") {
		validateRowConfig(fields, valid.with("limit", JsonPrimitive(0))) shouldBe mapOf("limit" to HomeRowFieldError.OUT_OF_RANGE)
		validateRowConfig(fields, valid.with("limit", JsonPrimitive(51))) shouldBe mapOf("limit" to HomeRowFieldError.OUT_OF_RANGE)
		validateRowConfig(fields, valid.with("limit", JsonPrimitive(2.5))) shouldBe mapOf("limit" to HomeRowFieldError.OUT_OF_RANGE)
	}

	test("integer given a string is out of range") {
		validateRowConfig(fields, valid.with("limit", JsonPrimitive("ten"))) shouldBe mapOf("limit" to HomeRowFieldError.OUT_OF_RANGE)
	}

	test("strings over the maximum length are too long") {
		validateRowConfig(fields, valid.with("note", JsonPrimitive("a".repeat(201)))) shouldBe
			mapOf("note" to HomeRowFieldError.TOO_LONG)
	}

	test("multi enum below minItems needs one") {
		validateRowConfig(fields, valid.with("kinds", JsonArray(emptyList()))) shouldBe
			mapOf("kinds" to HomeRowFieldError.PICK_AT_LEAST_ONE)
	}

	test("options the server no longer offers are unknown") {
		validateRowConfig(fields, valid.with("library", JsonPrimitive("gone"))) shouldBe
			mapOf("library" to HomeRowFieldError.UNKNOWN_OPTION)
		validateRowConfig(fields, valid.with("kinds", JsonArray(listOf(JsonPrimitive("Gone"))))) shouldBe
			mapOf("kinds" to HomeRowFieldError.UNKNOWN_OPTION)
	}

	context("editorRowLabel") {
		val row = HomeRowInstance(
			UUID.randomUUID(),
			"latest-media",
			null,
			Json.parseToJsonElement("""{"library":"a1","cardStyle":"thumb"}""").jsonObject,
		)

		test("prefers a trimmed custom title") {
			editorRowLabel(row.copy(title = "  Kids  "), provider) shouldBe "Kids"
		}

		test("otherwise shows the provider name and its required choices") {
			editorRowLabel(row, provider) shouldBe "Recently Added · Shows"
		}

		test("label is \"Recently Added · Recordings\"") {
			editorRowLabel(row.copy(config = JsonObject(mapOf("library" to JsonPrimitive("b2")))), provider) shouldBe
				"Recently Added · Recordings"
		}

		test("shows just the provider name when nothing required is chosen") {
			editorRowLabel(row.copy(config = JsonObject(emptyMap())), provider) shouldBe "Recently Added"
		}

		test("label for a missing provider is the provider id") {
			editorRowLabel(row, null) shouldBe "latest-media"
		}
	}

	test("a provider without properties has no fields") {
		provider.copy(schema = JsonObject(emptyMap())).fields().shouldBeEmpty()
	}
})
