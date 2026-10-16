# Heating Planner widget API and Android handoff

Implemented backend contract, schema version 1. Android API helpers are prepared; the widget itself is the next session's work. Keep hybrid-web without a Heating Planner screen. Widget taps open the existing app menu.

## Requests

```http
GET /api/heating-planner/widget-summary?siteId=42
GET /api/heating-planner/widget-summary?siteId=42&roomId=7
Authorization: <raw-login-token>
```

Use the raw token, without `Bearer`. `siteId` is required and both IDs must be positive integers. HTTP 400 covers missing/invalid parameters, HTTP 401 missing/invalid authentication, and HTTP 404 unknown or unowned sites/rooms. An owned site with no planner settings returns HTTP 200 with `NOT_CONFIGURED`. A room filter must belong to the selected site/settings. Sites are listed by existing authenticated `GET /api/sites`.

`HeatingPlannerController` delegates to `HeatingPlannerWidgetQueryService` in the backend repository `/home/norkator/Documents/GitHub/porssiohjain`. The DTO is `services/models/HeatingPlannerWidgetSummaryResponse.java`. These sources define the exact contract.

The GET is read-only: it never creates settings, recalculates/activates a plan, fetches weather, sends a push, or publishes a device command. No migration or hybrid-web changes are needed.

## Response

All timestamps are UTC ISO-8601 instants. `timezone` is the planner's IANA timezone, falling back to the site's timezone when unconfigured. Local dates and event labels use this returned timezone, not the phone's timezone. Units: temperatures/setpoints are degrees Celsius; wood amount follows the configured load amount (current planner uses kilograms).

| Field | Meaning |
| --- | --- |
| `schemaVersion`, `serverTime` | Version 1; server snapshot time, distinct from client fetch time |
| `site` | `{id, name}` |
| `timezone`, `operationState` | Planner timezone; site `NORMAL` or `POWER_SAVE` |
| `configured`, `enabled` | Persisted settings exist; master planner switch |
| `floorControlOptedIn`, `heatPumpControlOptedIn` | Independent opt-in flags; neither proves physical heating |
| `status` | `{code, reasonCodes}`; codes below |
| `automation` | `lastAutomaticPlanAt`, `lastAutomaticActivationAt`, `lastAutomationError`; nullable |
| `latestPlan`, `activeFloorPlan` | Nullable independent plan references; fields below |
| `coverage` | `todayDate`, `tomorrowDate`, `tomorrowAvailability`, `enabledRoomCount`, `roomsWithCurrentPoints` |
| `currentModeCounts` | Counts by planned `NORMAL`, `INACTIVE`, `PREHEAT`, `DISCHARGE`, `COMFORT_RECOVERY`; absent keys mean zero |
| `roomsWithActiveFloorPoints` | Current ACTIVE, non-INACTIVE floor points for opted-in enabled planning; scheduled data, not actuator confirmations |
| `roomsWithWarnings` | Number of returned rooms with reason codes |
| `nextChange` | Nullable `{at, changes[]}`; earliest future planned mode/setpoint change, preserving simultaneous rooms |
| `rooms` | All enabled rooms for the selected site, or the single enabled room requested |
| `stoveLoaded` | Site's one-shot readiness flag |
| `nextWoodRecommendation` | Nullable site-level advisory, retained even with a room filter |
| `warnings` | `{code, roomId, detail}[]`; room ID and detail may be null |

Plan references contain `planVersion` (UUID string), `status` (`SIMULATED`/`ACTIVE`), `generatedAt`, persisted `horizonStart`/`horizonEnd`, `coverageEnd`, `coversNow`, and `stale`. Persistence currently stores horizonEnd as the last point's start. `coverageEnd` includes the final simulation interval. `coversNow` requires a point interval containing now; gaps are not bridged.

Room fields:

- `roomId`, `name`.
- `roomTemperature`, `floorTemperature`: `{value, measuredAt, freshness, observedAt}` from `HeatingPlannerMeasurementService`. Freshness is `FRESH`, `STALE`, or `MISSING`. Missing values/timestamps are null. `measuredAt = null` with a value and `observedAt` means a Tuya cloud snapshot whose actual report time is unknown. Label it with its cloud fetch time, never as a newly measured temperature. For timestamped sensor reports, `observedAt` equals `measuredAt`. These are sensor inputs, never substituted predictions.
- `currentPoint`: nullable `{planVersion, pointStatus, startsAt, endsAt, operatingMode, plannedFloorSetpoint, plannedHeatPumpSetpoint, predictedRoomTemperature, predictedFloorTemperature, predictedAt, predictedHeating, reason}`. Predicted temperatures describe the end of the simulation step (`predictedAt`). Null currentPoint means no interval covers now. It is not proof of physical heater state.
- `nextChange`: same shape as the site-level nextChange, scoped to the room.
- `thermostats`: enabled floor thermostat gateway evidence, or an empty array if none is linked.
- `reasonCodes`: room-level warning codes.

Each nextChange `changes[]` item contains `roomId`, `roomName`, `planVersion`, `pointStatus`, `operatingMode`, `plannedFloorSetpoint`, `plannedHeatPumpSetpoint`, and persisted `reason`. Identical quarters and decimal scale differences are skipped; a gap starts a new event. Upcoming changes are suppressed for disabled planning or an outdated selected plan.

Each thermostat item contains `deviceId`, `desiredSource`, `desiredSetpoint`, `desiredMode`, `desiredVersion`, `desiredAt`, `desiredExpiresAt`, `desiredExpired`, `reportedSetpoint`, `reportedMode`, `reportedAt`, `reportFresh`, `readbackMatchesDesired`, `lastError`. Matching read-back requires a fresh readable report and an unexpired desired command. There is no separately persisted acknowledged version/time in the current gateway model. Do not label matching read-back as a version acknowledgement or as proof of an energized relay.

Wood recommendation fields: `id`, `roomId`, `roomName`, `planVersion`, `notifyAt`, `releaseStartsAt`, `releaseEndsAt`, `loadName`, `woodAmount`, `status` (`PENDING`/`SENT`), `due`, `reason`. `notifyAt` is the recommended lighting/call time; `due` means that time has arrived. Recommendations must still be before their release start, within the today/tomorrow horizon, from a non-superseded plan, with planner enabled and stove loaded. LIT/SKIPPED/EXPIRED/SUPERSEDED recommendations are excluded. SENT means the push was sent, not that the user lit the stove.

## Status and freshness

| `status.code` | Widget label/meaning |
| --- | --- |
| `NOT_CONFIGURED` | Planner not configured for this site |
| `DISABLED` | Master switch off |
| `NO_PLAN` | No persisted display plan or no enabled selected rooms |
| `SIMULATED` | Planned activity available; no current active floor points |
| `ACTIVE_PLAN` | Current active floor plan points; no room warnings |
| `PARTIAL` | Partial current coverage, excluded floor rooms, or room warnings alongside active floor points |
| `INACTIVE` | All current points have INACTIVE mode |
| `STALE` | Selected plan older than 75 minutes or no current point coverage |

A new simulated candidate may coexist with an older active floor plan. Opted-in floor rooms retain their still-covering ACTIVE schedule when available; excluded room points remain simulated. `latestPlan` can therefore differ from room currentPoint.planVersion and `activeFloorPlan`. Heat-pump opt-in and planned heat-pump setpoints are exposed independently: `SIMULATED` does not assert that no heat-pump command exists. This endpoint does not evaluate weather-rule priority or issue heat-pump commands.

Plan freshness is 75 minutes from creation for widget display. This is conservative display freshness, not a replacement for floor command eligibility (which checks point age and sensor state). Sensor report freshness is 60 minutes. Timestamp-free Tuya cloud snapshots use a one-hour observation freshness limit with polling every 30 minutes; all Tuya inputs additionally require an enabled device, API Online, and cloud contact within one hour. A failed poll invalidates Tuya input immediately. Thermostat readable report freshness is 15 minutes. A recent widget fetch does not make an old plan/report fresh.

Reason codes: `PLANNER_DISABLED`, `NO_PLAN`, `NO_ENABLED_ROOMS`, `PLAN_OUTDATED`, `CURRENT_POINT_MISSING`, `PLANNER_INACTIVE`, `ROOM_TEMPERATURE_STALE`, `ROOM_TEMPERATURE_MISSING`, `FLOOR_TEMPERATURE_STALE`, `FLOOR_TEMPERATURE_MISSING`, `AUTOMATION_ERROR`, `TOMORROW_PLAN_INCOMPLETE`, `FLOOR_ROOM_NOT_ACTIVE`, `THERMOSTAT_REPORT_STALE`, `THERMOSTAT_COMMAND_EXPIRED`, `THERMOSTAT_ERROR`. Localize codes in Android; persisted reasons/errors remain explanatory English text. INACTIVE is derived from structured operating mode, not string matching or an inferred weather reason.

`tomorrowAvailability` is COMPLETE/PARTIAL/UNAVAILABLE for persisted intervals across all selected enabled rooms. It is not a verification of weather/price publication completeness. Intervals use the configured simulation step (currently 15 minutes). Coverage includes local midnight and handles 23/25-hour DST days. Full weather/price evidence is not part of this compact endpoint.

## Android next session

Read the Android repository's `AGENTS.md` first. Preserve its unrelated changes, including existing untracked release artifacts. Java/classic RemoteViews and the current ApiClient are the established implementation; the older generic widget handoff's Kotlin/WorkManager suggestions are not the current architecture.

Prepared helpers in `ApiClient`:

```java
ApiClient.getSites(context); // JSONArray for site configuration
ApiClient.getHeatingPlannerWidgetSummary(context, siteId, roomId); // JSONObject; roomId may be null
```

Both helpers use context authentication, the active base URL, and the shared token-refresh lock. Do not capture a raw token in a new long-lived widget request path.

Implement next:

1. Add `HeatingPlannerWidgetProvider`, RemoteViews XML layout, widget metadata, manifest receiver, and a configuration activity. Store a site per appWidgetId; optional room selection can follow later. Room IDs/names are available from summary.rooms.
2. Register the provider in `EnergyWidgets.updateAll`, `refreshAllNow`, and `hasAnyWidgets`. Reuse quarter-hour refresh scheduling and existing `WidgetRefreshJobService`; add a manual refresh action.
3. Cache response and successful fetch timestamp per account/session, environment, site, and room. Clear protected caches on logout/account/environment changes. Retain cached data with a visible stale/offline label on temporary network failure. Never render a previous account's data. Inspect AppSession's existing cache clearing when registering new keys.
4. Display **Heating Planner** in both English and Finnish, site name, localized status, planned current activity, next change with local time/room, stove advisory when present, relevant warning, and fetch/plan timestamps. Larger layouts can add room temperatures. Use “Planned preheat”, not “Heating on”. No chart/timeline endpoint is implemented in version 1.
5. Widget taps open the existing app menu through the common app pending intent. Do not add a Heating Planner screen or route to hybrid-web. Do not send stove acknowledgements or activation commands from the widget.
6. Handle null/missing JSON fields and unknown future codes. Android optDouble-style defaults must not turn missing temperature into 0 °C. Re-evaluate cache age/currentPoint.endsAt while rendering to prevent stale data appearing live.
7. Verify token rotation, signed-out state, multiple sites, cache isolation, offline display, DST/site timezone, resize layouts, and actual tap behavior. Run `./gradlew compileDebugJavaWithJavac` and focused tests.

Backend focused verification:

```sh
./gradlew compileJava test --tests com.nitramite.porssiohjain.HeatingPlannerControllerTest --tests 'com.nitramite.porssiohjain.services.heating.HeatingPlanner*Test'
```

Tests cover raw-token auth, validation and ownership, unconfigured sites, persisted read-only responses, active/candidate coexistence, partial room activation, next-change ties/gaps, stale/missing measurements, expired desired/read-back evidence, pending/sent stove filtering, and both DST day lengths.
