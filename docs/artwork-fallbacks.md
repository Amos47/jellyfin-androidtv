# Episode artwork fallbacks

Landscape episode cards (Next Up, Continue Watching, season browsing, and anything else rendered by `CardPresenter`)
pick their image from `BaseItemDto.getEpisodeLandscapeImages(preferParentThumb)` in
`util/apiclient/EpisodeImages.kt`.

## Order

| # | Candidate | Source field |
|---|---|---|
| 1 | Episode image | `imageTags[Primary]`. Jellyfin stores episode stills as the primary image. |
| 2 | Closest parent backdrop: season, else series | `ParentBackdropItemId` / `ParentBackdropImageTags` |
| 3 | Parent or series thumb | `ParentThumbItemId` / `ParentThumbImageTag`, else `SeriesThumbImageTag` |
| 4 | Series poster, center-cropped | `SeriesPrimaryImageTag` |
| 5 | Icon placeholder | |

- With **Prefer series thumbnails** on (Settings → Customization), the thumb moves to first place. The rest of the
  order is unchanged.
- The server fills in every one of these fields for episodes in normal list responses
  (`DtoService.AddInheritedImages`). Falling back therefore needs **no extra requests**, which avoids N+1 requests on
  large season grids.

## Load failures

`CardPresenter` passes the remaining candidates to `AsyncImage` / `AsyncImageView.load(fallbackUrls = …)`:

- An HTTP error (404, 500) or an image that can't be decoded moves on to the next candidate.
- A network failure (`IOException`: offline, timeout) stops immediately, so an unreachable server doesn't trigger
  extra requests. See `Throwable.allowsImageFallback()`.
- Each candidate is tried at most once per bind. Requests are resized to the card size, and series images are shared
  between episodes, so they are usually cached already.

## Limitations

- Details screens (`FullDetailsFragment`, `ItemListFragment`) use `util/ImageHelper`, which doesn't have this chain
  yet.
- Failed loads aren't cached by Coil, so scrolling back to a broken card retries its first URL.
- The blurhash shown while loading belongs to the first candidate.
