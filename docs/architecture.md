# Architecture (code navigation)

`A/` = `app/src/main/java/org/jellyfin/androidtv/`. This was written against 0.20 (`release-0.20.z`).

## Stack

- **Language and build:** Kotlin and some Java. AGP 9.4, Kotlin 2.4, JDK 21. minSdk 23, targetSdk 36. Versions are in
  `gradle/libs.versions.toml`.
- **Dependency injection:** Koin. Modules are in `A/di/` (`AppModule`, `UtilsModule`, `PreferenceModule`, …) and are
  loaded by the androidx.startup initializer `A/di/KoinInitializer.kt`.
- **UI:**
  - Leanback (`RowsSupportFragment`, `ListRow`, `Presenter`) for browsing rows.
  - Compose for cards, the toolbar and settings.
  - Most screens are fragments.
- **Server API:** the Jellyfin Kotlin SDK (`org.jellyfin.sdk`), using request classes such as `GetNextUpRequest` and
  `GetItemsRequest`.
- **Images:** Coil 3, wrapped by `A/ui/AsyncImageView.kt` and the Compose `A/ui/composable/AsyncImage.kt`.
- **Playback:** Media3 ExoPlayer, in the `playback/*` modules and `A/ui/playback/`.

## Entry points and navigation

- Launcher: `A/ui/startup/StartupActivity.kt`, then `A/ui/browsing/MainActivity.kt`.
- `A/ui/navigation/Destinations.kt` lists the screens, which are fragment destinations. Screens are opened with
  `NavigationRepository.navigate(...)`.
- Settings is a Compose **overlay dialog**:
  - `A/ui/settings/compat/MainActivitySettings.kt`, with visibility in `SettingsViewModel`.
  - Routes are in `A/ui/settings/routes.kt`, and screens in `A/ui/settings/screen/**`.
  - It does **not** pause the screen underneath.

## Home screen

```
HomeFragment (Compose host; recreates rows when home settings change)
 └─ HomeRowsFragment (Leanback rows)
     ├─ reads UserSettingPreferences.activeHomesections (server display prefs, shared with web)
     ├─ maps HomeSectionType → HomeFragmentRow via HomeFragmentHelper
     └─ rows are ListRow(ItemRowAdapter) rendered by CardPresenter
```

- `A/constant/HomeSectionType.kt`: section types, mirroring jellyfin-web, plus the fork's `custom_*` types.
- `A/ui/home/HomeFragmentHelper.kt`: one `load*()` per section.
- `A/ui/itemhandling/ItemRowAdapter.java`:
  - `Retrieve()` dispatches on `QueryType` to `retrieve*` functions in `ItemRowAdapterHelper.kt` and
    `CustomHomeRowsAdapterHelper.kt`.
  - Rows refresh after playback through `ChangeTriggerType` (`ReRetrieveIfNeeded`).
- Fork rows:
  - `A/ui/home/CustomHomeRows.kt` holds pure logic and request builders.
  - `A/ui/itemhandling/CustomHomeRowsAdapterHelper.kt` holds the loading. It lives in this package to reach
    `ItemRowAdapter`'s protected members.

## Browsing episodes

- **Series details:**
  - `A/ui/itemdetail/FullDetailsFragment.java` and `FullDetailsFragmentHelper.kt`.
  - The series case adds Next Up, Seasons, Specials and similar rows.
  - **Play** uses `SdkPlaybackHelper` and starts at the pilot.
  - **Resume** uses `getNextUpEpisode()`.
- **Season:** `ItemLauncher` (SEASON) opens `Destinations.folderBrowser`, which shows
  `A/ui/browsing/GenericFolderFragment.kt`. Its episode row is an `ItemRowAdapter` with `CardPresenter`.

## Cards and images

- `A/ui/presentation/CardPresenter.kt`:
  - A Leanback presenter whose view holder is a `ComposeView`.
  - `getDisplayConfig()` picks the aspect ratio. Episodes use 16:9, or 2:3 with `preferSeriesPoster`.
  - It loads the image with `AsyncImage(url, fallbackUrls, …)`.
- `A/ui/itemhandling/BaseRowItem.kt` / `BaseItemDtoBaseRowItem.kt`: `getImage()` and `getImages()` (best first,
  then fallbacks).
- `A/util/apiclient/JellyfinImage.kt`: image extensions on `BaseItemDto` (`itemImages`, `parentImages`,
  `parentBackdropImages`, `seriesPrimaryImage`, `seriesThumbImage`) and `getUrl()`.
- `A/util/apiclient/EpisodeImages.kt` (fork): the episode landscape fallback chain.
- `A/util/ImageHelper.kt`: image selection for the details screens. It has no fallback chain yet.

## Next Up and resume

- Next Up request sites:
  - `HomeFragmentHelper.loadNextUp`
  - `BrowsingUtils.create*NextUpRequest`
  - `FullDetailsFragmentHelper.getNextUpEpisode`
  - `LeanbackChannelWorker`
  - `CustomHomeRowsAdapterHelper`
- All of them execute through `withUserPreferences()` (`A/data/querying/NextUpRequestExtensions.kt`).
- Resume row: `HomeFragmentHelper.loadResume` → `ItemRowAdapterHelper.retrieveResumeItems`, with one card per series
  (fork).

## Playback

- `A/util/sdk/SdkPlaybackHelper.kt` `getItemsToPlay`: builds the queue. For a series it queues all episodes.
- `A/ui/playback/PlaybackLauncher.kt` sends the queue to the legacy `PlaybackController.java`, or to the rewrite when
  the `playback_new` preference is set.

## Preferences

- `preference/` module: `booleanPreference`, `intPreference`, `enumPreference` and stores.
- `A/preference/UserPreferences.kt`: local SharedPreferences, read with `userPreferences[Key]`. Fork keys:
  - `next_up_rewatching_enabled`
  - `home_new_releases_max_days`
- `A/preference/UserSettingPreferences.kt`: server display preferences (`usersettings`, client `emby`), shared with
  jellyfin-web. Holds `homesection0..9`.
- Settings UI: Compose screens using `rememberPreference(...)`.

## Tests

- Unit tests use kotest (`FunSpec`) and mockk, in `app/src/test/kotlin/`. Run them with
  `./gradlew :app:testDebugUnitTest`.
- Fork tests:
  - `NextUpRequestExtensionsTests`
  - `EpisodeImagesTests`
  - `AsyncImageViewTests`
  - `CustomHomeRowsTests`
  - `HomeConfigurationTests`
