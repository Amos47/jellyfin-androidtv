package org.jellyfin.androidtv.constant

enum class QueryType {
	Items,
	NextUp,
	Views,
	Season,
	Upcoming,
	SimilarSeries,
	SimilarMovies,
	StaticPeople,
	StaticChapters,
	Search,
	Specials,
	AdditionalParts,
	Trailers,
	LiveTvChannel,
	LiveTvProgram,
	LiveTvRecording,
	StaticItems,
	Artists,
	AlbumArtists,
	AudioPlaylists,
	LatestItems,
	SeriesTimer,
	Premieres,
	Resume,

	// Personal fork home rows, see ui/home/CustomHomeRows.kt
	ContinueWatching,
	NewReleases,
}
