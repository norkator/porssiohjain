# Backend agent guidance

## Project

Pörssiohjain is a Spring Boot and Vaadin energy-usage optimization service. Backend Java is under `src/main/java/com/nitramite/porssiohjain`, Vaadin views are under `views`, Flyway migrations are under `src/main/resources/db/migration`, and tests are under `src/test/java`.

Use the repository Gradle wrapper with Java 21, for example:

```sh
./gradlew compileJava test
```

Preserve unrelated worktree changes. Use the existing Java, Spring, Vaadin, repository, translation, and Flyway conventions.

## Control Table handoff

`ControlSchedulerService` generates persisted `FINAL` control-table rows for price-based controls. `CHEAPEST_HOURS` retains its strict per-local-calendar-day quota. `CHEAPEST_HOURS_TOMORROW_AWARE` may reduce today's quota only from tomorrow's guaranteed below-minimum-price surplus:

```text
tomorrow surplus = max(0, tomorrow below-min minutes - daily on minutes)
today requirement = max(0, daily on minutes - tomorrow surplus)
```

Tomorrow-aware borrowing requires `alwaysOnBelowMinPrice` and complete, gap-free tomorrow prices. If either condition is absent, use the strict daily calculation. Tomorrow's first `dailyOnMinutes` belong to tomorrow and must not be double-counted as today's surplus. Existing `CHEAPEST_HOURS` semantics must not change when extending the flexible mode.

The scheduler ranks and thresholds with the combined control price from `ControlPriceService`: Nord Pool price with the control tax plus the applicable static or day/night transfer price. A chart's Nord Pool series alone may therefore differ from the price used for scheduling.

All scheduler, repository, REST chart, and Vaadin chart day boundaries must use the control's configured `ZoneId`. Use local dates converted with `date.atStartOfDay(zone).toInstant()` and half-open ranges such as `[todayStart, tomorrowStart)`. Do not use `Instant.truncatedTo(DAYS)`, UTC midnight, or fixed 24-hour additions for control days. This is required for non-UTC zones and 23/25-hour DST days; Helsinki midnight is 21:00 UTC in summer and 22:00 UTC in winter. Include control-table rows starting exactly at local midnight.

Nord Pool `deliveryStart` and `deliveryEnd` values are authoritative instants and may cross UTC dates for a local market day. Query them using the converted local-day boundaries; do not manually add or subtract the timezone offset from stored timestamps.

`ControlTableView` exposes the control timezone. Saving control settings must persist the timezone, regenerate the control table, and rerender charts. Keep today and tomorrow price queries aligned with the same control-local boundaries used by the scheduler.

Focused verification:

```sh
./gradlew test --tests com.nitramite.porssiohjain.services.ControlSchedulerServiceTest --tests com.nitramite.porssiohjain.services.NordpoolServiceTest --tests com.nitramite.porssiohjain.ControlServiceTest
```

## Powerplant handoff

Read `doc/powerplant.md` before changing the Powerplant feature. It documents the current free-form control-room board, persisted element model, Vaadin icon selection, direct STANDARD relay command support, and the distinction between configured indicator values and live telemetry.

## Solar panels handoff

The solar panel angle feature is currently an advisory/manual planning tool with a device polling API, not a persisted automation system.

Current implementation:

- `services/solar/SolarAnglePlannerService.java` calculates sun elevation/azimuth, target tilt/azimuth, movement deltas, directions, tolerance handling, daylight state, and next suggested check time.
- `services/models/SolarAngleRecommendationResponse.java` is the shared response model used by both Vaadin and REST.
- `views/SolarAnglePlannerView.java` provides the Vaadin view at `/solar-angle-planner`.
- `contollers/SolarAnglePlannerController.java` exposes `GET /api/solar-angle-planner/recommendation` for devices that periodically ask for movement guidance.

The Finnish visible feature name is **Aurinkopaneelit**. Keep shorter navigation labels preferred over long names such as `Aurinkopaneelin kulmasuunnittelu`.

The Vaadin view intentionally separates the two physical angles:

- top-down azimuth view: a 360-degree dial with current direction, target direction, sun marker, and cardinal/degree labels;
- side-view tilt view: current panel tilt and target panel tilt only.

Keep the form as a narrow left column and the visualizations as the wider right-side content on desktop, wrapping naturally on mobile.

For future automation, keep motor safety, calibration, limit switches, and physical movement execution on the device side. The backend should provide target angle and movement direction recommendations; it should not assume the panel can move safely unless a later persisted device capability model explicitly supports that.

## Heating Planner handoff

Read `doc/heating-planner.md` before changing the Heating Planner. It is the authoritative feature specification and contains the persistence, thermostat-limit, telemetry, push-notification, and active-control design.

Current implementation:

- `services/heating/HeatingPlanSimulationService.java` is the pure deterministic simulation/planning foundation.
- `views/HeatingPlannerView.java` provides configuration, planning, evidence, and active-control opt-in at `/heating-planner`.
- `views/components/HeatingPlanChart.java` renders its ApexCharts visualization.
- `entity/ZigbeeDeviceMeasurementEntity.java`, `entity/enums/ZigbeeMeasurementType.java`, and `entity/repository/ZigbeeDeviceMeasurementRepository.java` define normalized Zigbee measurement history for Heating Planner sensor use.
- `services/heating/HeatingPlannerMeasurementService.java` exposes latest fresh/stale/missing room and floor temperature lookup.
- `services/heating/HeatingPlanSimulationServiceTest.java` covers preheat, discharge, comfort, floor-limit, and wood-stove recommendation behaviour.
- Persisted plans, automatic replanning, wood-stove advisory notifications, and explicit opt-in thermostat control are implemented.

Heating Planner predictions must still be presented as predictions rather than measurements. Active plan state and acknowledged/read-back thermostat state must be shown distinctly.

The thermostat's configured internal floor-temperature limit is authoritative and remains enforced by the thermostat independently of Heating Planner. Planner floor-temperature bounds and freshness checks are additional optimization guardrails; documentation should not imply that Heating Planner is the thermostat's only protection against excessive floor temperature.

The product name is **Heating Planner**. Avoid using `Thermal Storage` as the feature name; thermal storage is an internal technique used by the planner.

The intended user-visible scope is the plan for today and tomorrow, plus the exact inputs and reasons used to determine that plan. It coordinates:

- automatic floor-heating preheat/discharge planning;
- market prices;
- outdoor temperature and wind forecast;
- per-room comfort limits;
- floor and room temperature measurements;
- an optional heat-retaining wood stove with user-configured load amount, delay, release duration, and declining heat effect;
- an advisory push telling the user when to light the stove.

Heat-pump optimization is explicitly outside this feature's scope. Existing heat pumps control themselves. Their effect is observed indirectly through room-temperature measurements and may reduce the need for floor preheating or a wood recommendation; Heating Planner must not issue heat-pump commands.

Wood cost is intentionally outside the model. Stove operation must remain human-controlled. Floor heating must be suppressed when predicted stove heat covers the room, subject to comfort recovery and configured planner bounds.

Keep two separate configurable weather gates: a forecast temperature below which Heating Planner becomes active (for example +5 °C), and a forecast temperature below which wood may be recommended for the relevant expensive period (for example 0 °C). The UI must explain when either gate suppresses planning.

Before replacing mock data, first define normalized Zigbee temperature/humidity measurement history and persisted room-to-thermostat/sensor ownership. Do not use `DeviceEntity.lastTelemetry` as historical storage. Preserve the existing acknowledged/read-back Zigbee desired-state rules and the current thermostat price-curve controller as a fallback.

Next Heating Planner steps:

1. Wire `HeatingPlannerMeasurementService` into the planner calculation and UI evidence panel. Replace static `21 C` room temperature only when a selected room sensor has a fresh reading; clearly show fresh, stale, and missing states.
2. Add explicit floor sensor selection per room/heat source. Do not silently treat room temperature as floor temperature. Missing or stale floor temperature must disable preheating.
3. Generate one whole-house plan containing per-room plan points for today and tomorrow. Persist it into `heating_planner_plan` and `heating_planner_plan_point`, superseding older active/simulated plans.
4. Keep the chart whole-house oriented, but add room filtering or room series visibility because each room has independent comfort targets, sensors, heat sources, and controller device.
5. Add dry-run monitoring: compare predicted room/floor temperatures with measured values, record model error, and show warnings/fallbacks before any active control is allowed.
6. Add the advisory wood-stove push workflow after persisted plans exist. Recommendations require the one-shot `stove_loaded` state, availability window, weather gate, and expensive-period reason. User actions should include `lit now`, `skip`, and actual lighting time; `lit now` consumes loaded state.
7. Active thermostat control uses an explicit opt-in separate from the master planner toggle. Preserve ownership, sensor freshness, planner temperature bounds, comfort minimum, command rate limits, desired-state expiry, Zigbee acknowledgement/readback, and fallback to the existing thermostat price-curve controller.

Recent cross-repository gateway contract work:

- Backend sync now accepts sensor telemetry fields `temperature`, `humidity`, `batteryPercentage`, and `measuredAt` in `ZigbeeGatewaySyncRequest.DeviceReport`.
- Non-thermostat Zigbee reports with measurements are registered as `DeviceType.TEMPERATURE_SENSOR` and stored in `zigbee_device_measurement`.
- The Android gateway repository at `/home/norkator/Documents/GitHub/energy-controller-android` was updated so `ZigbeeGatewayService` reads supported sensor profiles and includes those fields in cloud sync payloads.
- Before further Android gateway changes, read `/home/norkator/Documents/GitHub/energy-controller-android/AGENTS.md`.

Focused verification:

```sh
./gradlew compileJava test --tests com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationServiceTest
./gradlew test --tests com.nitramite.porssiohjain.ZigbeeGatewaySyncServiceTest --tests com.nitramite.porssiohjain.services.heating.HeatingPlannerMeasurementServiceTest --tests com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationServiceTest
```

The Android Zigbee gateway is maintained in the separate `energy-controller-android` repository. Read that repository's `AGENTS.md` before coordinating backend contracts with the native gateway.
