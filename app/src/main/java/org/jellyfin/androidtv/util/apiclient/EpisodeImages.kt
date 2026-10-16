package org.jellyfin.androidtv.util.apiclient

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/**
 * Get the landscape images for an episode card, best candidate first. All candidates are derived from data the server
 * already includes with the episode, so falling back doesn't require additional API requests.
 *
 * 1. The episode image (Jellyfin stores episode stills as the primary image)
 * 2. A backdrop from the closest parent with backdrops (the season, otherwise the series)
 * 3. A thumb from a parent or the series
 * 4. The series poster, which will be cropped to fit the card
 *
 * When [preferParentThumb] is set the parent thumb is moved to the front to honor the "prefer series thumbnails"
 * preference.
 */
fun BaseItemDto.getEpisodeLandscapeImages(preferParentThumb: Boolean): List<JellyfinImage> {
	val episodeImage = itemImages[ImageType.PRIMARY]
	val backdropImage = itemBackdropImages.firstOrNull() ?: parentBackdropImages.firstOrNull()
	val thumbImage = parentImages[ImageType.THUMB] ?: seriesThumbImage
	val posterImage = seriesPrimaryImage

	val candidates = when (preferParentThumb) {
		true -> listOf(thumbImage, episodeImage, backdropImage, posterImage)
		false -> listOf(episodeImage, backdropImage, thumbImage, posterImage)
	}

	return candidates.filterNotNull().distinct()
}

/**
 * Whether this image is (most likely) taller than it is wide. Uses the aspect ratio when the server provides it, which
 * it only does for an item's own primary image. Otherwise posters (series and parent primary images) are assumed to be
 * portrait. Used to show posters in full instead of cropping them in landscape cards.
 */
val JellyfinImage.isPortrait: Boolean
	get() = aspectRatio?.let { it < 1f }
		?: (type == ImageType.PRIMARY && (source == JellyfinImageSource.SERIES || source == JellyfinImageSource.PARENT))
