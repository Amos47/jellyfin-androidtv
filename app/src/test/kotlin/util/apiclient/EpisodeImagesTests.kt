package org.jellyfin.androidtv.util.apiclient

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

class EpisodeImagesTests : FunSpec({
	val episodeId = UUID.randomUUID()
	val seasonId = UUID.randomUUID()
	val seriesId = UUID.randomUUID()

	fun episode(
		primary: Boolean = false,
		backdropParent: UUID? = null,
		thumbParent: UUID? = null,
		seriesThumb: Boolean = false,
		seriesPoster: Boolean = false,
	) = BaseItemDto(
		id = episodeId,
		type = BaseItemKind.EPISODE,
		seriesId = seriesId,
		seasonId = seasonId,
		imageTags = if (primary) mapOf(ImageType.PRIMARY to "episode-primary") else null,
		parentBackdropItemId = backdropParent,
		parentBackdropImageTags = backdropParent?.let { listOf("parent-backdrop") },
		parentThumbItemId = thumbParent,
		parentThumbImageTag = thumbParent?.let { "parent-thumb" },
		seriesThumbImageTag = if (seriesThumb) "series-thumb" else null,
		seriesPrimaryImageTag = if (seriesPoster) "series-poster" else null,
	)

	fun List<JellyfinImage>.describe() = map { "${it.type.name}:${it.item}:${it.tag}" }

	test("Episode image is used when present") {
		val images = episode(primary = true, backdropParent = seriesId, seriesPoster = true)
			.getEpisodeLandscapeImages(preferParentThumb = false)

		images.first().item shouldBe episodeId
		images.first().type shouldBe ImageType.PRIMARY
	}

	test("Full fallback order without thumbnail preference") {
		val images = episode(
			primary = true,
			backdropParent = seasonId,
			thumbParent = seriesId,
			seriesPoster = true,
		).getEpisodeLandscapeImages(preferParentThumb = false)

		images.describe() shouldBe listOf(
			"PRIMARY:$episodeId:episode-primary",
			"BACKDROP:$seasonId:parent-backdrop",
			"THUMB:$seriesId:parent-thumb",
			"PRIMARY:$seriesId:series-poster",
		)
	}

	test("Missing episode image falls back to season backdrop") {
		val images = episode(backdropParent = seasonId, seriesPoster = true)
			.getEpisodeLandscapeImages(preferParentThumb = false)

		images.describe() shouldBe listOf(
			"BACKDROP:$seasonId:parent-backdrop",
			"PRIMARY:$seriesId:series-poster",
		)
	}

	test("Missing episode image falls back to series backdrop") {
		val images = episode(backdropParent = seriesId)
			.getEpisodeLandscapeImages(preferParentThumb = false)

		images.describe() shouldBe listOf("BACKDROP:$seriesId:parent-backdrop")
	}

	test("Series thumb is used when no parent thumb is provided") {
		val images = episode(seriesThumb = true)
			.getEpisodeLandscapeImages(preferParentThumb = false)

		images.describe() shouldBe listOf("THUMB:$seriesId:series-thumb")
	}

	test("Only series poster available") {
		val images = episode(seriesPoster = true)
			.getEpisodeLandscapeImages(preferParentThumb = false)

		images.describe() shouldBe listOf("PRIMARY:$seriesId:series-poster")
	}

	test("No artwork available") {
		episode().getEpisodeLandscapeImages(preferParentThumb = false).shouldBeEmpty()
		episode().getEpisodeLandscapeImages(preferParentThumb = true).shouldBeEmpty()
	}

	test("Thumbnail preference puts parent thumb first and keeps the remaining fallbacks") {
		val images = episode(
			primary = true,
			backdropParent = seriesId,
			thumbParent = seriesId,
			seriesPoster = true,
		).getEpisodeLandscapeImages(preferParentThumb = true)

		images.describe() shouldBe listOf(
			"THUMB:$seriesId:parent-thumb",
			"PRIMARY:$episodeId:episode-primary",
			"BACKDROP:$seriesId:parent-backdrop",
			"PRIMARY:$seriesId:series-poster",
		)
	}

	context("isPortrait") {
		fun image(source: JellyfinImageSource, type: ImageType, aspectRatio: Float? = null) = JellyfinImage(
			item = UUID.randomUUID(),
			source = source,
			type = type,
			tag = "tag",
			blurHash = null,
			aspectRatio = aspectRatio,
			index = null,
		)

		test("Series and parent posters are portrait") {
			image(JellyfinImageSource.SERIES, ImageType.PRIMARY).isPortrait shouldBe true
			image(JellyfinImageSource.PARENT, ImageType.PRIMARY).isPortrait shouldBe true
		}

		test("Known aspect ratio decides") {
			image(JellyfinImageSource.ITEM, ImageType.PRIMARY, aspectRatio = 0.67f).isPortrait shouldBe true
			image(JellyfinImageSource.ITEM, ImageType.PRIMARY, aspectRatio = 1.78f).isPortrait shouldBe false
			image(JellyfinImageSource.SERIES, ImageType.PRIMARY, aspectRatio = 1.78f).isPortrait shouldBe false
		}

		test("Episode images, backdrops and thumbs without a known aspect ratio are landscape") {
			image(JellyfinImageSource.ITEM, ImageType.PRIMARY).isPortrait shouldBe false
			image(JellyfinImageSource.PARENT, ImageType.BACKDROP).isPortrait shouldBe false
			image(JellyfinImageSource.SERIES, ImageType.THUMB).isPortrait shouldBe false
		}
	}

	test("Thumbnail preference without any thumb uses the episode image") {
		val images = episode(primary = true)
			.getEpisodeLandscapeImages(preferParentThumb = true)

		images.describe() shouldBe listOf("PRIMARY:$episodeId:episode-primary")
	}
})
