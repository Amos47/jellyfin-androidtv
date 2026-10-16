# Syncing with upstream

`custom` is based on upstream's **release branch**, never on `master`. The upstream README says `master` is unstable
and forks should use the latest `release-x.y.z`. Release branches are created at the start of a beta cycle and receive
every patch release.

**Always merge. Never rebase or force-push `custom`.** The versionCode is the commit count, and history must stay
append-only for in-place updates to work.

## One-time setup

```sh
git remote add upstream https://github.com/jellyfin/jellyfin-androidtv.git   # origin = personal fork
git fetch upstream --tags
```

## Routine update

```sh
git fetch upstream --tags
git tag --sort=-creatordate | head                 # newest upstream releases
git branch -r --list 'upstream/release-*'          # release branches

git checkout custom
git checkout -b sync/upstream-<version>            # e.g. sync/upstream-0.20.0
git merge upstream/release-0.20.z                  # or the newer release branch; see below
# resolve conflicts (see hot spots), then:
./gradlew :app:testDebugUnitTest
./gradlew assembleRelease
scripts/deploy-firetv.sh                           # try it on the TV
git checkout custom && git merge --no-ff sync/upstream-<version>
git push origin custom
```

- **Patch releases** (e.g. 0.20.1): merge the same `upstream/release-0.20.z` branch again.
- **New minor release** (e.g. 0.21): wait until `upstream/release-0.21.z` exists and has a stable tag (or a beta you
  trust), then merge that branch. Merging a newer release branch into `custom` works because each release branch
  descends from master.
- Read the upstream release notes for changes to home rows, cards, image loading, Next Up or the build script.

## Conflict hot spots

These are files the fork modifies, not adds. Check them first when a merge conflicts:

| File | Fork change |
|---|---|
| `app/build.gradle.kts` | applicationId, git-based version, resValues from applicationId, `DEVELOPMENT` flag |
| `res/values/strings.xml` | app names, `home_section_resume` label, new strings |
| `ui/itemhandling/ItemRowAdapter.java` | `QueryType` constructor, `ContinueWatching`/`NewReleases` cases, `UserPreferences` |
| `ui/itemhandling/ItemRowAdapterHelper.kt` | `retrieveNextUpItems(…userPreferences…)`, one card per series in `retrieveResumeItems` |
| `ui/itemhandling/BaseItemDtoBaseRowItem.kt`, `BaseRowItem.kt` | `getImages()` and the episode image chain |
| `ui/presentation/CardPresenter.kt` | fallback images |
| `ui/AsyncImageView.kt`, `ui/composable/AsyncImage.kt` | `fallbackUrls` |
| `ui/home/HomeFragment.kt` | rebuild rows after settings changes |
| `ui/home/HomeRowsFragment.kt`, `HomeFragmentHelper.kt` | new sections |
| `ui/settings/routes.kt`, `screen/home/SettingsHomeScreen.kt` | new settings |
| `constant/HomeSectionType.kt`, `constant/QueryType.kt` | new entries |
| `preference/UserPreferences.kt` | new preferences |
| `ui/itemdetail/FullDetailsFragmentHelper.kt`, `integration/LeanbackChannelWorker.kt` | Next Up with user preferences |
| `data/repository/NotificationsRepository.kt` | beta notice only for the official app |

Everything else the fork adds lives in new files (`CustomHomeRows*.kt`, `EpisodeImages.kt`, `HomeConfiguration.kt`,
`NextUpRequestExtensions.kt`, `GitVersionUtils.kt`, …), which never conflict.

After resolving, search for any remaining Next Up requests that bypass `withUserPreferences`:

```sh
git grep -n 'getNextUp(' -- '*.kt' '*.java'
```

## Upstreaming

These changes are good candidates for upstream PRs, since they're generic and small:

- the rewatching setting
- the episode artwork fallback
- rebuilding the home screen after settings changes
- `resValue`s derived from `applicationId`

Once a change is merged upstream, resolve the conflict in favor of upstream and delete the fork version.
