# Measurements and public sharing

The authenticated Vaadin Measurements view (`/measurements`, desktop and Start menu)
groups the latest retained readings by measurement type. It reads both
`zigbee_device_measurement` and `device_measurement` through `DeviceMeasurementService`.
Private history dialogs show the newest 500 values for the selected device/type/key.

Latest-reading lists select one ID per series using PostgreSQL `DISTINCT ON` and
the account/series/time indexes added by V115, then fetch those readings and their
devices together. They do not compare every retained history row with newer rows
or load history into the view. Timestamp ties prefer the higher row ID. The
Measurements view still includes silent sensors with retained readings; no recent-time
cutoff or history truncation is used to accelerate the list.

Select **Public** beside a measurement to add that device/type/key to the account's
share page. Choices are saved immediately. The sharing panel provides the page link,
a copy button, and removal controls that remain available when a sensor has no retained
readings. Demo accounts cannot change publishing choices. Admins viewing as another
user can manage that user's publishing choices for support.

Flyway V114 adds nullable `account.public_measurements_uuid` and the
`public_measurement` selection table. The first publication generates a random page
identifier, separate from account UUIDs and authentication credentials. The link remains
stable when measurements are added or removed. Account/device deletion cascades to
selections. Existing accounts start with no public measurements.

`PublicMeasurementsView` is an anonymous, standalone Vaadin route outside `MainLayout`:

- `/public-measurements/{pageId}` shows selected measurements in a compact responsive
  grid, with 200px overview charts covering the last 24 elapsed hours. Narrow screens
  use a single column; wider screens fit multiple charts per row.
- `/public-measurements/{pageId}/{selectionId}` opens only that published measurement,
  defaulting to seven elapsed days and offering 24-hour and three-day ranges.

Each request checks that the account is unblocked, the selection belongs to that account,
and the device is still owned by that account. Removing a selection makes its detail link
unavailable on subsequent loads/refreshes. Empty account pages remain accessible through
the same link and show no measurements. Public payloads contain selected device names,
measurement metadata, and timestamp/value pairs; they contain no account credentials.

Public history uses account-scoped half-open `[from, to)` queries with a maximum
seven-day window, without the private dialog's 500-row cap. Charts use the device's
configured timezone (Helsinki fallback for an invalid legacy value), and rolling durations
remain exactly 24/72/168 hours across DST. Cloud snapshots use `observedAt`, retain unknown
sensor report times, and display the snapshot explanation. Zigbee history is never copied
into `device_measurement`.

Focused tests: `PublicMeasurementServiceTest`, `PublicMeasurementPersistenceTest`,
`MeasurementsViewTest`, `PublicMeasurementsViewTest`, and `DeviceMeasurementServiceTest`.
