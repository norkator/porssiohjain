# Backend agent guidance

## Project

Pörssiohjain is a Spring Boot and Vaadin energy-usage optimization service. Backend Java is under `src/main/java/com/nitramite/porssiohjain`, Vaadin views are under `views`, Flyway migrations are under `src/main/resources/db/migration`, and tests are under `src/test/java`.

Use the repository Gradle wrapper with Java 21, for example:

```sh
./gradlew compileJava test
```

Preserve unrelated worktree changes. Use the existing Java, Spring, Vaadin, repository, translation, and Flyway conventions.

## Heating Planner handoff

Read `doc/heating-planner.md` before changing the Heating Planner. It is the authoritative feature specification and contains the staged persistence, safety, telemetry, push-notification, and active-control design.

Current implementation:

- `services/heating/HeatingPlanSimulationService.java` is a pure deterministic simulation/planning foundation.
- `views/HeatingPlannerView.java` is a Vaadin prototype at `/heating-planner`.
- `views/components/HeatingPlanChart.java` renders its ApexCharts visualization.
- `services/heating/HeatingPlanSimulationServiceTest.java` covers preheat, discharge, comfort, floor-limit, and wood-stove recommendation behaviour.
- The authenticated home view links to the prototype.

The current Heating Planner is deliberately mock-data and simulation-only. It must not send thermostat commands, persist room/device associations, or send wood-stove notifications yet. Do not present mock predictions as measured or active behaviour.

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

Wood cost is intentionally outside the model. Stove operation must remain human-controlled. Floor heating must be suppressed when predicted stove heat covers the room, subject to comfort recovery and safety limits.

Keep two separate configurable weather gates: a forecast temperature below which Heating Planner becomes active (for example +5 °C), and a forecast temperature below which wood may be recommended for the relevant expensive period (for example 0 °C). The UI must explain when either gate suppresses planning.

Before replacing mock data, first define normalized Zigbee temperature/humidity measurement history and persisted room-to-thermostat/sensor ownership. Do not use `DeviceEntity.lastTelemetry` as historical storage. Preserve the existing acknowledged/read-back Zigbee desired-state rules and the current thermostat price-curve controller as a fallback.

Focused verification:

```sh
./gradlew compileJava test --tests com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationServiceTest
```

The Android Zigbee gateway is maintained in the separate `energy-controller-android` repository. Read that repository's `AGENTS.md` before coordinating backend contracts with the native gateway.
