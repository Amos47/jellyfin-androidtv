# Development (personal fork)

This fork builds **Jellyfin Custom** (`org.jellyfin.androidtv.custom`). It installs next to the official Jellyfin app
and updates in place on every deploy.

| Build | Application id | Name | Signed with |
|---|---|---|---|
| release (daily use) | `org.jellyfin.androidtv.custom` | Jellyfin Custom | personal release key |
| debug (development) | `org.jellyfin.androidtv.custom.debug` | Jellyfin Custom Debug | `~/.android/debug.keystore` |

## Requirements (macOS)

- **JDK 21.** Temurin is installed in `~/Library/Java/JavaVirtualMachines`. Run `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` before using Gradle.
- **Android SDK.** Set `sdk.dir` in `local.properties` (gitignored), or set `ANDROID_HOME`. The Android Gradle Plugin downloads the platforms and build tools it needs.
- **adb.** `brew install --cask android-platform-tools`.

## Common commands

```sh
./gradlew :app:testDebugUnitTest   # unit tests (kotest + mockk)
./gradlew assembleRelease          # app/build/outputs/apk/release/
./gradlew assembleDebug            # app/build/outputs/apk/debug/
scripts/deploy-firetv.sh           # build + install + launch release on the Fire TV
scripts/deploy-firetv.sh --debug --logs
```

## Fire TV setup

1. On the Fire TV go to Settings → My Fire TV → About. Click the device name 7 times to unlock Developer Options.
2. In Settings → My Fire TV → Developer Options, turn on **ADB debugging**.
3. Find the IP address under Settings → My Fire TV → About → Network.
4. Add `firetv.host=<ip>` to `local.properties`. Alternatively, export `FIRETV_HOST`.
5. Run `scripts/deploy-firetv.sh` and accept the "Allow USB debugging" prompt on the TV the first time.

## Versioning

Builds without an explicit version get their version from git (`buildSrc/src/main/kotlin/GitVersionUtils.kt`):

- **versionName** comes from `git describe --tags --match 'v*'`, e.g. `0.20.0-beta.4-6-g930b668e4`. That is the upstream release
  plus the number of commits on top of it.
- **versionCode** comes from `git rev-list --count HEAD`.

Android only installs an update when its versionCode is equal to or higher than the installed one. The commit count
grows with every commit and every upstream merge, so new builds always install over old ones. This only holds if:

- the long-lived `custom` branch is **never rebased or force-pushed**. Merge upstream; don't rebase onto it.
- CI checks out the **full history** (`fetch-depth: 0`). A shallow clone gives a tiny commit count.
- you install from the newest branch. A build from an older or shorter branch has a lower versionCode and is
  rejected as a downgrade.

Setting `JELLYFIN_VERSION` or `-Pjellyfin.version` overrides the versionName. The versionCode still comes from git.

## Signing

The release build is signed with a personal key. The four standard properties are read from Gradle properties or the
environment, see `app/build.gradle.kts`. Locally they live in `~/.gradle/gradle.properties`, outside the repository:

```properties
keystore.file=/Users/<you>/.android/jellyfin-custom-release.jks
keystore.password=...
signing.key.alias=jellyfin-custom
signing.key.password=...
```

The key fingerprint is SHA-256 `E6:BC:A4:C2:AA:37:19:4E:0F:67:6D:4B:4F:99:29:93:C4:82:49:A8:07:27:6B:A4:D8:FC:D7:D8:65:44:C5:11`.

Without these properties the release APK is built **unsigned**, and the deploy script refuses to install it.

### Back up the key

**If the keystore or its password is lost, no future build can update the installed app.** The app would have to
be uninstalled, which deletes its settings and server login. Back up both, together:

1. Keystore: `~/.android/jellyfin-custom-release.jks`
2. Password: the `keystore.password` value from `~/.gradle/gradle.properties`

Store them in a password manager, which can hold the file as an attachment and the password as a field. Never commit
either of them.

### Restore on a new machine

1. Copy the keystore to `~/.android/jellyfin-custom-release.jks`.
2. Recreate `~/.gradle/gradle.properties` with the four properties above, then `chmod 600` it.
3. Check the fingerprint with
   `keytool -list -keystore ~/.android/jellyfin-custom-release.jks`. It must match the value above.

### CI

Store the keystore as a base64 secret and the passwords as secrets. Expose them as the environment variables
`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Upstream's `app-publish.yaml`
shows the pattern.
