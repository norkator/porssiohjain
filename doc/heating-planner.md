# Heating Planner

Native Android widget API and implementation handoff: [heating-planner-widget.md](heating-planner-widget.md). The summary route is read-only; hybrid-web intentionally has no Heating Planner screen.

## Status

Design specification, live planning, and explicit opt-in active control. The thermostat's configured internal floor-temperature limit remains authoritative and is enforced by the thermostat independently of Heating Planner. The planner adds configurable planning bounds, sensor-freshness checks, latest-plan command publishing for Zigbee gateways, command expiry/rate limiting, and fallback behaviour.

## Sites and navigation

Heating Planner settings, rooms, comfort schedules, plans and active-control opt-ins belong to an account and site. Opening `/heating-planner` automatically shows the account's default Heating Planner site. The **Site** dropdown above the planner switches to another owned site without a separate selection page. **Make default** saves the current site as the account-wide Heating Planner default, shared across browsers and sessions. Browsing another site does not change this preference. The feature's own labels, dialogs and chart labels remain English independently of the desktop locale.

Until a default is explicitly saved, use the first enabled configured planner, then the first configured planner, then the first enabled site, then the first defined site. Settings/site ID order makes these fallbacks stable across automatic replans. A saved default can also be a disabled site. With no sites, the view links to Sites so a site can be created first. Flyway V118 adds the nullable `account.default_heating_planner_site_id` reference; deleting that site clears the preference and the normal fallback applies. Default selection never creates planner settings or enables control.

Direct links at `/heating-planner/{siteId}` validate effective-account ownership before loading or calculating a plan. Site changes navigate within the same desktop window and use its existing unsaved-room-edit guard; canceling leaves both the view and dropdown on the current site. The desktop Back-to-list toolbar is hidden because there is no separate site selection page. Start/taskbar switching continues to preserve the retained view.

Device selectors list the account's devices because devices have no direct site assignment. Select the devices belonging to the current site's rooms explicitly. A controller already assigned to another site's planner must be removed there and saved before it can be assigned here. Recent sensor readings show only devices selected in the current site's rooms and remain lazy-loaded; refresh them after changing device selections. Each enabled site's background planning continues independently of the site currently open in the browser.

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

Heat pumps can either remain observed or be controlled by Heating Planner. A site-level **Control heat pumps** switch is independent of floor-heating active control and defaults off. When off, their heating effect is visible through room-temperature measurements but no heat-pump command is issued.

When control is enabled, each heat-pump room uses its explicit room sensor and comfort target. Heating Planner may request a configured adjustment above or below that target (2 °C by default, clamped to the device-supported 16–30 °C range). Below the comfort minimum always requests recovery. A per-room price-shifting toggle optionally raises the requested temperature in selected cheap preheat periods and lowers it in expensive discharge periods. With price shifting off, setpoint changes respond only to the measured/predicted room temperature around the comfort target. Missing or stale room measurements suppress heat-pump commands.

A matching Weather Controls heat-pump rule with **Priority rule** enabled takes precedence over Heating Planner for that pump. A weather rule without priority is used only when Heating Planner has no command for the pump. When the priority rule stops matching, Heating Planner may resume control on the next scheduler run.

Heat-pump change pushes compare the successfully sent power state, operating mode, and target temperature with the previous sent settings. Repeated settings do not send another notification merely because polled indoor/outdoor temperatures or command metadata changed.

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

The site's operation state also affects price decisions. `NORMAL` uses the configured price thresholds. In `POWER_SAVE`, Heating Planner treats every period as expensive for floor-heating discharge and eligible wood-stove planning, and skips price-driven floor preheating. It keeps actual market prices in charts, persisted points, and cost estimates. The planner weather gate, wood weather gate, sensor freshness, comfort recovery, and thermostat limits still apply. The Vaadin view shows the selected site's operation state above the weather gate.

## Daily comfort schedules

Heating Planner is still in development. Keep the new comfort schedule text in English, including when the UI language is Finnish; use the base message bundle until the feature is ready for translation.

**Rooms and heat sources → Comfort schedules** manages named, site-specific schedules. A schedule has one default target (for example 21 °C) and optional daily time windows (for example 06:00–09:00 at 22 °C). Select the schedule in a room's **Comfort schedule** column and save the rooms. Clearing the selection returns to a constant target. Existing rooms remain on their original constant targets.

Times use the site's timezone, repeat every day, include the start and exclude the end. Windows may cross midnight and must not overlap. Gaps use the default target. Local-clock resolution also applies on 23/25-hour DST days; repeated local times use the same target. Times can be entered to the minute, while planner decisions continue to use 15-minute steps. A schedule may be shared by several rooms in the same site. Editing it affects those rooms on the next manual or automatic plan calculation. A schedule in use must be deselected and the rooms saved before it can be deleted.

The scheduled target is resolved separately for each planned period in both manual and automatic planning. The room's independently configured comfort minimum stays fixed. The simulation maximum follows the period's target + 2.5 °C. Normal and discharge floor setpoints use the existing target + 2 °C / target − 2 °C offsets, capped by the configured preheat maximum; preheat and absolute floor limits remain unchanged. Existing heat-pump planning also uses the period's target. Temperature predictions remain predictions, and active control retains its existing opt-in and freshness requirements.

Flyway V116 adds `heating_planner_comfort_schedule`, its daily periods, an optional room schedule reference, and a `comfort_target` snapshot on plan points. Saving a room revalidates the schedule's account/site ownership. Historical plan targets are preserved when the schedule is later changed. The room table keeps explicit column widths and scrolls horizontally within its available width on small screens.

Flyway V117 aligns the room-limit database check with configuration and simulation validation: the independent comfort minimum may equal or exceed a target, but must stay strictly below the simulation maximum (target + 2.5 °C), including the lowest scheduled target's maximum. The target must remain at or below the stored maximum. Existing room values are preserved.

## Sensor roles

Floor and room measurements are not interchangeable:

- floor temperature estimates stored thermal energy and keeps planning within the configured planner bound;
- room temperature protects occupant comfort;
- room humidity is monitoring context and may later identify shower/drying periods, but does not directly request heating in the first version;
- outdoor temperature and wind forecast estimate future heat loss.

The UI must require the user to state which measurement is the floor measurement. Device/model defaults may assist but must not silently infer this for unknown devices.

### Tuya Smart sensors

Imported Tuya temperature sensors are available in the Vaadin room and floor sensor selectors. Save the selected role explicitly; selecting a room sensor does not also select a floor sensor. Sensor import never enables active thermostat control.

`DeviceMeasurementService` provides the common read contract used by latest-temperature lookup, recent-reading/history UI, and thermal-model learning. Zigbee continues to write only to `zigbee_device_measurement`; Tuya and future integrations use `device_measurement`. No existing Zigbee history is moved or copied. Both stores retain one month of history with daily cleanup.

Enabled Tuya sensors with a saved account connection are polled every 30 minutes. Values are normalized to Celsius. If Tuya supplies a property report timestamp, it is preserved and the existing 60-minute measurement freshness applies; polling the same report never renews its timestamp. Otherwise the value is explicitly labeled as an online **cloud snapshot**, with an unknown sensor report time and a one-hour observation freshness limit. Its timestamp indicates when the cloud state was fetched, not when the sensor measured it. Thermal learning uses these observation times for cloud snapshots and report times for timestamped readings, retaining the existing 15-minute minimum sampling interval and cooling-evidence requirements.

Every Tuya input also requires an enabled device, API Online, and successful cloud contact within one hour. Tuya API Online expires after one hour without contact. Failed polls, cloud-reported offline status, disconnection, and disabling the device make its readings unavailable to planning. Offline cached values may still be displayed in the Devices dialog but are not stored as fresh observations. Missing/stale floor input continues to disable preheating; device-level thermostat floor limits remain independent and authoritative.

The planner still recalculates every 15 minutes using the latest valid input. Thirty-minute observations fit thermal learning's maximum two-hour sample gap; cooling rates use actual elapsed time. Fewer observations can delay collecting sufficient cooling evidence, and a new cloud value may take up to the next poll to affect planning. Timestamped reports older than 60 minutes remain stale even after a successful poll; an hourly-reporting sensor can briefly exceed this limit before the next poll retrieves its new report.

See [tuya-smart-integration.md](tuya-smart-integration.md) for account setup, property selection, storage/timestamp fields, status semantics, and live validation steps.

## Planning horizon

Use a today-and-tomorrow horizon in the site's timezone with 15-minute simulation steps. Tomorrow remains visibly unavailable until both tomorrow's market prices and sufficient weather forecast data exist. Replan when any of these occurs:

- new day-ahead prices arrive;
- the weather forecast changes materially;
- a fresh floor or room observation differs materially from the prediction;
- the user changes room settings;
- a site power limit constrains heating.

Live recalculation seeds the current price period with the latest sensor inputs and simulates the remaining periods of today and tomorrow. It skips fully elapsed periods rather than applying today's cooling history to a current measurement. Each 15-minute price point advances the thermal model by 15 minutes. Evidence separates sensor inputs, predicted room temperatures, configured comfort limits, and requested heat-pump setpoints; a comfort-recovery prediction is not a new sensor observation.

The chart's display horizon is separate from live calculation: each tab covers its whole local calendar day, including 23/25-hour DST days. Today retains full-day market prices and weather forecasts. Elapsed temperature/setpoint points use the latest saved prediction available when that period began, including superseded plans; unavailable history remains a gap. These are predictions, not measurements or proof of executed commands. Historical chart points never enter the live plan, its costs, or command decisions. A current-time line uses an exact instant, is visible only on the matching local day, and updates every minute while the chart is open.

The deterministic planner identifies expensive periods across the complete today-and-tomorrow horizon. For each expensive block it estimates forecast room heat loss from the indoor/outdoor temperature difference and wind, converts the required reserve into floor-heating steps, and selects sufficient preceding cheap points. It prefers lower prices and uses later points as the tie-breaker to reduce storage loss. There is no fixed preheat look-ahead. During selected cheap periods it raises the floor setpoint up to the configured preheat maximum. During expensive periods it lowers the setpoint so that stored heat is used. Room comfort, measurement freshness, and configured planner bounds override price optimization.

An optional daily no-preheat window blocks only price-driven floor preheating during user-defined local times. For example, a `22:00-05:00` window prevents the optimizer from choosing those points for storing cheap heat in the floor; outside the window, preheating can still be selected if it is cheap enough and needed before an expensive period. Comfort recovery remains allowed inside the window so the planner does not knowingly leave a room below its configured minimum.

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

Learning reads up to 14 days of room measurements, starting within the available retained weather history. Raw reports are paged through the complete matching interval and sampled at least 15 minutes apart; frequent or duplicate reports do not consume a fixed oldest-row cap or count as independent cooling samples. Only falling intervals no more than two hours apart with weather within 90 minutes qualify. At least 24 cooling intervals are needed to learn; confidence also reflects the cooling fit error, so 24 intervals alone do not guarantee the 25% active-control requirement.

A rebuild with too few intervals or confidence below 25% retains a previously qualifying model only while its recorded cooling evidence is no more than 14 days old. It preserves that model's confidence, sample count, rates and training/evidence timestamps. Reprocessing unchanged history never renews the cooling evidence timestamp. Confidence and evidence validity are checked both at activation and when issuing commands, so an old active plan cannot bypass expired or invalidated learning. Changing the selected room sensor or measurement key invalidates the previous sensor's model. Flat temperatures do not produce new cooling evidence; they also do not erase a still-valid model.

The Vaadin **Thermal model** dialog shows each saved room's actual confidence, cooling interval count, training time, last cooling observation and model readiness. **Rebuild from retained measurements** updates these saved models and reports whether each was rebuilt, retained or left on configured defaults. It uses saved sensors and room settings, does not activate a plan or send thermostat commands, and does not lower the confidence requirement. Recalculate the displayed plan afterwards, or let the next automatic replan use the updated models. Legacy qualifying models receive a bounded transition based on their old training time; previously overwritten confidence is not invented or recovered by migration.

Opening the Vaadin view and recalculating its displayed plan reuse qualifying saved
cooling models without rescanning measurement or weather history for learning.
Missing, expired or invalid saved models use the configured conservative defaults;
viewing never renews the learning or evidence timestamps. The existing 15-minute
automatic planning pass for enabled sites and **Rebuild from retained measurements**
continue to perform learning. Current room/floor sensor freshness and active-control
readiness are still checked independently. The collapsed **Recent sensor readings**
panel queries its latest-per-series readings only when first opened; **Refresh readings**
explicitly updates it afterwards. V115 adds indexes for these lists and Zigbee
device/type/key history lookups.

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

Do not overload `DeviceEntity.lastTelemetry` as historical storage. Normalized history lives in `zigbee_device_measurement` for existing Zigbee reporting and `device_measurement` for Tuya/future integrations, read through the common measurement service.

## Device limits and fail-safe behaviour

Heating Planner does not replace or bypass the thermostat's configured internal floor-temperature limit. That device-level limit is the primary protection and remains effective independently of cloud planning. The planner's floor-temperature fields are additional optimization bounds used to decide when and how much to preheat.

- The configured preheat maximum can never exceed the absolute floor maximum.
- Missing or stale floor temperature disables preheating.
- Missing or stale room temperature disables price-driven discharge and returns to the existing safe thermostat behaviour.
- The comfort minimum overrides an expensive-price instruction.
- Limit setpoint steps and command frequency.
- Heating Planner publishes the latest active plan command to the Zigbee gateway while the command remains valid.
- Wood-stove actions always remain advisory and human-operated. The service does not ignite a fire or control combustion air or dampers.
- Stove temperature monitoring does not replace certified independent smoke and carbon-monoxide alarms.
- Cloud or gateway failure leaves the last verified local setting; it must not invent a new fallback command.
- Heat-pump control requires an explicit site-level opt-in. Unsupported AC types or devices without a readable previously polled state remain observed only.
- Planner bounds should be configured consistently with the thermostat installation; Heating Planner does not invent or advertise a universal device limit.

## Active-control activation

Active thermostat control is separate from the Heating Planner master switch. The user must explicitly activate a freshly recalculated plan from the Heating Planner view. Each enabled floor-heating room is independently eligible for active control when it has an owned Zigbee thermostat, explicit fresh room and floor sensors, readable and recently reported thermostat state, plan points, and a learned cooling model with at least 25% confidence. Activation requires at least one eligible room; ineligible rooms are visibly excluded and remain on the existing fallback controller. The simulated candidate must be no more than 30 minutes old and cover the current time.

Activation atomically supersedes the previous active plan, promotes the selected plan and only eligible rooms' points to `ACTIVE`, expires Heating Planner desired states for excluded rooms, and enables the site's active-control flag. This confirmation is the one-time active-control opt-in. After opt-in, a background job recalculates every enabled site every 15 minutes from current prices, forecast, measurements, settings, and learned room models. A valid replacement with at least one eligible room is automatically promoted and supersedes the previous active plan; the user does not repeatedly confirm routine replans.

Automation status is persisted on the site settings: last successful automatic plan time, last automatic activation time, and the latest error or rejected-activation reason. Missing prices/weather or invalid plan coverage prevent automatic activation for the site. Stale sensors, low model confidence, and stale or unreadable gateway reports exclude the affected room; activation is rejected when no room remains eligible. If replanning continues to fail, the active command path stops accepting plan points older than 75 minutes and desired commands expire, allowing fallback control to resume.

Expected automatic activation deferrals are normal status results: the generated simulated plan, successful calculation time, and exact readiness reason remain saved for review and the next scheduled retry. Weather-gate inactivity still suspends previous active plans and expires their desired states. Each site's automatic refresh uses its own transaction. Unexpected calculation or persistence failures roll back only that site's refresh, log the full exception, and save the failure reason in a separate transaction so other sites can continue.

At command time the same room and floor freshness checks are repeated. Plan points older than 75 minutes are rejected, changed Heating Planner setpoints are limited to one per five minutes, and desired commands expire after at most 30 minutes or at the plan horizon end. While Heating Planner is active, gateway polls receive the latest valid Heating Planner desired state whenever one is available. Disabling active control, or disabling the master planner switch, supersedes active plans and immediately expires desired states marked as originating from Heating Planner; the existing thermostat controller can then resume as fallback.

## Delivery sequence

1. Deterministic in-memory simulator and unit tests.
2. Vaadin simulation view with manual floor and wood-stove parameters and existing price/weather series.
3. Zigbee sensor telemetry contract and historical persistence.
4. Persisted room configuration and planned-versus-actual monitoring.
5. Push recommendation workflow with acknowledgement and actual lighting time.
6. Learned per-room floor and per-load stove thermal parameters.
7. Active floor control behind an explicit opt-in.
8. Whole-house coordination of the configured floor-heating zones and wood-stove heat influence.

## Acceptance criteria for active control

- Ownership isolation is tested for every linked device and site.
- Floor and room sensor freshness is visible and enforced.
- Every command has a reason, plan version, and expiry.
- Simulation error and comfort violations are measurable.
- The existing price-curve controller remains an available fallback.
- A dry-run period can run for at least one week without issuing commands.
