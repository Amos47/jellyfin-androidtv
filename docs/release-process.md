# Release process

There is no CI for the fork yet ([features.md](features.md#cicd-for-the-fork)). Releases are currently built locally
and installed over wireless ADB.

## Versions

Every build gets its version from git (see [development.md](development.md#versioning)):

- **versionName:** `git describe`, e.g. `0.20.0-beta.4-16-g985ca7e99`. That is the upstream release, the number of
  fork commits on top of it, and the commit hash.
- **versionCode:** the commit count of `HEAD`. It increases with every commit and merge, so any newer `custom` build
  updates the installed app.

## Build and install

```sh
git checkout custom && git pull
./gradlew :app:testDebugUnitTest
scripts/deploy-firetv.sh           # assembleRelease + install + launch
```

The signed APK is at `app/build/outputs/apk/release/jellyfin-androidtv-v<versionName>-release.apk`. To install on a
second device, set `FIRETV_HOST=<ip>` for that run.

Installing restarts the app, so avoid deploying while someone is watching.

## Checklist

- [ ] Unit tests pass.
- [ ] Release APK is signed with the personal key (`apksigner verify --print-certs <apk>` shows
      `CN=Jellyfin Custom`).
- [ ] Installed as an update: `firstInstallTime` is unchanged in `adb shell dumpsys package org.jellyfin.androidtv.custom`.
- [ ] Changed features checked on the TV, and [features.md](features.md) updated.
- [ ] `custom` pushed to `origin`.

## Planned (CI)

- Build and test on PRs into `custom` and on pushes to `custom`, publishing the APK as a workflow artifact.
- Tags like `custom-<n>` produce a GitHub release with the signed APK. Tags starting with `v` would collide with
  upstream's tag scheme.
- Signing comes from repository secrets. Checkout needs `fetch-depth: 0`.
