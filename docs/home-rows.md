# Home rows

The fork adds two home section types, picked in Settings → Home → Home section N:

| Picker label | Row title | Stored value |
|---|---|---|
| Continue watching | Continue watching | `custom_continuewatching` |
| New releases | New releases | `custom_newreleases` |

The original resume section is labelled "Continue watching (in progress only)" in the English picker. It now shows
one card per show: the most recently played in-progress episode.

## Continue watching (combined)

Three requests are made in parallel (`CustomHomeRowsAdapterHelper.retrieveContinueWatching`):

1. **Resume items:** in-progress videos (episodes and movies).
2. **Next Up:** with the rewatching setting and the Next Up date cutoff, `enableResumable=false`.
3. **Recently played episodes:** the 100 most recently played watched episodes. These provide each series' last
   activity date, which Next Up doesn't return.

`combineContinueWatching` merges them:

- One card per series:
  - The in-progress episode wins over the series' next up episode.
  - Exception: if a later episode of that series was **finished after** the in-progress one was last played, the
    in-progress episode is treated as abandoned and next up is shown instead.
- Movies in progress are kept.
- Ordered by most recent activity. Items without a known date keep server order at the end.

## New releases

`createNewReleasesRequest` asks for movies and episodes that are:

- `minPremiereDate` = now − N days (Settings → Home → **New releases period**, default 30) and `maxPremiereDate` = now
- unwatched (`isPlayed=false`)
- not missing (`isMissing=false`)
- sorted by premiere date, newest first, limit 200.

`collapseNewReleases` keeps one card per series: the earliest new episode, so you can watch in order. Series are
positioned by their newest release. Items without a release date never appear.

## Storage and other clients

Home sections are normally stored in the **server** display preferences (`usersettings`, client `emby`), shared with
jellyfin-web and the iPhone app.

- The server only stores section types it knows (`DisplayPreferencesController`). It replaces unknown values with the
  default section for that slot.
- The fork's section types are therefore kept **on the device**, per slot:
  - Stored in `UserPreferences.localHomeSections`, e.g. `3=custom_continuewatching`.
  - Handled in `UserSettingPreferences.getEnum` and `setEnum`, with parsing in `preference/LocalHomeSections.kt`.
  - The server stores `none` for those slots, so the web and iPhone show nothing there.
- Choosing a standard section for a slot clears its local override and syncs through the server as usual.
- Limitation: the local overrides apply to the device, not per Jellyfin user.
- Server-stored settings are committed outside the settings screen's composition (`rememberPreference`). Before this,
  navigating back cancelled the commit, so section changes were never saved on the server (an upstream bug).

## Refreshing after settings changes

Settings is an overlay, so the home screen isn't paused or resumed when it closes. `HomeFragment` snapshots a
`HomeConfiguration` when Settings opens: the sections plus the Next Up cutoff, rewatching, New Releases period and
series thumbnails settings. If the snapshot differs when Settings closes, it recreates the rows fragment.
