# Grenoble discovery seed

`python manage.py seed_discovery` loads three French/English walking and cultural
entries, photos for the 15 known ski resorts, and direct provider camera viewers.
It runs after migrations in the Docker entrypoint and GitHub Actions deployment.
The initial station loader also uses these bundled photos and camera viewers.
No network access is required during seeding. Repeating the command does not
create duplicate places or cameras. Existing place copy and credited station
photos are preserved; camera URLs are maintained by their seeded station/name.

`photos.json` records the author, license and Wikimedia Commons source for each
JPEG. Images are resized versions of the source photographs, with the same
license. The app displays clickable attribution. Source pages provide the
original image and license terms. These are destination photographs, not current
weather images. `cameras.json` records the official resort page that links to each
provider viewer; viewer links were checked on 2026-09-08. Camera availability and
provider refresh delays can change independently of deployment.

`places.json` contains original bilingual summaries and walking suggestions based
on the linked Grenoble tourism, municipal and Isère museum pages. Opening times
and access conditions are intentionally left to the official visit links.

## Conditions

`/api/station-weather/` uses the configured `WEATHER_API_KEY` for OpenWeather,
caches results for ten minutes, and returns the provider observation timestamp.
Missing credentials or provider failures return no temperature, never a seeded
weather value. Weather requests do not update operator snow/opening reports.

`ski_assessment` on stations uses `StationLiveStatus` reports no older than six
hours: closures, incomplete data and temperature/snow concerns are distinguished.
Even with open pistes, wind, visibility and snow quality must be checked in the
operator bulletin; temperature alone cannot establish ideal skiing conditions.

## Validation and release

Run Django checks, migration drift checks and tests before pushing. CI validates
migration 0049, applies the seed, and deploy explicitly runs `migrate`,
`seed_discovery`, then `migrate --check`. Android pushes still build signed APK/AAB
artifacts. Google Play production publication requires a manual workflow run
with `publish_to_play` enabled.


## Cultural news

Migration 0048 registers the Musée de Grenoble, MC2 and La Casemate official RSS
feeds, checked on 2026-09-08. `fetch_culture_rss` imports short excerpts, publisher
links, optional enclosure images, original languages and publication dates.
Missing or invalid dates are skipped; publication dates are not event dates.
The API exposes them through `/api/ski-news/?category=culture`; existing ski-news
requests remain ski-only. English UI users can read the original French articles,
which are explicitly labelled rather than presented as translated content.

Deployment refreshes the feeds once; `refresh_culture.yml` then runs every four
hours (GitHub scheduled jobs can be delayed). Each feed has its own sync timestamp
and error in Django admin. Failures preserve existing articles; no sample cultural
stories are fabricated. Only healthy sources prune their own articles older than
180 days. Ski RSS pruning cannot delete cultural articles.
