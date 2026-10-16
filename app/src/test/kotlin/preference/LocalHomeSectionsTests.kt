package org.jellyfin.androidtv.preference

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.constant.HomeSectionType

class LocalHomeSectionsTests : FunSpec({
	test("homeSectionIndex parses home section keys only") {
		homeSectionIndex("homesection0") shouldBe 0
		homeSectionIndex("homesection9") shouldBe 9
		homeSectionIndex("homesection") shouldBe null
		homeSectionIndex("skipBackLength") shouldBe null
	}

	test("Local home sections round trip") {
		val sections = mapOf(
			3 to HomeSectionType.CONTINUE_WATCHING_COMBINED,
			7 to HomeSectionType.NEW_RELEASES,
		)

		sections.toLocalHomeSectionsString() shouldBe "3=custom_continuewatching,7=custom_newreleases"
		parseLocalHomeSections(sections.toLocalHomeSectionsString()) shouldBe sections
	}

	test("Invalid and server section types are ignored") {
		parseLocalHomeSections("") shouldBe emptyMap()
		parseLocalHomeSections("x=custom_newreleases,2=nextup,4=unknown,5") shouldBe emptyMap()
		parseLocalHomeSections("1=custom_newreleases,2=nextup") shouldBe mapOf(1 to HomeSectionType.NEW_RELEASES)
	}
})
