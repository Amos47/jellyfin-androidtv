# Home rows

## Server home rows

On the owner's server fork (`~/Code/jellyfin`, Custom Home Rows), the server decides the home screen: an ordered list
of rows, each from a server **provider** with its own settings. The TV treats rows generically, as jellyfin-web does.

- **Detection:** `GET HomeRows/Configuration` with `ApiVersion` 1. A 404 is remembered per server for the process;
  other failures are asked again next time. Stock servers get everything below this section unchanged.
- **Calls:** `data/homerows/HomeRowsApi.kt`. The SDK has no HomeRows endpoints and can't send PUT, so requests go
  through OkHttp with the SDK's authorization header.
- **Home screen:** `buildServerRows` loads the layout, then every enabled row's first 20 items in parallel (10 second
  limit each). Rows that are disabled, unavailable, need configuration, are empty or fail are left out. Cards follow
  the row's `Presentation.CardShape`:

  | CardShape | Cards |
  |---|---|
  | Thumb | wide; episodes use their own image |
  | Poster | posters; episodes show the series poster |
  | Square | square |
  | Button, or all items are libraries | the My media rows (small or large) |

- **Settings → Home** becomes a rows editor (`ui/settings/screen/home/rows/`):
  - the rows list, with Hidden / Unavailable / Needs settings captions, **Add row**, **Save**, **Reset to default**,
    and for admins **Save as default for everyone** and **Remove household default** (each confirmed);
  - **Reorder rows** on the list: select a row to pick it up, move it with up/down, select again (or Back) to drop it;
  - one screen per row: show on home screen, title, and one entry per provider setting (checkbox,
    pick-one, pick-many, number, text);
  - edits stay pending in `HomeRowsEditorViewModel` until Save. Back from the list, or closing Settings, with
    pending edits asks **Save / Discard / Keep editing**. A failed save keeps the edits and shows the server's
    messages on the rows.
- **Moved settings:** the Next Up cutoff and New releases period are row settings on the server, so the TV hides its
  local versions. "Enable rewatching in Next Up" stays: series Resume, library Next Up and the launcher still use it.
- **TV launcher:** on a HomeRows server, `LeanbackChannelWorker` publishes one launcher row: the items of the first
  shown row that isn't a library row (`launcherRow`) go to **Watch Next**, which is what the Fire TV home shows for
  apps. Preview channels are not shown on the Fire TV, so the standard channels are removed. If
  the layout fails to load, the worker retries later instead of publishing the standard channels. On a stock server,
  the standard five channels stay.
- **Refresh:** every successful save, reset or default change bumps `HomeRowsApi.revision`, which is part of the
  `HomeConfiguration` snapshot, so the home screen is rebuilt when Settings closes.

## Stock server sections

The fork adds two home section types. They're used as **per-user overrides on this TV** (see below), picked in
Settings → Home → Home section N → This TV only:

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

The **server** layout stays the source of truth for every client. It is stored in the display preferences
(`usersettings`, client `emby`) and shared with jellyfin-web and the iPhone app. On top of that, each slot can have a
**per-user override on this device**:

| Slot | All devices (server) | This TV only (override) |
|---|---|---|
| 1 | Continue watching (in progress only) | Continue watching (combined) |
| 2 | Next up | Hidden |
| 3 | Latest media | (none: same as all devices) |

- **Settings:** Settings → Home → Home section N has two groups. **All devices** holds the standard sections, saved on
  the server. **This TV only** holds "Same as all devices", Continue watching, New releases and "Hidden on this TV".
- **Why fork sections can't go on the server:** the server only stores section types it knows. It replaces unknown
  values with the slot's default (`DisplayPreferencesController`).
- **Storage:** overrides are local SharedPreferences, one key per Jellyfin user (`home_section_overrides_<userId>`), so
  household members on the same TV don't share them. Signed out means no overrides. See
  `preference/HomeSectionOverrides.kt` and `UserSettingPreferences.homeSectionOverrides` / `setHomeSectionOverride`.
- **Other clients** keep their standard sections and never see the overrides.
- **Saving:** server settings are committed outside the settings screen's composition (`rememberPreference`). Before,
  navigating back cancelled the commit, so section changes were never saved (an upstream bug).

## Refreshing after settings changes

Settings is an overlay, so the home screen isn't paused or resumed when it closes. `HomeFragment` snapshots a
`HomeConfiguration` when Settings opens: the sections plus the Next Up cutoff, rewatching, New Releases period and
series thumbnails settings. If the snapshot differs when Settings closes, it recreates the rows fragment.
