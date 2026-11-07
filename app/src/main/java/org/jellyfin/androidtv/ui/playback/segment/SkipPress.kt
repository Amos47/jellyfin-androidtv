package org.jellyfin.androidtv.ui.playback.segment

import org.jellyfin.sdk.model.api.MediaSegmentType

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
