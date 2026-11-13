# Water meters / Vesimittarit

The authenticated Vaadin desktop and Start menu open `/water-meters`. Measurements
and Water meters appear directly after Dashboard. Accounts can add, edit,
disable and remove their own meters. Admins viewing as another user can manage
and poll that user's meters for support, including demo accounts. Direct demo
logins cannot change or poll meters. No new account-tier resource quota is introduced.

## Providers and daily polling

Each meter has an explicit `WaterMeterProvider` and a portal address supplied by
the user. The initial adapter is **WRM Systems WMD**. `WaterMeterClient` owns portal
validation, authentication and normalization to a cumulative cubic-metre reading.
Future companies using that system can supply their own tenant address; other
APIs can add an enum value and a corresponding adapter without replacing the
history or notification model. Adapters must restrict credential delivery to
their provider's trusted endpoints.

The current WRM adapter supports payer/customer-number and meter-number login.
It fetches the configured tenant's login page, retains the cookies and CSRF
value, posts `/<tenant>/login`, then reads `GET /cards/watermeter-info`. The form
fields are `_csrf`, `mode=water`, `login-by=ul`, `login-input-a`, `login-input-b`.
Each invocation has an isolated cookie jar. Only the fixed HTTPS
`wmd.wrm-systems.fi` origin is accepted; redirects are never followed with
credentials. The parser selects `Viimeisin lukema`, accepts decimal commas and
superscript cubic-metre units, and rejects missing or ambiguous readings.
Different portal authentication modes need their own adapter support.

Automatic reads run daily at 12:00 Europe/Helsinki. `WATER_METERS_POLL_CRON`
overrides the Spring cron expression; `-` disables scheduled polling. The UI
describes the default schedule. Enabled meters get at most one automatic attempt
per Helsinki calendar day, including failures. **Read now / Lue nyt** is an
explicit extra read or retry. Both login fields are encrypted with the existing
`CryptoConverter` and `APP_CRYPTO_KEY`, and are absent from view DTOs. Blank
credential fields on edit preserve saved values.

## Measurements and graph

V120 creates `water_meter`; V121 adds `water_measurement`, the provider discriminator
and push settings. V121 renames the encrypted credential columns to `username`
and `password` without changing their ciphertext. It leaves the existing latest
snapshot intact but does not backfill history. History starts with successful
reads after the migration.

Every successful automatic or manual read appends a history point and updates
`water_meter.latest_total_cubic_meters` in the same transaction. Repeated equal
totals are retained as distinct successful observations. Failed polls leave both
history and the previous successful total/fetch time unchanged. The chart and
table behind **Graph / Kuvaaja** query only the selected, account-owned meter's
history in chronological order, without a 500-row cap. Graph access performs no
provider request. Deleting a meter or its account cascades to its history.

Values are cumulative totals, not daily consumption. The current WRM card does
not supply a measurement timestamp: `observedAt` means fetch time. Changing
provider/portal/credentials clears the latest snapshot but retains existing
history. Add a separate meter entry when tracking another physical meter.

## Scheduled push notifications

Each meter has an independent push-enabled toggle, Daily/Weekly frequency,
notification time (whole minutes), timezone and a weekly weekday (Monday by
default). Push is disabled by default; the initial time is 12:05 Europe/Helsinki.
The scheduler checks every minute and delivers only during the selected minute.
It does not catch up missed times. Missing spring DST times move forward through
`atZone`; repeated autumn times produce only one delivery. A persisted successful
send time plus a meter row lock suppresses duplicates per local day or ISO week.
Editing a schedule does not clear this delivery marker.

Notifications report exactly the latest cached `latest_total_cubic_meters` and
its original fetch time, even if the latest provider poll failed. They do not
poll the provider, invent a fresh measurement or calculate consumption. Meters
without a saved reading or disabled meters send nothing. Existing account push
opt-in, active push tokens, demo restrictions and weekly account notification
limits apply. Delivery uses the existing `PushNotificationService`, with
localized English/Finnish text and `WATER_METER_NOTIFICATION` data. Failed
FCM delivery is not marked as sent. No email delivery is added.

Repository-root `wrm.json` is Git-ignored and used only for local investigation;
the application does not load it. Keep real credentials and sessions out of
source files, fixtures and logs.

```sh
./gradlew compileJava test --tests '*WaterMeter*' --tests '*WrmWmdServiceTest' \
  --tests '*TampereenVesiServiceTest' --tests '*PushNotificationServiceTest' --tests '*MainLayoutTest' \
  --tests '*DesktopWindowNavigationTest'
```

## Tampereen Vesi

`TAMPEREEN_VESI` uses the fixed HTTPS Tampereen Vesi Online portal. Configure
its username/password (encrypted like WRM credentials), metering-point code,
starting reading in m³, and the date **at whose start (00:00 Helsinki time)**
that reading applies. Do not enter a midday reading as a midnight baseline.

The adapter creates an isolated cookie jar, retrieves the login CSRF input,
posts `UserName`, `Password`, and `__RequestVerificationToken` to
`/eServices/Online/Login`, follows only same-origin redirects, and extracts a
fresh form token. It posts `mpCode`, empty `mpSourceCompanyCode`, `startDate`
(ISO baseline date), and the token to
`/Reporting/SessionlessConsumption/GetMpConsumptionModel`. Cookie handling is
automatic; browser cookie values are neither configured nor persisted. The
exact authenticated redirect/token and startDate behavior still needs live
verification; the implementation does not use captured sessions as fixtures.

The captured response supplies `Data.Days.Consumption.Data` timestamp/value
pairs in m³, not a current meter reading. Only complete Helsinki calendar days
from the baseline through yesterday are eligible. Null/missing days are never
assumed to be zero. Dated usage is retained in `water_measurement.consumption_date` and
`consumption_cubic_meters`, with one row per meter/day. Refreshed values replace prior values, allowing corrections and avoiding
duplicate increments on manual retries. The calculated total advances only
through a contiguous sequence of available days; the UI shows the last included
date. Missing baseline-day data fails the poll and preserves the previous
snapshot. If the provider's returned history cannot cover an old baseline,
choose a baseline within available data rather than silently skipping days.
The captured sample's newest day was two days behind its fetch date.

V125 adds the baseline, metering-point code, dated usage columns and coverage date.
Daily rows retain their fetch time separately from their consumption date. Rows
beyond a missing day retain usage with a null calculated total until the gap is
filled; graph/history totals omit these pending rows. The graph places calculated
daily totals at the end of their consumption day.
Changing source or baseline clears this calculation state and snapshot while
retaining existing history. Totals and push messages explicitly describe
Tampereen Vesi values as calculated. The existing noon polling schedule remains
in use; daily data availability, rather than local midnight, determines when
the calculated total can advance. Investigation captures containing session
cookies or personal data must not be committed.

## Android measurement widgets

Water-meter values have a separate authenticated, read-only widget API:

- `GET /api/measurements/water-meters/latest`
- `GET /api/measurements/water-meters/history?waterMeterId=...&type=WATER_METER_TOTAL&key=total_m3&hours=24`
- `GET /api/measurements/water-meters/history?waterMeterId=...&type=WATER_CONSUMPTION&key=daily_m3&hours=48`

The original `/api/measurements/latest` and `/history` endpoints, common device
measurement enum, storage, Measurements view and public sharing remain unchanged.
`WaterMeterMeasurementsController` and `WaterMeterWidgetQueryService` expose only
owned meters and account-scoped stored history; they never contact the provider.
History rejects invalid type/key combinations or periods other than 24/48 hours
with 400, and unowned/deleted meters with 410. Authentication uses the same raw
account token as existing measurement widgets. Silent/disabled meters retain
cached values; unpolled meters have no fabricated zero reading.

Responses use the existing widget value/history shape: `deviceId` (the water
meter ID in this separate namespace), `deviceName`, `measurementType`,
`measurementKey`, `source=WATER_METER`, provider `sourceKey`, `unit=m³`, `value`,
`observedAt`, null `measuredAt`, and `receivedAt`. Additional `calculated` and
`consumptionDate` distinguish baseline-derived totals and the last covered/usage
day from fetch time. Total series are `WATER_METER_TOTAL/total_m3`; dated daily
usage is `WATER_CONSUMPTION/daily_m3`. WRM does not synthesize a daily series.
Meter IDs can overlap device IDs because type/key are also part of identity.
Credentials, portal sessions and account entities are excluded from all payloads.

History is the full half-open rolling [from,to) window with no 500-point cap.
WRM totals use observation times. Dated usage and calculated totals are positioned
at the end of the Helsinki consumption day, using local dates and ZoneId so
23/25-hour DST days remain correct. Daily usage can be shown while a missing
prior day prevents calculation of a cumulative total; null totals are omitted.
The latest response always preserves the original fetch time. The Android app
merges water choices into its widget picker, routes water history to the new
endpoint, and leaves existing device widget refresh requests unchanged. Existing
session/environment isolation, raw-token renewal, cached-offline handling and
24/48-hour selections are reused. A new backend and Android build are required;
no installed phone is updated automatically.
