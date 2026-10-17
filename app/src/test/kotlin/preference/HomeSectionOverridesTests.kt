package org.jellyfin.androidtv.preference

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.constant.HomeSectionType
import java.util.UUID

class HomeSectionOverridesTests : FunSpec({
	test("Overrides round trip") {
		val overrides = mapOf(
			0 to HomeSectionType.CONTINUE_WATCHING_COMBINED,
			1 to HomeSectionType.NONE,
			6 to HomeSectionType.NEW_RELEASES,
		)

		overrides.toHomeSectionOverridesString() shouldBe "0=custom_continuewatching,1=none,6=custom_newreleases"
		parseHomeSectionOverrides(overrides.toHomeSectionOverridesString()) shouldBe overrides
	}

	test("Invalid entries and server section types are ignored") {
		parseHomeSectionOverrides("") shouldBe emptyMap()
		parseHomeSectionOverrides("x=custom_newreleases,2=nextup,4=unknown,5") shouldBe emptyMap()
		parseHomeSectionOverrides("1=custom_newreleases,2=nextup") shouldBe mapOf(1 to HomeSectionType.NEW_RELEASES)
	}

	test("Overrides are stored per user") {
		val userA = UUID.randomUUID()
		val userB = UUID.randomUUID()

		(homeSectionOverridesPreference(userA).key == homeSectionOverridesPreference(userB).key) shouldBe false
	}

	test("Resolving replaces and hides slots and keeps the server layout otherwise") {
		val server = listOf(
			HomeSectionType.LIBRARY_BUTTONS,
			HomeSectionType.RESUME,
			HomeSectionType.NEXT_UP,
			HomeSectionType.LATEST_MEDIA,
			HomeSectionType.NONE,
		)
		val overrides = mapOf(
			1 to HomeSectionType.CONTINUE_WATCHING_COMBINED,
			2 to HomeSectionType.NONE,
			4 to HomeSectionType.NEW_RELEASES,
		)

		resolveHomeSections(server, overrides) shouldBe listOf(
			HomeSectionType.LIBRARY_BUTTONS,
			HomeSectionType.CONTINUE_WATCHING_COMBINED,
			HomeSectionType.LATEST_MEDIA,
			HomeSectionType.NEW_RELEASES,
		)
	}

	test("Without overrides the server layout is used") {
		resolveHomeSections(listOf(HomeSectionType.RESUME, HomeSectionType.NONE), emptyMap()) shouldBe
			listOf(HomeSectionType.RESUME)
	}
})
