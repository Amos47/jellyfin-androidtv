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
| [Portrait images on landscape cards](#portrait-images-on-landscape-cards) | P1 | Implemented, not installed yet |
| [Series-level resume (Play button)](#series-level-resume) | P1 | Planned |
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
  - The section is stored in the server display preferences shared with web and iPhone. Those clients render it as an
    empty section.
  - Use home slots that are "None" on the web, and avoid saving Home settings from the web.
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

- **Problem:** new episodes without a still, backdrop or thumb fell back to the series poster. A 2:3 poster
  center-cropped into a 16:9 card kept only its middle band, cutting off heads and titles. Seen on Cupertino and
  Law & Order S26.
- **Behavior:** portrait images are fitted inside landscape cards over a background decoded from the image's
  blurhash. Portrait means series or parent posters, or images the server reports as taller than wide. No extra
  requests are made.
- **Location:** `CardPresenter.kt` (letterbox), `EpisodeImages.kt` (`JellyfinImage.isPortrait`), and
  `AsyncImage.kt`, which now applies the scale type on every update.
- **Tests:** `EpisodeImagesTests` (`isPortrait`).
- **Limitation:** when the episode image fails to load and the load-time fallback reaches a poster, the poster is
  still cropped, because the card decides on letterboxing from metadata before loading.

## Series-level resume

- **Problem:**
  - **Play** on a series starts at the pilot: `SdkPlaybackHelper.getItemsToPlay` queues all episodes from the start.
  - **Resume** already uses Next Up, so it follows rewatching.
- **Plan:**
  - Make Play on a started series use the same episode choice as the combined Continue Watching row: the in-progress
    episode, else next up.
  - Queue from that episode in aired order. A never-watched series still starts at the pilot.
  - For a fully watched series without a rewatch, consider an explicit "Watch again from the start" choice.
- **Tests to add:** never watched, partially watched, in-progress episode, rewatch position, end of season, end of
  series, specials.

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
