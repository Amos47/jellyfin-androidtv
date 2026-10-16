# Rewatching

## Client

Settings → Home → **Enable rewatching in Next Up** (`UserPreferences.nextUpRewatchingEnabled`, off by default) sets
`enableRewatching` on every Next Up request through `GetNextUpRequest.withUserPreferences()`.

Why a separate setting is needed:

- The Jellyfin SDK sends `enableRewatching=false` unless told otherwise.
- The web client's "Enable Rewatching in Next Up" is stored only in the browser, so it can't be read by this app
  ([#4586](https://github.com/jellyfin/jellyfin-androidtv/issues/4586)).

## Server behavior

These notes are from `TVSeriesManager.GetNextUpBatched` and `NextUpService` in the Jellyfin server.

- Series are considered in order of their most recent play date. The `nextUpDateCutoff` setting drops series not
  played since that date.
- For each series the server can return:
  - **Next up:** the first unwatched episode after the last *watched* one, in aired order. With
    `enableResumable=false`, an episode that has playback progress is skipped and nothing is returned for that series.
  - **Rewatch** (only when `enableRewatching=true`): the episode after the most recently *played* episode among those
    marked watched. This one ignores watched state, and in-progress episodes are always skipped.
- Results are sorted by last-played date, newest first.
- Specials are interleaved if the server option "Display specials within seasons" is on.

## Consequences

- Starting to rewatch S04E08 (already marked watched) makes S04E08 the most recently played episode, so Next Up
  shows **S04E09** while S04E08 is in progress. The in-progress episode only appears in Continue Watching. The
  combined **Continue watching** section ([home-rows.md](home-rows.md)) solves this by showing S04E08.
- A series can appear twice: once for a newly released unwatched episode, and once for the rewatch position. The web
  client does the same.
- Watched state and position are per user on the server, so the iPhone, web and Fire TV stay in sync. No local
  rewatch state is kept.
