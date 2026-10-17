# Feature tracker

This is the authoritative backlog for the personal fork. Update it whenever a feature changes status.

Statuses: **Planned** → **In progress** → **Implemented** (unit tested, built) → **Verified** (checked on the Fire TV).

| Feature | Priority | Status |
|---|---|---|
| [Rewatching in Next Up](#rewatching-in-next-up) | P0 | Verified |
| [Episode artwork fallback](#episode-artwork-fallback) | P0 | Implemented, device check pending |
| [Custom app identity and signing](#custom-app-identity-and-signing) | P0 | Verified |
| [Fire TV deployment](#fire-tv-deployment) | P0 | Verified |
| [Combined Continue Watching section](#combined-continue-watching-section) | P1 | Verified |
| [One card per show in Continue Watching](#one-card-per-show-in-continue-watching) | P1 | Verified |
| [Home refresh after settings changes](#home-refresh-after-settings-changes) | P1 | Implemented, device check pending |
| [New Releases section](#new-releases-section) | P1 | Implemented, device check pending |
| [Portrait images on landscape cards](#portrait-images-on-landscape-cards) | P1 | Verified (letterboxing works) |
| [Series-level resume](#series-level-resume) | P1 | Installed, device check pending |
| [Server settings saved reliably](#server-settings-saved-reliably) | P0 | Verified (layout now stored on the server) |
| [Per-user home section overrides](#per-user-home-section-overrides) | P0 | Implemented, not installed yet |
| [Series posters in Recently added](#series-posters-in-recently-added) | P1 | Verified |
| [Skip intro (server setup)](#skip-intro-server-setup) | P1 | Plugin installed, intro analysis not yet run |
| [CI/CD for the fork](#cicd-for-the-fork) | P1 | Planned |
| [Artwork fallback on details screens](#artwork-fallback-on-details-screens) | P2 | Planned |
| [Upstream sync automation](#upstream-sync-automation) | P2 | Planned |

---

## Rewatching in Next Up

- **Problem:** shows being rewatched never appeared in Next Up on the Fire TV, but did on the web and iPhone.
- **Root cause:**
  - The Jellyfin SDK sends `enableRewatching=false` by default, and the app never overrode it.
  - The web client's "Enable Rewatching in Next Up" is a browser-local setting. Other clients can't read it
    ([#4586](https://github.com/jellyfin/jellyfin-androidtv/issues/4586)).
- **Behavior:**
  - New setting: Settings → Home → **Enable rewatching in Next Up** (off by default).
  - It applies to every Next Up request: the home row, library and series rows, the series Resume button, and the
    Android TV home channels.
  - The server picks the episode after the most recently played one, so a rewatch continues where you are rather than
    from the pilot.
- **Location:**
  - `data/querying/NextUpRequestExtensions.kt` (`withUserPreferences`)
  - `UserPreferences.nextUpRewatchingEnabled`
  - `SettingsHomeScreen.kt`
- **Tests:** `NextUpRequestExtensionsTests`.
- **Compatibility:**
  - No server changes.
  - With rewatching on, a show can appear twice in Next Up (a new unwatched episode plus the rewatch position). The web
    client behaves the same; the combined Continue Watching section shows one card per show.
- **Details:** [rewatching.md](rewatching.md).

## Episode artwork fallback

- **Problem:**
  - In season browsing, episodes without their own image showed an icon placeholder.
  - Next Up only fell back from the series thumb to the episode image.
  - Images that failed to load left the card blank.
- **Behavior:** episode cards try, in order:
  1. the episode image
  2. the closest parent backdrop (season, otherwise series)
  3. the parent or series thumb
  4. the series poster
  5. the icon

  With "Prefer series thumbnails" on, the thumb stays first. When a load fails with an HTTP or decode error, the card
  moves to the next candidate. Network failures stop early.
- **Location:**
  - `util/apiclient/EpisodeImages.kt`
  - `BaseItemDtoBaseRowItem.getImages()`
  - `CardPresenter.kt`
  - `AsyncImageView.load(fallbackUrls)`
- **Tests:** `EpisodeImagesTests`, `AsyncImageViewTests`.
- **Upstream issues:**
  - [#1259](https://github.com/jellyfin/jellyfin-androidtv/issues/1259): poster shown instead of backdrop. Same class of problem.
  - [#2263](https://github.com/jellyfin/jellyfin-androidtv/issues/2263): episode images in Next Up preference. Related.
  - [#5684](https://github.com/jellyfin/jellyfin-androidtv/issues/5684): a 0.19 bug with Jellyfin 12 servers. Not present on 0.20.
- **Limitations:**
  - Cards only. Details screens still use `ImageHelper`; see [Artwork fallback on details screens](#artwork-fallback-on-details-screens).
  - A poster fallback is center-cropped into 16:9.
- **Details:** [artwork-fallbacks.md](artwork-fallbacks.md).

## Custom app identity and signing

- **Behavior:**
  - Release: `org.jellyfin.androidtv.custom`, "Jellyfin Custom", signed with a personal key.
  - Debug: `org.jellyfin.androidtv.custom.debug`, "Jellyfin Custom Debug".
  - Both install next to the official app. versionCode is the git commit count, so every build updates in place.
- **Location:**
  - `app/build.gradle.kts`
  - `buildSrc/src/main/kotlin/GitVersionUtils.kt`
  - `res/values/strings.xml` (`app_name_*`)
  - `NotificationsRepository.kt`: the beta notice is suppressed for the fork.
- **Details:** [development.md](development.md#signing) (including key backup).

## Fire TV deployment

- **Behavior:** `scripts/deploy-firetv.sh` builds, connects over wireless ADB, installs and launches the app.
  - `--debug` installs the debug build.
  - `--logs` streams the app's log output.
  - The device address comes from `firetv.host` in the gitignored `local.properties`.
- **Details:** [development.md](development.md#fire-tv-setup).

## Combined Continue Watching section

- **Problem:**
  - During a rewatch, Next Up shows the episode *after* the one in progress. The server skips resumable episodes and
    anchors on the last watched one.
  - The in-progress episode only appeared in the separate Continue Watching row.
- **Behavior:** a new home section, "Continue watching":
  - It contains in-progress episodes and movies, plus the next episode for shows with nothing in progress.
  - One card per show, ordered by most recent activity.
  - It follows the rewatching setting and the Next Up date cutoff.
  - An in-progress episode is replaced by next up when a later episode of that show was finished afterwards.
- **Location:**
  - `ui/home/CustomHomeRows.kt` (`combineContinueWatching`)
  - `ui/itemhandling/CustomHomeRowsAdapterHelper.kt`
  - `QueryType.ContinueWatching`
  - `HomeSectionType.CONTINUE_WATCHING_COMBINED` (`custom_continuewatching`)
- **Tests:** `CustomHomeRowsTests`.
- **Compatibility:**
  - Used as a per-user **This TV only** override on top of the server layout. The server can't store this section
    type. Web and iPhone keep the slot's standard section.
- **Details:** [home-rows.md](home-rows.md).

## One card per show in Continue Watching

- **Problem:** the upstream Continue Watching row lists every in-progress episode, so a rewatched show could appear
  several times.
- **Behavior:**
  - The row keeps the most recently played episode per show.
  - In the English section picker, the upstream section is labelled "Continue watching (in progress only)".
- **Location:** `ItemRowAdapterHelper.retrieveResumeItems` (uses `distinctBySeries`).
- **Tests:** `CustomHomeRowsTests` (`distinctBySeries`).

## Home refresh after settings changes

- **Problem:**
  - Home settings didn't apply until the app restarted. This affected section layout, Next Up cutoff, series
    thumbnails and rewatching.
  - Settings is an overlay that doesn't pause the home screen, and sections were only read when the rows were created.
  - This bug is also present upstream.
- **Behavior:** when Settings closes, the home rows are rebuilt if any home-related setting changed.
- **Location:** `ui/home/HomeConfiguration.kt`, `ui/home/HomeFragment.kt`.
- **Tests:** `HomeConfigurationTests`.
- **Upstream potential:** good candidate for an upstream PR.

## New Releases section

- **Behavior:** a new home section, "New releases":
  - It shows unwatched movies and episodes whose **release date** (`PremiereDate`) is within the last N days.
  - N is set in Settings → Home → **New releases period**, default 30.
  - One card per show, showing its earliest new episode.
  - Missing (virtual) episodes are excluded.
- **Location:**
  - `CustomHomeRows.kt` (`createNewReleasesRequest`, `collapseNewReleases`)
  - `SettingsHomeNewReleasesPeriodScreen.kt`
  - `UserPreferences.homeNewReleasesMaxDays`
  - `HomeSectionType.NEW_RELEASES` (`custom_newreleases`)
- **Tests:** `CustomHomeRowsTests`.
- **Limitations:** depends on release-date metadata. Items without a premiere date never appear.

## Portrait images on landscape cards

- **Problem:** Cupertino S01E02 and Law & Order S26E02 showed cropped heads. Their episode images are portrait
  380×562 `-thumb.jpg` sidecar files written by **Sonarr**. A 16:9 center crop of a portrait image keeps only its
  middle band. The rest of the library's episode images are 16:9. An earlier guess that the series poster fallback was
  responsible was wrong: those shows have backdrops and thumbs.
- **Behavior:** portrait images are fitted inside landscape cards over a background decoded from the image's
  blurhash. Portrait means the server reports the image as taller than wide (`primaryImageAspectRatio`), or it is a
  series or parent poster. No extra requests are made.
- **Location:** `CardPresenter.kt` (letterbox), `EpisodeImages.kt` (`JellyfinImage.isPortrait`), and
  `AsyncImage.kt`, which now applies the scale type on every update.
- **Tests:** `EpisodeImagesTests` (`isPortrait`).
- **Open:**
  - The Sonarr thumbs themselves. The user asked to hold this; don't delete Sonarr-managed files by hand.
  - Possibly rank portrait episode images below the 16:9 backdrop and thumb.
- **Limitation:** a load-time fallback that reaches a poster is still cropped.

## Series-level resume

- **Problem:**
  - A series' resume button ("Play next up") was hidden whenever the series was marked played. That is always the case
    during a rewatch, which left only **Play all**, starting at the pilot.
  - When the button was shown, it used Next Up, which skips the in-progress episode during a rewatch.
- **Behavior:**
  - The resume button shows, and gets focus, whenever there is an episode to continue with: the in-progress episode
    (with the resume or start-over menu), otherwise next up (including rewatches when enabled).
  - It uses the same selection as the combined Continue watching row (`combineContinueWatching`), so the app has one
    notion of "the next episode".
  - For a never-watched series, or a finished one with no rewatch, the button stays hidden and **Play all** (from the
    pilot) has focus.
  - Playback queues the following episodes when "media queuing" is enabled (the upstream behavior for episodes).
- **Location:** `ui/itemdetail/SeriesContinue.kt`, `FullDetailsFragmentHelper.getNextUpEpisode`, and
  `FullDetailsFragment.handleResumeButtonAndFocus`.
- **Tests:** `SeriesContinueTests`: never watched, partially watched, in progress, rewatch in progress, rewatch between
  episodes, and finished.
- **Requests:** 3 small parallel requests when a series page opens (resume items in the series, next up for the series,
  last played episode).
- **Play key on series cards:**
  - The remote's Play/Pause key on a series card continues with the same episode, at its saved position.
  - When there is nothing to continue, it falls back to the original play menu (play first unwatched, play all,
    shuffle).
  - Long-press → **Play all** still starts from the beginning.
  - Location: `KeyProcessor.java` (SERIES case) and `KeyProcessorHelper.playSeriesContinueEpisode`.

## Server settings saved reliably

- **Problem:**
  - Home section choices were lost when the app restarted.
  - Server-stored settings (`UserSettingPreferences`, `LiveTvPreferences`) were committed in a `LaunchedEffect` of the
    settings screen. List screens navigate back right after a choice, which cancelled the network commit.
  - The value only lived in memory, so the server had no `homesection*` keys at all. This is an **upstream bug**.
- **Behavior:** `rememberPreference` commits server stores in the process lifecycle scope, so leaving the screen
  doesn't cancel the save.
- **Location:** `ui/settings/compat/rememberPreference.kt`.
- **Upstream potential:** good candidate for an upstream PR.

## Per-user home section overrides

- **Problem:**
  - The server only stores home section types it knows, and replaces unknown values with the slot default. The fork's
    sections reverted after a restart.
  - A first fix stored "None" on the server for those slots. That left web and iPhone with empty slots, and the
    device-wide storage was shared between users.
- **Behavior:**
  - The server layout is unchanged and stays standard for other clients.
  - Each slot can have a **This TV only** override for the signed-in user: Continue watching (combined), New
    releases, or Hidden.
  - Overrides are stored locally per Jellyfin user ID.
  - The home settings list shows "X on this TV, Y elsewhere".
- **Location:** `preference/HomeSectionOverrides.kt`, `UserSettingPreferences` (`homeSectionOverrides`,
  `setHomeSectionOverride`, `activeHomesections`), and `SettingsHomeSectionScreen` / `SettingsHomeScreen`.
- **Tests:** `HomeSectionOverridesTests`, `UserSettingPreferencesTests`, including per-user isolation.
- **Details:** [home-rows.md](home-rows.md#storage-and-other-clients).

## Series posters in Recently added

- **Problem:** "Recently added in Shows" showed a single new episode as a wide episode card. The server doesn't group a
  single new episode into its series (Jellyfin 12, see #5684).
- **Behavior:** episodes and seasons in recently added rows are replaced with their series. The row shows posters and
  unwatched counts, at the position of the first occurrence, with no duplicates. This costs one extra request, and
  only when the row contains episodes or seasons.
- **Location:** `ItemRowAdapterHelper.retrieveLatestMedia`, and `CustomHomeRows.kt` (`replaceWithSeries`).
- **Tests:** `CustomHomeRowsTests` (`replaceWithSeries`).

## Skip intro (server setup)

- **Status:** the Intro Skipper 12.0.5.0 plugin was installed on 2026-10-16. Its detection task still needs to run;
  `MediaSegments` was empty when checked. No app change is needed. The app already shows an "ask to skip" button for intro and outro segments
  (Settings → Playback → Media segments), in both players.
- **Finding (2026-10-16):**
  - The server (Jellyfin 12.2.0, on the Mac) has no segment plugin installed, and its `MediaSegments` table is empty.
    So no show has skip intro, not just Friends.
  - Jellyfin doesn't detect intros by itself.
  - The bundled ffmpeg (8.1.3-Jellyfin) includes Chromaprint, which Intro Skipper needs.
- **Setup:**
  1. Install the **Intro Skipper** plugin (repository `https://intro-skipper.org/manifest.json`, current releases
     require Jellyfin 12).
  2. Restart the server.
  3. Let its analysis task run.
- **Note:** a failed segment request is silently treated as "no segments" (`MediaSegmentRepositoryImpl.getSegmentsForItem`).

## CI/CD for the fork

- **Plan:**
  - A GitHub Actions workflow for `custom` and PRs into it: unit tests plus `assembleRelease`, with the APK as an
    artifact.
  - Signing from repository secrets (`KEYSTORE_*`, see [development.md](development.md#ci)).
  - A full-history checkout (`fetch-depth: 0`) so versionCode is correct.
  - Tag-based GitHub releases.
  - Upstream's workflows only trigger on `master` and `release-*`, and its publish workflow is gated to the jellyfin
    organization.

## Artwork fallback on details screens

- **Plan:** use `getEpisodeLandscapeImages` in `util/ImageHelper.getPrimaryImageUrl` for episodes. The details screens
  (`FullDetailsFragment`, `ItemListFragment`) compute their own aspect ratio, so check the layout before changing it.

## Upstream sync automation

- **Plan:** a scheduled workflow that notices new upstream release tags and opens a PR merging the release branch into
  `custom`. Never auto-merge it.
