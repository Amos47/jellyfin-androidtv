package org.jellyfin.androidtv.data.querying

import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.model.api.request.GetNextUpRequest

/**
 * Apply the user's Next Up preferences to [this] request. Used by every Next Up query so all screens agree on what
 * "next up" means.
 */
fun GetNextUpRequest.withUserPreferences(userPreferences: UserPreferences) = copy(
	enableRewatching = userPreferences[UserPreferences.nextUpRewatchingEnabled],
)
