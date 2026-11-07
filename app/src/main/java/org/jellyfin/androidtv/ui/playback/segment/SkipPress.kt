package org.jellyfin.androidtv.ui.playback.segment

import org.jellyfin.sdk.model.api.MediaSegmentType
import kotlin.time.Duration

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

/**
 * The skip button for [position]: shown for the whole segment, including after resuming in the middle of it, until the
 * last second. Checked on every position update instead of only when playback crosses the segment start.
 */
fun skipPrompt(
	position: Duration,
	segments: List<SkipSegment>,
	hasNextItem: Boolean,
	dismissedStart: Duration?,
): SkipPrompt? {
	val current = segments
		.filter { it.start <= position && position < it.end - MediaSegmentRepository.SkipMinDuration }
		.maxByOrNull { it.start }
		?: return null

	if (current.start == dismissedStart) return null

	return SkipPrompt(
		target = current.end,
		nextEpisode = skipPress(current.type, hasNextItem) == SkipPress.NEXT_EPISODE,
		segmentStart = current.start,
	)
}
