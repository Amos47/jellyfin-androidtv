package org.jellyfin.androidtv.data.querying

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import java.util.UUID

class NextUpRequestExtensionsTests : FunSpec({
	fun userPreferences(rewatching: Boolean) = mockk<UserPreferences> {
		every { this@mockk[UserPreferences.nextUpRewatchingEnabled] } returns rewatching
	}

	test("Rewatching is disabled by default") {
		UserPreferences.nextUpRewatchingEnabled.defaultValue shouldBe false
	}

	test("withUserPreferences enables rewatching when preference is enabled") {
		val request = GetNextUpRequest(limit = 50).withUserPreferences(userPreferences(rewatching = true))

		request.enableRewatching shouldBe true
	}

	test("withUserPreferences disables rewatching when preference is disabled") {
		val request = GetNextUpRequest(limit = 50, enableRewatching = true).withUserPreferences(userPreferences(rewatching = false))

		request.enableRewatching shouldBe false
	}

	test("withUserPreferences keeps other request parameters") {
		val seriesId = UUID.randomUUID()
		val original = GetNextUpRequest(seriesId = seriesId, limit = 1, enableResumable = false)
		val request = original.withUserPreferences(userPreferences(rewatching = true))

		request shouldBe original.copy(enableRewatching = true)
	}
})
