# Native measurement widgets

The Android app has separate latest-value and history-graph widgets. Each widget
selects a device ID, `DeviceMeasurementType` and exact measurement key from the
same retained readings as Vaadin Measurements. Nothing needs to be published.

`GET /api/measurements/latest` returns an array of explicit reading DTOs:
`deviceId`, `deviceName`, `measurementType`, `measurementKey`, `source`,
`sourceKey`, `unit`, `value`, `observedAt`, `measuredAt`, `receivedAt`.
Silent sensors remain selectable; `measuredAt` stays null for cloud snapshots.
No entity or credential is serialized. Current device ownership is checked even
for retained rows created before an ownership change.

`GET /api/measurements/history?deviceId=12&type=TEMPERATURE&key=temperature&hours=48`
accepts 24 (default) or 48 elapsed hours. The response contains `deviceId`,
`measurementType`, `measurementKey`, `from`, `to`, `latest` (nullable), and `points`
(`observedAt`, `value`). The half-open `[from,to)` history uses the common
account-scoped service across both measurement stores without the private
dialog's newest-500 limit. A retained latest reading can precede the graph window.
Unknown properties or empty history return empty data, never another property.
Deleted/foreign devices return the same 410 status. Malformed selections return
400. Both endpoints use `@RequireAuth` and the normal raw `Authorization` token.

Android polls with its existing quarter-hour widget job and context-authenticated
ApiClient token renewal. Cached responses are scoped to the login, environment,
widget ID and configuration revision; logout/new login/environment changes clear
selections and data. A failed refresh retains the last snapshot with an offline
label. Phone timestamps are presentation only; all window boundaries are UTC
instants, so rolling periods stay exactly 24/48 hours across DST.

Deploy these backend endpoints before distributing the Android widget build.
No Vaadin, database migration, or hybrid-web change is required.

Verification: `./gradlew compileJava test --tests '*MeasurementsControllerTest'
--tests '*MeasurementWidgetQueryServiceTest' --tests '*DeviceMeasurementServiceTest'
--tests '*PublicMeasurementPersistenceTest'`.
