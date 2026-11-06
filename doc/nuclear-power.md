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

The **Process / Core map** switch preserves the same model and settings. The core
map is a fictional circular array of fuel channels and twelve individually
selectable rods, `CR-01` through `CR-12`. Rings show actual insertion; nearby fuel
activity is an illustrative derived visualization, not a measured local power or
temperature. The side section projects the same twelve rod positions.

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

**Auto** holds configurable core temperature (40–330 °C; default 286 °C) and
circulation (25–100%; default 85%) targets. A one-second illustrative regulator uses
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

## Logging

The event journal retains the newest 200 records in this browser instance, with
simulated elapsed time, severity, category, and message. Categories are Operator,
Automation, Process, Alarms, and System. Automatic corrections and process snapshots
are recorded every ten simulated seconds; mode changes, commands, faults, clears,
acknowledgements and recovery are recorded as they happen. Filtering changes only
what is displayed. **Export CSV** exports all retained records in chronological
order, explicitly marked `SIMULATED`. Logs are not persisted and are discarded by
Reset demo or closing/replacing the view.

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
- `src/main/frontend/nuclear-power-model.js` contains the deterministic, browser-local
  plant, individual rod/channel layout, controller, disturbances, alarms and log.
  Its snapshot separates `commands` (requested mode, targets, enable states) from
  reported `signals` and per-rod insertion. Signals contain value, unit, source,
  quality, and simulated elapsed time; that time is not a sensor timestamp.
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

## Verification

```sh
JAVA_HOME=/path/to/java21 ./gradlew compileJava test \
  --tests com.nitramite.porssiohjain.views.MainLayoutTest \
  --tests com.nitramite.porssiohjain.views.NuclearPowerViewTest \
  --tests com.nitramite.porssiohjain.views.DesktopWindowNavigationTest
node --test src/test/frontend/nuclear-power-model.test.mjs
node --test src/test/frontend/nuclear-power-browser.test.mjs
# With the disposable local application already running:
NUCLEAR_POWER_APP_URL=http://127.0.0.1:18080 node --test src/test/frontend/nuclear-power-browser.test.mjs
```

Browser tests use Chrome at `/opt/google/chrome/chrome` by default; `CHROME_BIN` can
select another installed Chromium executable. Without `NUCLEAR_POWER_APP_URL`, a
temporary loopback server tests the current frontend source directly. Each run prints
its temporary screenshot/CSV artifact directory. No extra browser test dependency
is required beyond Node and Chrome.

Latest checkpoint (2026-11-06): the clean Java 21 build and 24 focused desktop/view
tests pass. Thirteen model/motion tests cover the controls and regulation, including
a two-minute settled-error window and zero-flow behavior with disturbances enabled.
The browser regression also passes against the source-only console and the
authenticated local Vaadin application. No real MQTT/model devices are connected.

The model tests cover independent rod positions, bank behavior, speed/flow inertia,
phase-preserving motion, automatic regulation under disturbances, categorized bounded
logging, normal restart, and trip recovery without losing settings. Browser checks
must also verify fixed pump hub centers over multiple frames, individual selection,
both view modes, control disabling in Auto, CSV export, mobile/container resizing,
and pause/minimize/detach/reduced-motion cleanup. Use the disposable local profile
(`./run-local.sh 18080`, admin/admin) for authenticated application checks.
