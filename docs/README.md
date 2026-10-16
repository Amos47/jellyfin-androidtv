# Jellyfin Custom: personal fork documentation

This branch (`custom`) is a long-lived personal fork of
[jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv), built for an Amazon Fire TV Stick. It installs
as **Jellyfin Custom** (`org.jellyfin.androidtv.custom`), next to the official app. It works with unmodified
Jellyfin servers.

The aim is a home media experience the whole household can use: open the app, find the show you're watching, press
play, and continue from the right episode, including when rewatching a series.

| Document | Contents |
|---|---|
| [features.md](features.md) | **Feature tracker** (authoritative): status, locations, tests, upstream issues |
| [development.md](development.md) | Toolchain, build/test/deploy commands, Fire TV setup, versioning, signing key backup |
| [architecture.md](architecture.md) | Where things live in the code, and where the fork hooks in |
| [rewatching.md](rewatching.md) | How Next Up and rewatching work (client and server) |
| [artwork-fallbacks.md](artwork-fallbacks.md) | Episode card image selection and load-failure fallback |
| [home-rows.md](home-rows.md) | Combined Continue Watching, New Releases, and settings refresh |
| [upstream-sync.md](upstream-sync.md) | Merging new upstream releases into `custom` |
| [release-process.md](release-process.md) | Building, versioning and installing releases |

## Principles

- Keep changes small and isolated. Put new code in new files where practical, so upstream merges rarely conflict.
- Use existing Jellyfin server APIs. No server fork and no custom server state.
- Behave consistently across screens, and keep existing settings compatible.
- Only ever **merge** upstream into `custom`. Never rebase or force-push it: version codes come from the commit count
  (see [development.md](development.md#versioning)).
- Local working notes for agents and sessions are kept in the gitignored `.fork-state/` folder.
