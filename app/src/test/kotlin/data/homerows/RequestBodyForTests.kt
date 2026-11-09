package org.jellyfin.androidtv.data.homerows

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okhttp3.Request
import okio.Buffer

class RequestBodyForTests : FunSpec({
	fun build(method: String, body: String?) =
		Request.Builder().url("http://server/x").method(method, requestBodyFor(method, body)).build()

	test("a POST without a body sends an empty one, which OkHttp requires") {
		val request = build("POST", null)

		request.body?.contentLength() shouldBe 0L
	}

	test("a GET or DELETE without a body sends none") {
		build("GET", null).body shouldBe null
		build("DELETE", null).body shouldBe null
	}

	test("a body is sent as JSON") {
		val body = build("PUT", """{"a":1}""").body!!
		val buffer = Buffer().also(body::writeTo)

		buffer.readUtf8() shouldBe """{"a":1}"""
		body.contentType().toString() shouldBe "application/json; charset=utf-8"
	}
})
