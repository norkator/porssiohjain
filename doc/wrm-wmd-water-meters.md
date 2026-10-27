# Water meters / Vesimittarit

The authenticated Vaadin desktop and Start menu open `/water-meters`. Measurements
and Water meters appear directly after Dashboard. Accounts can add, edit,
disable and remove their own meters. Admins viewing as another user can manage
and poll that user's meters for support. Demo accounts cannot change or poll
meters. No new account-tier resource quota is introduced.

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
  --tests '*PushNotificationServiceTest' --tests '*MainLayoutTest' \
  --tests '*DesktopWindowNavigationTest'
```
