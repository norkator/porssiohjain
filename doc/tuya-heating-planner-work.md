# Tuya measurements and Heating Planner work

## Agreed scope

- Vaadin only: imported Tuya Smart sensors can be selected explicitly as room or floor sensors in Heating Planner.
- Poll enabled imported sensors every 30 minutes. Maintain My devices last communication and API Online with a one-hour contact timeout; Tuya MQTT is always Offline.
- Keep `zigbee_device_measurement` and its ingestion unchanged. Use the new singular `device_measurement` table for Tuya and future integrations. Do **not** dual-write Zigbee readings or backfill them into the new table.
- A common measurement read service adapts both stores for Heating Planner, including learning and recent-reading/history UI.
- Clean new measurement rows daily with the same one-month retention as Zigbee.
- Preserve explicit floor selection, stale/missing sensor guardrails, active-control opt-in, and the thermostat's independent floor limit.

## Checkpoint

Implementation and automated verification are complete. Deployment and live validation remain below.

Implemented:

- Flyway `V112__create_device_measurement.sql`, generic entity/type/repository.
- `DeviceMeasurementService` common read adapter; existing Zigbee storage remains authoritative for Zigbee.
- `TuyaScheduler` 30-minute polling and daily one-month retention cleanup.
- Tuya polling writes normalized temperature/humidity/battery readings, Celsius conversion, connectivity updates, and keeps report timestamps distinct from cloud fetch time.
- Heating Planner latest-reading, thermal learning, recent-reading/history UI, and widget summary use the common adapter. The history chart is now `DeviceMeasurementHistoryChart`.
- Imported Tuya temperature sensors use the existing explicit room/floor selectors. No controller opt-in or floor role is inferred from importing/selecting a room sensor.
- Timestamp-free online cloud snapshots are accepted, clearly labeled, with null `measuredAt`, poll-time `observedAt`, and one-hour freshness. Timestamped reports retain 60-minute freshness; both require successful cloud contact within one hour and online/enabled state. The shared Tuya freshness duration also controls API offline detection, so the generic ten-minute device timeout does not mark Tuya sensors offline between polls.
- My devices renders API status and last successful cloud communication, forces MQTT Offline, refreshes after manual polling/connection changes, and clears online flags when disabled/disconnected.
- The polling worker is separate from the shared control scheduler; overlapping passes are skipped.
- English/Finnish Tuya help and integration/planner/widget documentation are updated.

Validation:

- Java 21 `compileJava` and `compileTestJava` passed.
- Focused Tuya, Heating Planner services, common adapter, DeviceService, Zigbee sync, and Vaadin tests passed after correcting the new H2 migration check and a test fixture's missing timezone.
- The expanded final run additionally includes `HeatingPlannerControllerTest`, `PowerplantServiceTest`, and the separate-worker overlap test: **178 tests across 24 classes passed**, with no failures or skipped tests.
- `git diff --check` and local documentation link validation passed.
- The 30-minute polling follow-up passed Java 21 compilation and **189 tests across 23 classes** in two focused runs (178 service/controller/integration tests and 11 Vaadin tests). Regressions cover Tuya's one-hour contact timeout and dashboard status, independent reading/contact freshness, explicit floor input, and thermal learning from both 30-minute and hourly cloud snapshots. The planner keeps its 15-minute recalculation interval; fewer observations may delay model learning, and timestamped reports older than one hour still become stale.

Final verification command (Java 21):

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava test \
  --tests 'com.nitramite.porssiohjain.services.tuya.*' \
  --tests 'com.nitramite.porssiohjain.services.heating.*' \
  --tests com.nitramite.porssiohjain.services.DeviceMeasurementServiceTest \
  --tests com.nitramite.porssiohjain.DeviceServiceTest \
  --tests com.nitramite.porssiohjain.ZigbeeGatewaySyncServiceTest \
  --tests com.nitramite.porssiohjain.views.HeatingPlannerViewTest \
  --tests com.nitramite.porssiohjain.views.ListCreationViewsTest \
  --tests com.nitramite.porssiohjain.HeatingPlannerControllerTest \
  --tests com.nitramite.porssiohjain.services.PowerplantServiceTest --console=plain
```

Remaining live checks after deployment:

1. Apply V112 through the normal Flyway startup path; observe automatic observations over several 30-minute intervals and after restart.
2. Choose a Tuya room sensor and an actual floor sensor in Heating Planner; verify evidence labels, retained history and thermal-model inputs.
3. Verify offline/failed-poll/stale behavior, disabling/re-enabling devices, and disconnect/relink status. Confirm that the next successful poll restores availability.
4. Compare values/units with the Tuya app and observe a real sensor change; validate token refresh after expiry. The previous manual proof of concept returned 20.9 °C, 51% humidity and 100% battery.

The starting worktree was clean. Current changes belong to this task. No commit, live cloud request, production app, or production database migration has been run for this work.

If Gradle fails with sandbox network/wildcard-IP restrictions, retry the wrapper command with the tool's required escalation rather than changing production settings.
