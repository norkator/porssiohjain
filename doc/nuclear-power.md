# Nuclear Power

`/nuclear-power` is an authenticated, English-only Vaadin feature inside `MainLayout`.
It has its own desktop/Start shortcut, title-bar icon, and taskbar entry. Both locale
bundles deliberately call it **Nuclear Power**. The desktop shell keeps the account's
normal language; the view and all its controls remain English.

## Current scope

The feature is a fictional, interactive control-room simulation. All readings,
equipment states, alarms, events, and trends are simulated. It does not persist
telemetry, access measurement history, send device commands, or implement a physical
reactor model. Its separate primary/secondary circuits are a fictional two-loop
layout, not a Chernobyl/RBMK simulation or a validated model of any named reactor
type. It starts in Manual at a nominal operating point, with both pumps enabled at
100% and small deterministic process fluctuations enabled.

The user intends to evolve this into a physical, MQTT-connected scale model and
expects further iteration after trying it. Keep the controls rich and interactive,
keep all feature text English, and keep this document and the root `AGENTS.md`
handoff current when behavior changes.

The schematic includes a reactor cutaway with moving rods and fuel channels,
separate primary and secondary circuits, animated pumps and coolant flow, a heat
exchanger, a turbine/generator, and a condenser. Click or keyboard-activate outlined
equipment to inspect it. The diagram can be zoomed and scrolled. Controls and
instruments adapt to the feature window's width, including desktop window resizing.

Click the reactor vessel (or **Core map ↗**) to open the core map in a movable,
nonmodal floating dialog above the desktop windows. The process schematic stays
visible and interactive underneath, using the same model and rod positions. Drag
the dialog title bar to move it; when the title bar has keyboard focus, arrow keys
move it in 16 px steps. The frame is clamped to the browser viewport on movement
and resize. Close with × or Escape. The dialog closes when the feature is hidden
or detached; rod selection, settings, and its position remain in the retained view.
It uses the native manual-popover top layer with `role="dialog"`, so it can float
above the desktop frames without making the process view inert.

The core map is a fictional circular array of fuel channels and twelve individually
selectable rods, `CR-01` through `CR-12`. Rings show actual insertion; nearby fuel
activity is an illustrative derived visualization, not a measured local power or
temperature. The side section projects the same twelve rod positions.

Core temperature above the existing 335 °C demo high-temperature threshold turns
the temperature card, status light, outlet instrument and annunciator red. Text
also reads **HIGH TEMPERATURE**. Acknowledging the alarm does not remove this
indication; it clears only when temperature falls back to 335 °C or below. Both
themes have explicit high-temperature colors. The independent 360 °C simulated
coolant trip is retained, with an additional local-fuel hot-spot trip. These are
illustrative, not physical safety limits.

## Progressive stress, damage and meltdown

Failure is driven by a separate fictional condition/heat model, never a random
explosion or a single timer. Normal automatic generation, including constrained
MW requests, remains protected. Sustained loss of cooling can still overwhelm a
shutdown because stored fuel heat remains after rod insertion.

The overview's condition strip shows stage, peak local fuel temperature, cooling
margin, retained heat, temperature rate of rise, core damage and pump stress.
Negative cooling margin warns before severe temperatures. Pump buttons acquire
amber/red edges as stress and damage accumulate; their tooltips show actual speed,
stress and damage. Selecting a pump also exposes stress and damage in its details.
The floating core map shows hot/damaged channels and the selected channel's fuel
temperature and retained damage. The coolant reading and local fuel temperature
are separate signals; a hot spot can precede high average coolant temperature.

Stages are **NORMAL**, **STRAINED**, **OVERHEATING**, **PROTECTIVE SHUTDOWN**,
**COMPONENT DAMAGE**, **CORE DAMAGE**, and latched **MELTDOWN**. The order depends on
the fault: protection may trip before temperatures rise. Stress recedes after
correction, while damage does not heal automatically. At meltdown, fuel deforms
gradually and a glowing pool grows over subsequent seconds; there is no explosion.
Generation shuts down and the session remains inspectable/exportable.
The temperature trend expands its scale above the old 400 °C range during a severe
incident rather than clipping the continued rise.

Open **Faults & incident** below the left-hand instruments to select a progressive
primary obstruction, secondary cooling loss, or primary-pump deterioration. Faults
ramp rather than instantly changing flow. Existing manual pump controls and uneven
rod settings also affect stress and local heat. These controls are simulation-only.
**Clear injected fault** restores the fault's restriction gradually, preserving wear.
**Repair components** requires a stopped reactor, no injected fault, coolant below
150 °C, fuel below 200 °C, and no meltdown. It repairs accumulated damage/stress while
preserving heat and operating settings. Meltdown can only be cleared by **Reset demo**.

Trip reset additionally requires cleared faults, repaired damage and local fuel
below 410 °C. It does not clear retained heat, stress, damage or the incident history,
and it never restarts automatically. Auto reduces generation against cooling capacity
and local fuel hot spots; it does not bypass protective trips or restart failed pumps.

### Illustrative model constants

All constants are gameplay/demo parameters, not real reactor engineering:

- Heat reserve starts at 96 internal units, follows generation with a 40-second
  response and decays over a 240-second response after shutdown. Displayed retained
  heat is reserve × 1.5 MW. Normal initial fuel temperature is approximately 323 °C.
- Cooling margin compares a 1200 MW nominal cooling scale, reduced by actual flow
  and core damage, with generation plus retained heat. Fuel temperature integrates
  heat input minus cooling with bounded rates. Poor cooling keeps the coolant
  reading coupled to retained fuel heat rather than dropping immediately to ambient.
- Rod imbalance creates local temperatures. Above 410 °C, exposure accumulates;
  above 450 °C and after 15 seconds of exposure, channel damage accumulates.
  A local peak above 470 °C trips generation. Cooling failure can continue afterward.
- Meltdown latches only after a channel reaches 100% damage with fuel at least
  650 °C. Its visual progression then ramps with a 12-second response.
- Pump stress comes from sustained requested/actual flow mismatch. Stress above
  60% accumulates wear, progressively reducing available flow; speed alone does not
  damage a healthy pump. All state freezes with simulation pause/hidden-view suspension.

## Layout and appearance

The console uses the full feature-window width with no fixed maximum width. At
container widths of at least 1400 px, it has three columns:

- left: stacked temperature, thermal output, electrical output, flow and rod cards,
  followed by selected equipment details;
- middle: regulation controls, then reactor overview beside the operator console;
- right: live trends above the filterable event journal.

The default overview fits 1920×1080 and 2560×1440 screens without page scrolling.
Long event lists scroll inside the journal. Narrower/resized windows progressively
use two columns and then stacked sections, with mobile-sized controls retained.

Dark is the default. The header's **Light theme** switch changes this feature's
panels, controls, diagrams, chart and logs independently of the Windows 98 shell.
The browser preference uses `porssiohjain.nuclear-power.theme` in local storage;
unavailable storage falls back to dark and never prevents switching themes. Theme
changes do not reset simulation state or alter control settings.
Light mode uses darker secondary labels and full-opacity disabled controls, so
Auto-owned actuator settings remain readable while disabled.

## Controls

Controls:

- Start/Stop: normal startup or cooldown, maintaining circulation settings and
  individual operating rod targets. Start requires available circulation and a
  temperature below the demo reset limit.
- Load setpoint: sets requested output and corresponding rod insertion.
- Bank Insert/Withdraw: moves every rod's own target by five percentage points,
  retaining offsets until travel limits are reached. Load setpoint intentionally
  applies a uniform bank target. Mean actual insertion is displayed separately.
- Individual rods: select a numbered channel or use the rod selector, then adjust
  its insertion slider or one-percentage-point buttons. Only that rod changes.
- Pump A/B: enable/disable and independently adjust speed targets from 0 to 100%.
  Actual speed/flow ramps gradually; disabling preserves the speed setting. Pump
  rotation and each circuit's coolant animation follow actual speed, including
  coast-down, with continuous phase and an explicit local SVG rotation pivot.
- Emergency shutdown: latches a simulated trip and inserts rods. Restart requires
  **Reset trip**, then **Start**. Low primary flow or high core temperature can also
  trigger the illustrative protection sequence. Reset trip requires all rods at
  least 99.5% inserted, both enabled circuits with target/actual speed at least 20%,
  and core temperature below the demo limit of 335 °C. These are arbitrary simulation
  limits, not real nuclear operating instructions. A checklist explains readiness.
  Reset trip preserves settings, elapsed time, history, and logs and leaves the
  reactor stopped. Reset demo remains available to replace the whole session.
- Acknowledge alarms: acknowledges existing alerts without hiding active conditions.
  New faults require their own acknowledgement.
- Pause/Resume: freezes/resumes simulated time and animations.
- Reset demo: restores the initial operating point, event log, and trend history.

## Automatic regulation and fluctuations

**Auto** has selectable **Core temperature** and **Electrical output** objectives.
Temperature regulation is the default. It holds configurable core temperature
(40–330 °C; default 286 °C) and circulation (25–100%; default 85%) targets.
A one-second illustrative regulator uses
feed-forward plus proportional/integral temperature correction to adjust the rod
bank, retaining individual offsets where travel allows. Independent feedback adjusts
both pump speed targets. It does not automatically re-enable an operator-disabled
pump. Manual actuator sliders are disabled during automatic running; pump enable
buttons, Stop and Emergency shutdown remain available. During a trip, pump speeds
can be changed to restore circulation. Returning to Manual holds current settings.

Small, bounded deterministic waveforms disturb heat, output and each loop's flow.
**Fluctuations: On/Off** lets the user compare disturbance response. The automatic
controller regulates the drifting process rather than freezing displayed values.
The model is deliberately simplified and does not solve reactor/neutron physics.
Trips inhibit regulation. Resetting a trip never triggers automatic restart; an
explicit Start is required even when Auto remains selected.

For grid-style generation control, choose **Regulate → Electrical output**, enter
**Output target** (0–400 MW electric; default 200), then select **Auto**. The target
is generator electrical output (`MWₑ`), distinct from the thermal output (`MWₜₕ`).
The left electrical card and regulation status show actual versus requested output.
The right trend switches its output axis from reactor load (%) to electrical MW and
includes the target's recorded history as a dashed line. Only active electrical
regulation records target samples, so the line does not imply a target was active
before it was selected.

In electrical-output mode, **Core target** becomes **Core limit**: the same configured
temperature now limits requested generation. Pump circulation still follows the
configured flow target. The controller adjusts rods against actual generator output
and secondary flow, limits thermal power by available cooling and the temperature
ceiling, and prevents integral wind-up when constrained. Unachievable requests show
the limiting reason (for example **Core temperature limit**); reported output is not
replaced with the requested value. Returning to temperature regulation restores that
objective, preserving the electrical setpoint for later. Trips, manual mode and
explicit restart rules remain authoritative for both objectives.

## Logging

The event journal retains the newest 200 records in this browser instance, with
simulated elapsed time, severity, category, and message. Categories are Operator,
Automation, Process, Alarms, and System. Automatic corrections and process snapshots
are recorded every ten simulated seconds; mode changes, commands, faults, clears,
acknowledgements and recovery are recorded as they happen. Filtering changes only
what is displayed. **Export CSV** exports all retained records in chronological
order, explicitly marked `SIMULATED`. Logs are not persisted and are discarded by
Reset demo or closing/replacing the view.

The condition model keeps a separate incident report so the initiating fault is
not lost when the ordinary journal rolls over. It records the cause, up to six
preceding events, and a bounded 160-entry timeline with instrument snapshots;
the first incident event is preserved. **Export incident report** downloads JSON
explicitly marked `SIMULATED`. Early successful recovery marks an incident resolved;
meltdown leaves it open for inspection. Reports are browser-session data and are
cleared by Reset demo. Export before resetting if the report is needed later.

Each custom-element instance owns its state. Switching desktop windows retains the
instance. Minimizing, scrolling the entire console out of view, or hiding the browser
tab suspends background simulation work without fast-forwarding on return. Detaching
the element clears its timer and observers. Reduced-motion preferences disable visual
animation while instruments and controls continue to work.

## Implementation and future measurements

- `views/NuclearPowerView.java` checks authentication before adding the console.
- `src/main/frontend/nuclear-power.js` renders the native custom element, SVG
  schematic, controls, recent two-minute trends, and bounded event journal.
- `src/main/frontend/nuclear-power.css` is isolated in the element's shadow root.
- `src/main/frontend/nuclear-power-light.css` supplies the optional light palette.
- `src/main/frontend/nuclear-power-model.js` contains the deterministic, browser-local
  plant, individual rod/channel layout, controller, disturbances, alarms and log.
  Its snapshot separates `commands` (requested mode/objective, temperature/flow/MW
  targets, enable states) from
  reported `signals` and per-rod insertion. Signals contain value, unit, source,
  quality, and simulated elapsed time; that time is not a sensor timestamp.
- `src/main/frontend/nuclear-power-condition.js` owns stored heat, local fuel zones,
  progressive faults, component stress/wear, damage, meltdown and incident retention.
- `src/main/frontend/nuclear-power-motion.js` integrates speed-dependent impeller
  angles and flow dash offsets without phase jumps. Animation frames are separate
  from the 250 ms plant/instrument update and obey visibility and reduced motion.

A later measurement adapter should reuse `DeviceMeasurementService` with effective
account ownership checks. Bind sources per instrument, preserve their actual units
and measured/observed timestamps, and explicitly display measured, calculated,
simulated, stale, and missing states. Do not replace missing live signals with demo
values or write simulated values into the measurement tables. The existing measurement
types cover temperature, humidity, battery percentage, and thermostat setpoint;
flow, power, and confirmed pump state require additional source support.

Real actuator control is a separate future feature. Commanded state and confirmed
read-back state must stay distinct. The existing Powerplant board and dedicated
heating controllers remain independent of this simulation.

Future physical-model work should introduce a transport adapter rather than connect
the current buttons to arbitrary existing relays. Define account/model ownership,
MQTT topics and device/channel bindings, command IDs, expiry, acknowledgements,
actual rod position/pump-speed telemetry, freshness and offline states, and explicit
simulation/hardware mode. Physical interlocks and emergency stop need device-side
enforcement; browser animation or a sent MQTT command cannot establish confirmed
hardware state. The current numeric ranges and trip criteria must not be reused as
physical safety limits without a separate scale-model capability design.

## Deferred work — wait for the user's explicit go signal

The following requests are recorded for the next development pass. They are not
implemented and must not be started until the user says to begin:

- **Fuel depletion and manual replacement:** model individual fuel rods/assemblies
  separately from control rods. Track gradual, uneven use and remaining capability,
  driven by each element's operating history rather than simultaneous expiry. The
  core map should allow the operator to replace used elements individually when the
  planned replacement conditions permit. Automation must never replace fuel. Define
  the simplified burnup/reactivity model and UI clearly before implementation; the
  current fictional simulation is not a validated reactor-physics model.
- **Manual control-rod override during Auto:** allow an operator to withdraw a
  selected control rod while automatic regulation remains active, deliberately
  disturbing the process so the controller responds. Define override ownership,
  duration/release, and controller compensation without silently fighting the same
  actuator. Preserve protective trips and future device-side interlocks. Current
  Auto-owned rod controls remain disabled until this change is authorized.

## Verification

```sh
JAVA_HOME=/path/to/java21 ./gradlew compileJava test \
  --tests com.nitramite.porssiohjain.views.MainLayoutTest \
  --tests com.nitramite.porssiohjain.views.NuclearPowerViewTest \
  --tests com.nitramite.porssiohjain.views.DesktopWindowNavigationTest
node --test src/test/frontend/nuclear-power-model.test.mjs
node --test src/test/frontend/nuclear-power-failure.test.mjs
node --test src/test/frontend/nuclear-power-browser.test.mjs
# With the disposable local application already running:
NUCLEAR_POWER_APP_URL=http://127.0.0.1:18080 node --test src/test/frontend/nuclear-power-browser.test.mjs
```

Browser tests use Chrome at `/opt/google/chrome/chrome` by default; `CHROME_BIN` can
select another installed Chromium executable. Without `NUCLEAR_POWER_APP_URL`, a
temporary loopback server tests the current frontend source directly. Each run prints
its temporary screenshot/CSV artifact directory. No extra browser test dependency
is required beyond Node and Chrome.

Latest checkpoint (2026-11-06): the Java 21 build and 24 focused desktop/view tests
pass. Twenty-three model/motion/failure tests cover the controls and regulation, including
a two-minute settled-error window and zero-flow behavior with disturbances enabled.
Failure tests cover warning time, intervention, accumulated damage/repair, local hot
spots, automatic protection, sustained cooling loss, slow meltdown and report retention.
The browser regression also passes against the source-only console and the
authenticated local Vaadin application. No real MQTT/model devices are connected.

The model tests cover independent rod positions, bank behavior, speed/flow inertia,
phase-preserving motion, automatic regulation under disturbances, categorized bounded
logging, normal restart, and trip recovery without losing settings. Browser checks
must also verify fixed pump hub centers over multiple frames, individual selection,
both view modes, control disabling in Auto, CSV export, mobile/container resizing,
wide-screen fitting and column ordering, theme default/persistence, electrical MW
tracking and limiting, floating core-map opening/movement/closing with the process
still visible, persistent overheat indication after acknowledgement, and
pause/minimize/detach/reduced-motion cleanup. Use the disposable local profile
(`./run-local.sh 18080`, admin/admin) for authenticated application checks.
