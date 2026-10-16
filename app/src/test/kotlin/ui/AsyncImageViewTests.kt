package org.jellyfin.androidtv.ui

import coil3.network.HttpException
import coil3.network.NetworkResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AsyncImageViewTests : FunSpec({
	test("Missing image (HTTP 404) falls back to the next image") {
		HttpException(NetworkResponse(code = 404)).allowsImageFallback() shouldBe true
	}

	test("Server error (HTTP 500) falls back to the next image") {
		HttpException(NetworkResponse(code = 500)).allowsImageFallback() shouldBe true
	}

	test("Invalid image falls back to the next image") {
		IllegalStateException("Failed to decode image").allowsImageFallback() shouldBe true
	}

	test("Network failures do not fall back") {
		IOException("Connection reset").allowsImageFallback() shouldBe false
		SocketTimeoutException().allowsImageFallback() shouldBe false
		UnknownHostException().allowsImageFallback() shouldBe false
	}
})
