package org.jellyfin.androidtv.ui.playback.segment

import org.jellyfin.sdk.model.api.MediaSegmentType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * Personal fork: what the skip button does. Skipping the credits goes straight to the next episode, like the phone.
 */

enum class SkipPress {
	/** Seek to the end of the segment. */
	SEEK,

	/** Play the next episode in the queue right away. */
	NEXT_EPISODE,
}

fun skipPress(type: MediaSegmentType, hasNextItem: Boolean): SkipPress =
	if (type == MediaSegmentType.OUTRO && hasNextItem) SkipPress.NEXT_EPISODE else SkipPress.SEEK

/** A segment the user is asked about skipping. */
data class SkipSegment(
	val type: MediaSegmentType,
	val start: Duration,
	val end: Duration,
)

/**
 * The skip button to show: seek to [target], or play the next episode when [nextEpisode]. [segmentStart] identifies the
 * segment, so dismissing it hides the button for that segment only.
 */
data class SkipPrompt(
	val target: Duration,
	val nextEpisode: Boolean,
	val segmentStart: Duration,
)

/** How long before the end an episode without a credits marker offers the next episode. */
val NextEpisodeFallbackWindow = 30.seconds

/**
 * The skip button for [position]: shown for the whole segment, including after resuming in the middle of it, until the
 * last second. Checked on every position update instead of only when playback crosses the segment start.
 */
fun skipPrompt(
	position: Duration,
	segments: List<SkipSegment>,
	hasNextItem: Boolean,
	dismissedStart: Duration?,
	runtime: Duration? = null,
	hasCreditsMarker: Boolean = true,
): SkipPrompt? {
	val current = segments
		.filter { it.start <= position && position < it.end - MediaSegmentRepository.SkipMinDuration }
		.maxByOrNull { it.start }
		?: return nextEpisodeFallback(position, hasNextItem, dismissedStart, runtime, hasCreditsMarker)

	if (current.start == dismissedStart) return null

	return SkipPrompt(
		target = current.end,
		nextEpisode = skipPress(current.type, hasNextItem) == SkipPress.NEXT_EPISODE,
		segmentStart = current.start,
	)
}

/** Without a credits marker, the last [NextEpisodeFallbackWindow] offers the next episode, like the phone. */
private fun nextEpisodeFallback(
	position: Duration,
	hasNextItem: Boolean,
	dismissedStart: Duration?,
	runtime: Duration?,
	hasCreditsMarker: Boolean,
): SkipPrompt? {
	if (!hasNextItem || hasCreditsMarker || runtime == null) return null

	val start = runtime - NextEpisodeFallbackWindow
	if (position < start || start == dismissedStart) return null

	return SkipPrompt(target = runtime, nextEpisode = true, segmentStart = start)
}
