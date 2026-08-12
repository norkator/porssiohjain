# Heating Planner

## Status

Design specification, live planning, and explicit opt-in active control. Sensor freshness, safety limits, Zigbee acknowledgement/readback, command expiry/rate limiting, and fallback behaviour are enforced in software. Site-specific floor limits and the learned thermal model still require careful real-building validation by the operator.

## Goal

Coordinate two forms of stored heat:

- an electrically heated floor, which the service can charge automatically during inexpensive hours; and
- a heat-retaining wood stove, which the user charges manually after receiving a push notification.

The service heats the floor during inexpensive hours before an expensive period, then reduces or stops electrical heating while stored heat continues to warm the room. When a configured wood burn can cover an expensive period without violating comfort limits, the service recommends when to light the stove and accounts for its predicted heat release when planning the floor.

The controller optimizes future conditions rather than mapping only the current electricity price to a thermostat setpoint.

## Vaadin concept

Add a `Thermal storage` view in two stages.

### Simulation stage

The view creates an unsaved scenario containing:

- room name;
- floor-heating thermostat;
- room temperature/humidity sensor;
- normal and maximum preheat floor temperatures;
- minimum and desired room temperatures;
- initial floor and room temperatures;
- estimated heating and cooling parameters;
- optional wood-stove load profile and notification availability;
- price and outdoor-weather horizon.

The view is deliberately limited to the operational plan for **today and tomorrow**. It is not a long-term analytics dashboard.

The result is a shared time-series chart containing electricity price, outdoor temperature, floor temperature, room temperature, thermostat setpoint, electrical heating state, predicted wood-stove heat release, and recommended stove-lighting time. It must also display an explanation for every change of operating mode.

Beside the chart, show exactly what produced the current plan:

- plan creation time and covered interval;
- market-price publication/fetch time and today/tomorrow price series;
- weather forecast fetch time, outdoor temperature, and wind series;
- latest floor and room measurements with their timestamps and freshness state;
- room comfort minimum, target, and maximum;
- normal, preheat-maximum, and absolute-maximum floor temperatures;
- selected wood load, amount, release delay, release duration, and heat-response estimate;
- planner/model version and whether parameters are configured estimates or learned values;
- warnings, fallbacks, or unavailable inputs that affected the plan.

Every planned action must be selectable in the chart or timeline and expose its reason, for example: `preheat from 03:15 because 07:00-10:00 is expensive and -15 C is forecast`, or `notify at 16:15 so the normal wood load begins releasing heat at 17:00`.

### Monitoring stage

Once telemetry persistence exists, the same view becomes the room configuration and monitoring view. It compares planned, simulated, and measured temperatures and shows model error. Configuration is organized as a house/site containing heat zones. A zone normally links one room sensor and zero or more heat sources. Kitchen, shower, toilet, and entrance can each have an independently controlled floor thermostat. The living-room zone can contain the heat-retaining wood stove without floor heating. Other configured rooms may receive a reduced share of stove heat.

A separately controlled heat pump is outside Heating Planner scope. It continues using its own thermostat. Its heating effect is naturally visible in room-temperature measurements, so the planner responds by reducing unnecessary floor preheating or wood recommendations without issuing heat-pump commands.

## Planning inputs

The planner deliberately uses only:

- market electricity price;
- outdoor temperature and wind forecast;
- measured and desired room temperatures;
- floor temperature and configured floor limits;
- configured thermal response of the floor, room, and wood stove.

Wood price is outside the scope. A wood burn is preferred when it can replace electric heating during a sufficiently expensive period while keeping rooms within their comfort ranges.

Two independent user-defined weather gates apply:

- **Planner active below:** Heating Planner performs floor preheating and wood planning only when at least one forecast point in the today/tomorrow horizon is below this outdoor temperature, for example +5 °C. Above it, existing heating controls continue normally and the optimization plan is visibly inactive.
- **Recommend wood below:** a wood recommendation is permitted only when forecast outdoor temperature at the expensive period being covered is below this threshold, for example 0 °C.

Both comparisons are strict `below` comparisons. The view must show the configured thresholds, the forecast values used, and the reason when either gate suppresses planning.

## Sensor roles

Floor and room measurements are not interchangeable:

- floor temperature estimates stored thermal energy and enforces the floor safety limit;
- room temperature protects occupant comfort;
- room humidity is monitoring context and may later identify shower/drying periods, but does not directly request heating in the first version;
- outdoor temperature and wind forecast estimate future heat loss.

The UI must require the user to state which measurement is the floor measurement. Device/model defaults may assist but must not silently infer this for unknown devices.

## Planning horizon

Use a today-and-tomorrow horizon in the site's timezone with 15-minute simulation steps. Tomorrow remains visibly unavailable until both tomorrow's market prices and sufficient weather forecast data exist. Replan when any of these occurs:

- new day-ahead prices arrive;
- the weather forecast changes materially;
- a fresh floor or room observation differs materially from the prediction;
- the user changes room settings;
- a site power limit constrains heating.

The deterministic planner identifies expensive periods across the complete today-and-tomorrow horizon. For each expensive block it estimates forecast room heat loss from the indoor/outdoor temperature difference and wind, converts the required reserve into floor-heating steps, and selects sufficient preceding cheap points. It prefers lower prices and uses later points as the tie-breaker to reduce storage loss. There is no fixed preheat look-ahead. During selected cheap periods it raises the floor setpoint up to the configured preheat maximum. During expensive periods it lowers the setpoint so that stored heat is used. Room comfort, measurement freshness, and floor safety override price optimization.

If a wood-stove load is configured and an expensive period has forecast heating demand, the planner works backwards from the desired heat-release start:

```text
notification time = desired heat-release start - configured release delay
```

It emits a recommendation rather than operating the stove. The push notification should state the room, configured wood amount/load name, recommended lighting time, expected release interval, expensive interval being covered, and the reason. The user can acknowledge `lit now`, `skip`, or record a different lighting time. Actual lighting time must replace the recommendation in subsequent predictions.

The planner must also require an explicit one-shot **stove loaded** state. A recurring daily availability window means only that the user can light the stove during those hours; it does not mean that wood is prepared every day. A recommendation is allowed only when the stove is loaded and its notification time falls inside an enabled availability window. `Lit now` consumes the loaded state. The user can also clear it manually. Readiness should expire conservatively rather than remain true indefinitely.

The first stove model has a configured delay before useful heat begins, an initial room-heating rate, and a duration over which heat output declines linearly to zero. Later observations can learn the actual curve for each load size.

Later, replace fixed thresholds with an optimizer which minimizes:

`electricity cost + room-discomfort penalty + site-peak penalty + setpoint-change penalty`

## Initial thermal model

The simulation uses two coupled temperature states:

```text
floor change = heater gain - heat transferred from floor to room
room change  = heat received from floor + wood-stove heat - heat lost to outdoors and wind
```

Parameters are deliberately expressed as rates rather than claiming a precise physical building model. Configured conservative floor-heating and floor-to-room rates remain in use until actual heater-demand telemetry is available. The planner learns per-room outdoor and wind cooling rates from trustworthy falling-temperature intervals aligned with persisted site weather. It requires a minimum sample count, records confidence and training time, clamps learned rates to conservative physical ranges, and blends them with configured defaults according to confidence. Warming intervals are not used to infer electric heater gain because sunlight, residual floor heat, a heat pump, cooking, or a wood stove could otherwise be misattributed to floor heating.

Preheating requires a fresh explicitly selected floor sensor. Price-driven discharge requires a fresh room sensor. Missing or stale inputs leave optimization inactive for the affected decision and preserve the existing controller as fallback.

The Java foundation is `HeatingPlanSimulationService`. It is pure and deterministic: a Vaadin view can submit a scenario and chart the returned snapshots without activating a physical device.

## Proposed persistence (monitoring stage)

### `thermal_storage_room`

- account and site ownership;
- room name and timezone;
- thermostat device and channel;
- room sensor device and temperature/humidity measurement identifiers;
- optional separate floor-sensor measurement identifier;
- optional wood-stove identifier;
- normal, preheat-maximum, absolute floor-maximum, desired-room, and minimum-room temperatures;
- optimization enabled flag;
- model parameters and model version;
- created/updated timestamps.

### `wood_stove_load_profile`

- owning room and user-facing load name;
- wood amount entered by the user, for example kilograms or a repeatable basket/load label;
- delay from ignition until useful heat release;
- useful heat-release duration;
- initial room-heating rate and optional heat shares delivered to adjacent rooms;
- enabled flag and notification availability window;
- current one-shot loaded/readiness state and expiry;
- learned parameters, sample count, and updated timestamp.

Wood monetary cost is intentionally not stored or considered by the planner.

### `thermal_storage_observation`

- room and observation time;
- floor, room, and humidity readings;
- outdoor temperature and wind;
- recommended and actual stove-lighting events and selected load profile;
- actual setpoint, heating demand, and data-quality flags.

### `thermal_storage_plan_point`

- room, plan version, planned time, and creation time;
- price, predicted floor/room temperatures, planned setpoint and heating state;
- predicted wood-stove heat rate and optional stove recommendation identifier;
- operating mode and human-readable reason;
- whether the point is simulated, active, superseded, or completed.

Do not overload `DeviceEntity.lastTelemetry` as historical storage. Zigbee temperature/humidity reporting needs a normalized measurement/history contract before monitoring is enabled.

## Safety and fail-safe rules

- The configured preheat maximum can never exceed the absolute floor maximum.
- Missing or stale floor temperature disables preheating.
- Missing or stale room temperature disables price-driven discharge and returns to the existing safe thermostat behaviour.
- The comfort minimum overrides an expensive-price instruction.
- Limit setpoint steps and command frequency.
- A plan is advisory until a Zigbee write is acknowledged and read back.
- Wood-stove actions always remain advisory and human-operated. The service does not ignite a fire or control combustion air or dampers.
- Stove temperature monitoring does not replace certified independent smoke and carbon-monoxide alarms.
- Cloud or gateway failure leaves the last verified local setting; it must not invent a new fallback command.
- Surface-material and construction limits are user/site-specific. The service must not advertise one universal safe floor temperature.

## Active-control activation

Active thermostat control is separate from the Heating Planner master switch. The user must explicitly activate a freshly recalculated plan from the Heating Planner view. Each enabled floor-heating room is independently eligible for active control when it has an owned Zigbee thermostat, explicit fresh room and floor sensors, readable and recently reported thermostat state, no unacknowledged command, plan points, and a learned cooling model with at least 25% confidence. Activation requires at least one eligible room; ineligible rooms are visibly excluded and remain on the existing fallback controller. The simulated candidate must be no more than 30 minutes old and cover the current time.

Activation atomically supersedes the previous active plan, promotes the selected plan and only eligible rooms' points to `ACTIVE`, expires Heating Planner desired states for excluded rooms, and enables the site's active-control flag. This confirmation is the one-time active-control opt-in. After opt-in, a background job recalculates every enabled site every 15 minutes from current prices, forecast, measurements, settings, and learned room models. A valid replacement with at least one eligible room is automatically promoted and supersedes the previous active plan; the user does not repeatedly confirm routine replans.

Automation status is persisted on the site settings: last successful automatic plan time, last automatic activation time, and the latest error or rejected-activation reason. Missing prices/weather or invalid plan coverage prevent automatic activation for the site. Stale sensors, low model confidence, and gateway/readback problems exclude the affected room; activation is rejected when no room remains eligible. If replanning continues to fail, the active command path stops accepting plan points older than 75 minutes and desired commands expire, allowing fallback control to resume.

At command time the same room and floor freshness checks are repeated. Plan points older than 75 minutes are rejected, changed Heating Planner setpoints are limited to one per five minutes, and desired commands expire after at most 30 minutes or at the plan horizon end. Existing Zigbee desired-state versioning, gateway acknowledgement, and thermostat readback remain authoritative. Disabling active control, or disabling the master planner switch, supersedes active plans and immediately expires desired states marked as originating from Heating Planner; the existing thermostat controller can then resume as fallback.

## Delivery sequence

1. Deterministic in-memory simulator and unit tests.
2. Vaadin simulation view with manual floor and wood-stove parameters and existing price/weather series.
3. Zigbee sensor telemetry contract and historical persistence.
4. Persisted room configuration and planned-versus-actual monitoring.
5. Push recommendation workflow with acknowledgement and actual lighting time.
6. Learned per-room floor and per-load stove thermal parameters.
7. Active floor control behind an explicit opt-in and conservative safety validation.
8. Whole-house coordination of the configured floor-heating zones and wood-stove heat influence.

## Acceptance criteria for active control

- Ownership isolation is tested for every linked device and site.
- Floor and room sensor freshness is visible and enforced.
- Every command has a reason, plan version, expiry, acknowledgement, and readback.
- Simulation error and comfort violations are measurable.
- The existing price-curve controller remains an available fallback.
- A dry-run period can run for at least one week without issuing commands.
