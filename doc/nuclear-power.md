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
type. It starts stopped with Auto → Electrical output selected (200 MW target).
Start both pumps explicitly, allow circulation to build, then press reactor Start
to begin generation. Initial rods are fully inserted, output and
retained heat are zero, and coolant/fuel start near ambient (30 °C). Both pumps
start disabled with zero actual motor speed and flow, retaining 100% speed targets;
small deterministic process fluctuations are enabled.
Reset demo returns to this same stopped state.

The user intends to evolve this into a physical, MQTT-connected scale model and
expects further iteration after trying it. Keep the controls rich and interactive,
keep all feature text English, and keep this document and the root `AGENTS.md`
handoff current when behavior changes.

The schematic includes a reactor cutaway with moving rods and fuel channels,
separate primary and secondary circuits, animated pumps and coolant flow, a heat
exchanger, a turbine/generator, and a condenser. Click or keyboard-activate outlined
equipment to open its movable component console. The reactor opens the core map;
its former Equipment detail readings are inside that window. The main view has no
Equipment detail card. The diagram can be zoomed and scrolled. Controls and
instruments adapt to the feature window's width, including desktop window resizing.

Click the reactor vessel (or **Core map ↗**) to open the core map in a movable,
nonmodal floating dialog above the desktop windows (up to 900 px wide, with a
650 px core drawing and viewport clamping). The process schematic stays
visible and interactive underneath, using the same model and rod positions. Drag
the dialog title bar to move it; when the title bar has keyboard focus, arrow keys
move it in 16 px steps. The frame is clamped to the browser viewport on movement
and resize. Close with × or Escape. The dialog closes when the feature is hidden
or detached; rod selection, settings, and its position remain in the retained view.
It uses the native manual-popover top layer with `role="dialog"`, so it can float
above the desktop frames without making the process view inert.

The core map is a fictional circular array of individually selectable fuel assemblies
and twelve control rods, `CR-01` through `CR-12`. Control-rod rings show actual insertion;
amber fuel rings show remaining reserve. Depleted assemblies show a high-contrast
cross and **EMPTY** label; low-reserve assemblies show their remaining percentage.
A count below the map summarizes depleted and low-reserve assemblies. Tooltips and
accessible labels state reserve status; damage retains its separate red border. Fuel activity is illustrative, not a measured
local power or temperature. The side section projects the same twelve rod positions.

Core temperature above the existing 335 °C demo high-temperature threshold turns
the temperature card, status light, outlet instrument and annunciator red. Text
also reads **HIGH TEMPERATURE**. The core card and outlet instrument bounce gently
while their borders are red (above 335 °C), stopping on cooldown; reduced motion
keeps them static and pause/hidden-view suspension freezes the bounce. Acknowledging the alarm does not remove this
indication; it clears only when temperature falls back to 335 °C or below. Both
themes have explicit high-temperature colors. Normal running temperatures use a
green card, reading and status indicator; stopped/tripped or damaged states do not
show this green operating indication. The independent 360 °C simulated
coolant trip is active in Auto, with an additional Auto local-fuel hot-spot trip. These are
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
The latched meltdown adds a slow red pulse across the entire console and a
movable, nonmodal top-layer dialog with bouncing **CORE MELTDOWN** text, incident
export and **Reset demo**. It uses the shared core-map/generator title-bar dragging,
keyboard movement and viewport clamping. Close with × or Escape; reopen from
**Meltdown incident ↗** in the footer. Closing it does not clear the incident,
red console pulse or siren. Hiding the view closes it; returning reopens it.
Reduced-motion preferences replace the pulse/bounce with static red emphasis;
pause and hidden-view suspension also pause these effects. Existing damage and
meltdown thresholds remain unchanged.

**Enable plant sounds** in the header opts into browser-synthesized audio, with an
explicit mute control. Unacknowledged alarms start with an attention tone every
three seconds. Fuel temperature and retained core damage escalate the sound through
overheating (2 s), core damage (1.3 s), critical (1 s) and meltdown (0.85 s) cadences,
with wider pitch sweeps, stronger waveform and modestly increasing gain. Critical
starts at 600 °C peak fuel or 70% damage; earlier levels use the existing 410/450 °C
warning/damage boundaries. These are illustrative demo levels. Acknowledgement
silences ordinary attention tones; ongoing overheating/damage and meltdown remain
audible until conditions resolve or the user mutes. Mute, pause, hidden views and detach stop audio;
Reset demo clears the incident sound. Browsers require a user gesture to enable
audio. Sound settings belong to this view instance and no audio files are fetched.
The same switch enables distinct quiet ascending Start and descending Stop cues,
only when that command changes the reactor's running state. Blocked/repeated commands
produce no cue. Kenney UI Audio samples provide quiet on/off switch clicks and a short click when
an adjustment is committed (not on every slider movement). They share Enable plant
sounds and mute/pause/visibility cleanup, preload only when sound is enabled and
never loop. One sampled UI voice plays at a time, with 80 ms input throttling and
0.13 default gain; the Clicks volume slider adjusts UI samples independently (0–0.26 gain), and Test
click previews the adjustment sound. Missing samples show an unavailable message.
The context stays running while sounds are enabled and the view is active, even
between sounds, to avoid suspend/resume races. Mute/pause/hide still suspend it.
Unloaded/late clicks are skipped rather than replayed. Sample files and
the included CC0 license/source record are under
`META-INF/resources/audio/nuclear-power/kenney-ui`. Original pack:
https://kenney.nl/assets/ui-audio; downloaded from the Calinou/kenney-ui-audio mirror
because the original download returned 403. No external audio request occurs at runtime.
Continuous operating hum has been removed. Routine operation is silent after the
brief Start/Stop cue; cues peak at 0.0004. Warning and critical alarm sounds retain
their separate escalation. Mute, pause, hidden views and detach stop all cue and
alarm nodes immediately.
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

- Heat reserve starts at zero internal units, follows generation with a 40-second
  response and decays over a 240-second response after shutdown. Displayed retained
  heat is reserve × 1.5 MW. Initial fuel temperature is 30 °C.
- Cooling margin compares a 1200 MW nominal cooling scale, reduced by actual flow
  and core damage, with generation plus retained heat. Fuel temperature integrates
  heat input minus cooling with bounded rates. Poor cooling keeps the coolant
  reading coupled to retained fuel heat rather than dropping immediately to ambient.
- Rod imbalance creates local temperatures. Above 410 °C, exposure accumulates;
  above 450 °C and after 15 seconds of exposure, channel damage accumulates.
  A local peak above 470 °C trips generation in Auto. Cooling failure can continue afterward.
- Meltdown latches only after a channel reaches 100% damage with fuel at least
  650 °C. Its visual progression then ramps with a 12-second response.
- Pump stress comes from sustained requested/actual flow mismatch. Stress above
  60% accumulates wear, progressively reducing available flow; speed alone does not
  damage a healthy pump. All state freezes with simulation pause/hidden-view suspension.

## Layout and appearance

The console uses the full feature-window width with no fixed maximum width. At
container widths of at least 1400 px, it has three columns:

- left: stacked temperature, thermal output, electrical output, flow and rod cards,
  followed by the collapsed fault/incident tools; equipment details live in dialogs;
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

## Process animation

The schematic uses the shared visibility-aware frame loop for illustrative motion:

- Turbine blades show projected rotation and the generator has a centered rotating
  rotor. The separate simulated shaft-speed read-back drives visual rotation, with
  startup acceleration and coast-down after shutdown. An open breaker can leave
  the shaft turning while electrical output is zero. Rotation is independent of the
  steam-particle temperature threshold. This is not a measured RPM.
- Steam particles follow the steam pipe toward the turbine; their visibility and
  travel rate use secondary temperature and actual circulation. Condenser droplets
  follow the same heat/flow activity. Cold circulation still shows water flow but
  no steam. With no flow, particles stop and disappear.
- The exchanger has traveling heat highlights and cross-circuit heat arrows driven
  by the temperature difference and actual flow in both loops. Fluids stay separate.
- Side-view fuel colors and core-map fuel glow follow local simulated temperatures.
  Retained fuel heat keeps the core glowing after shutdown; uneven channels remain
  visible. Meltdown gradually slumps the fuel, adds molten drips and grows a gently
  shifting molten pool. No explosion or random plant-state changes are introduced.
- An auxiliary-power strip identifies turbine, grid or diesels and animates the
  energized route into the pump bus. Loss of AC dims the bus; source restoration
  does not restart the reactor. Existing diesel cutaways retain their own motion.

Cold startup begins with inserted rods, no turbine rotation, steam or fuel glow,
with both pumps stopped and no coolant circulation. Explicitly start both pumps
and wait for actual motor speed and flow in both circuits to reach at least 20%;
then pressing reactor Start lets the
existing simulated rod, heat, flow and output signals bring each visual into activity.
Stopping reverses generation activity while retained heat and cooling remain visible.
New/reset sessions clear visual rotor inertia. Pause/hidden/detached views freeze
motion, and reduced motion retains static state/color indicators while data updates.
All colors support the feature's light and dark themes.

## Controls

Controls:

- Start/Stop: normal startup or cooldown, maintaining circulation settings and
  individual operating rod targets. Start requires both pumps explicitly enabled, actual motor speed and flow of at least 20% in both circuits, AC supply, and a
  temperature below the demo reset limit.
- Load setpoint: sets requested output and corresponding rod insertion.
- Bank Insert/Withdraw: moves every rod's own target by five percentage points,
  retaining offsets until travel limits are reached. Load setpoint intentionally
  applies a uniform bank target. Mean actual insertion is displayed separately.
- Individual rods: select a numbered channel or use the rod selector, then adjust
  its insertion slider or one-percentage-point buttons. Only that rod changes.
- Pump A/B: enable/disable and independently adjust speed targets from 0 to 100%.
  Actual speed/flow ramps gradually; disabling preserves the speed setting. Pump
  rotation follows motor speed and each circuit's coolant animation follows flow, including
  coast-down, with continuous phase and an explicit local SVG rotation pivot.
- Emergency shutdown: latches a simulated trip and inserts rods. Restart requires
  **Reset trip**, then **Start**. In Auto, low primary flow or high core temperature can also
  trigger the illustrative protection sequence. Reset trip requires all rods at
  least 99.5% inserted, both enabled circuits with target/actual speed at least 20%,
  and core temperature below the demo limit of 335 °C. These are arbitrary simulation
  limits, not real nuclear operating instructions. A checklist explains readiness.
  Reset trip preserves settings, elapsed time, history, and logs and leaves the
  reactor stopped. Reset demo remains available to replace the whole session.
- Acknowledge alarms: acknowledges existing alerts without hiding active conditions.
  New faults require their own acknowledgement.
- Pause/Resume: freezes/resumes simulated time and animations.
- Reset demo: restores the initial stopped state, event log, and trend history.

Manual mode leaves protective shutdown to the operator: low primary flow, high
coolant temperature and fuel hot spots still raise alarms and accumulate damage,
but do not insert rods or stop generation automatically. Use **Emergency shutdown**
or **Stop** explicitly. Switching to Auto restores the protective trips on the next
simulation tick; switching modes never clears an existing trip. Irreversible
meltdown still ends generation and requires **Reset demo** in either mode.

## Component consoles and operating exercises

Heat exchanger, both pumps, turbine/generator and condenser open independent,
movable nonmodal windows via pointer or Enter/Space on their outlined equipment.
Requested inputs and actual read-backs remain separate. The underlying process stays
usable; windows retain positions, clamp to the viewport, close with ×/Escape and
close on hide/detach. Pause keeps them available for inspection. Each console has
live readings, an animated valve/flow illustration, causal explanation, local
120-sample trends with per-series ranges and operator-change markers, disturbances,
operating hours, retained wear and a bounded maintenance history. The reactor's
former detail card is now a collapsible reading section inside the core-map window.

- Pumps: start/stop, speed, ±25 percentage-point speed trim and discharge valve.
  Actual motor speed is distinct from flow: restricting a valve can reduce flow
  without reducing shaft speed. AC supply remains authoritative. Speed is Auto-owned
  while running unless the operator explicitly enables the pump's manual override.
  Release resumes regulation through the existing actual-speed inertia; enable state
  is never silently changed by Auto. Other tuning remains operator-owned while Auto
  compensates through available reactor/pump controls.
- Heat exchanger: primary bypass, secondary valve, effectiveness and fouling.
  Transfer is the product of remaining non-bypassed flow, valve opening,
  effectiveness, unfouled fraction, disturbance and wear factors. Restrictions reduce
  heat removal, affect secondary temperature and electrical output, and can heat the
  core. Fault-free cooled/stopped service removes fouling; running adjustments cannot
  decrease accumulated fouling to bypass that requirement.
- Condenser: cooling-water valve and 0–60 °C inlet temperature. Warmer water and
  restriction reduce heat rejection, cooling margin and available generation.
- Turbine: steam admission, governor response (0.1–30 s), load ramp (1–400 MW/s),
  manual generator breaker and separate turbine trip/reset/Start. Shaft speed has its
  own simulated inertia and can remain nonzero with zero electrical output. The
  breaker closes only with shaft speed ≥90%, cooling available, no turbine trip,
  active fault, severe wear or meltdown. Auto may establish low unloaded shaft speed
  with an open breaker but never closes it. A turbine trip closes admission and opens
  the breaker. Reset requires cooled/stopped/fault-free conditions and shaft ≤5%;
  it leaves admission closed and breaker open until explicit turbine Start/tuning.
  It never resets the reactor trip or restarts the reactor.

Component disturbances have adjustable strength and period, a one-shot nudge,
continuous periodic fluctuation, progressive deterioration and explicit clearing.
Clearing restores the restriction while preserving wear/damage. Component service
requires the same cooled/stopped/fault-free conditions as core repairs; it preserves
hours, retained heat, fuel and trip state. Global Repair components also services
these components after all faults are cleared. Fuel replacement is blocked by
component faults/wear as well as the existing core checks. Maintenance and fuel
replacement events remain inspectable for this session; Reset demo clears them.

**Objectives & scenarios** opens a separate control-room window with a startup
checklist, maintenance journal and three objectives: reach the selected electrical
MW target, recover cooling, or maintain powered circulation through grid loss.
Success must hold for 20 simulated seconds. Starting an objective never starts or
repairs the plant. Diagnostic scenarios present falling flow, warming return,
uneven output or interrupted external supply as symptoms; inspect equipment or use
Reveal cause to identify the initiating cause. Their faults require explicit recovery.

The authoritative `electricalOutput` read-back includes transfer restrictions,
condenser cooling, turbine admission, breaker/trip and any load ramp. The controller,
auxiliary supply, charts, incident report and instrument use that same value.
Valve handles and bypass flow follow actual positions, not just requested settings.
All component constants/rates and shaft percentages are illustrative demo values.

## Automatic regulation and fluctuations

**Auto** has selectable **Core temperature** and **Electrical output** objectives.
Electrical output regulation is the initial and Reset demo default (200 MW electric).
Core temperature regulation remains selectable. It holds configurable core temperature
(40–330 °C; default 286 °C) and circulation (25–100%; default 85%) targets.
A one-second illustrative regulator uses
feed-forward plus proportional/integral temperature correction to adjust the rod
bank, retaining individual offsets where travel allows. Independent feedback adjusts
both pump speed targets. It does not automatically re-enable an operator-disabled
pump. Bank/load and pump-speed sliders are disabled during automatic running; individual
rod sliders require an explicit override. Pump enable buttons, Stop and Emergency
shutdown remain available. During a trip, pump speeds
can be changed to restore circulation. Returning to Manual holds current settings.

Small, bounded deterministic waveforms disturb heat, output and each loop's flow.
**Fluctuations: On/Off** lets the user compare disturbance response. The automatic
controller regulates the drifting process rather than freezing displayed values.
The model is deliberately simplified and does not solve reactor/neutron physics.
Trips inhibit regulation. Resetting a trip never triggers automatic restart; an
explicit Start is required even when Auto remains selected.

For grid-style generation control, the initial **Auto → Electrical output** mode uses
**Output target** (0–400 MW electric; default 200). The target
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

### Individual rod overrides during Auto

Select a control rod in the floating core map and enable **Manual override in Auto**.
Its slider and one-percent buttons then hold that rod's requested position while Auto
continues to regulate the remaining rods. A dashed amber channel border identifies
the override. Actual insertion still moves gradually, so the process can fluctuate.
Large imbalances can create local hot spots and invoke the existing protective response.

The controller accounts for fixed override targets when calculating the remaining
bank's insertion, preserving offsets until travel limits. It reports **Manual overrides
limit regulation** when the available rods cannot reach the requested bank mean and
**All rods under manual override** when none are free. Integrals do not wind up against
these limits. Unchecking the override returns that rod to Auto without jumping its
actual position. There is no timeout or silent takeover. Stop, trip and mode changes
release overrides; Stop/trip always insert every rod. Bank/load controls remain blocked
in Auto. Snapshot rod commands include `owner: AUTO|MANUAL` independently of read-back.

## Fuel exposure and manual replacement

Fuel assemblies (`FA-01` onward) are separate inventory objects from control rods.
They start with deterministic, staggered reserves of 36–99%, so they do not expire
together. Energy/exposure-based use follows generated thermal load, nearby control-rod
positions and a bounded spatial factor. Shutdown and pause consume no further fuel.
The broad principle is that [burnup tracks energy produced from fuel](https://www.nrc.gov/reading-rm/doc-collections/fact-sheets/bg-high-burnup-spent-fuel).
This feature's reserve/effectiveness equations and time scales are an accelerated toy
model, not isotope tracking, a validated neutronics calculation or real fuel lifetime.

Select an amber assembly in the map to see remaining reserve, effectiveness, accumulated
demo full-power hours and replacement count. **Fuel exposure speed** offers 1×, 10× and
60×; it changes fuel use only, leaving thermal dynamics and simulated time unchanged.
At 1×, a fresh assembly has six demo full-power hours of nominal reserve, adjusted by
its channel's history. Effectiveness declines with reserve; zero reserve contributes
zero capability. Aggregate capability is normalized to the initial mixed core and capped
at 115% after fresh replacements. The retained manual startup load is 64%; Auto uses its selected target after Start.

Manual output falls as fuel is used. Auto withdraws available rods to compensate, then
reports **Fuel capability** if the remaining fuel cannot satisfy the target. Per-assembly
20%/depleted transitions enter the journal; an active low-fuel alarm remains until the
affected assemblies are replaced. **Automation never replaces fuel.** The map's **Replace all fuel** action manually
renews every assembly together, using exactly the same readiness checks as selected
replacement. The operation is atomic; it does not start the reactor or clear heat,
damage or a trip. A staggered removal/insertion animation illustrates each changed
assembly, and a status message confirms completion. Single replacement uses the
same animation. Inventory is updated immediately; animation is cosmetic, pauses
with the simulation and clears when the map closes/hides; reduced motion uses an
immediate static update.

**Replace selected fuel** changes only the selected assembly to 100% reserve, zero
exposure and an incremented replacement count. It requires a stopped reactor, all rods
at least 99.5% inserted (requested and actual), coolant below 150 °C, peak local fuel
below 200 °C, no injected fault, no retained damage and no meltdown. Readiness reasons
are shown beside the button. The trip latch, retained heat, other assemblies and control
settings remain intact. Component repair never renews fuel. Reset demo resets inventory.
These are illustrative UI interlocks, not a real refuelling procedure.

## Auxiliary power and backup generators

The header always identifies the source that powers coolant pumps. Click it to open
**Auxiliary power & backup generators**, with a live source path and diesel cards.
It uses the same movable, nonmodal top-layer window behavior as the reactor core map:
drag its title bar or focus the title bar and use arrow keys, with viewport clamping
on movement/resize. Close with × or Escape; its position is retained when reopened.
Both windows can stay open together, and the underlying process controls remain
available. Reopening or dragging a window brings it forward. Escape closes the
focused floating window (or the most recently opened one when focus is outside).
Hiding/detaching the feature closes the windows. The generator window can remain
open while paused, with its simulated state and visual motion frozen.

Each diesel has an SVG engine cutaway, radiator, moving pistons, drive shaft,
alternator fan, fuel tank and AC output line in the process diagram's visual style.
Starting engines turn slowly; running engines animate faster; stopped/empty engines
remain still. These are illustrative animation rates, not measured RPM. The tank
fill follows actual simulated fuel reserve. A lit output terminal and moving power
line appear only when that diesel is supplying bus load, so an unloaded running
engine is visually distinct from one powering pumps. Text and accessible drawing
labels identify standby, cranking, running/unloaded, supplying and empty states.
Both themes have dedicated drawing colors. Motion uses the existing visibility-aware
animation loop, stops with pause/hide/detach, and stays static with reduced motion.

Pump power is independent of reactor generation: after shutdown it normally comes from
the external grid. The broad source arrangement is informed by the [NRC emergency power
system description](https://nrcoe.inl.gov/SysStudy/EPS); this demo does not reproduce a
particular plant's electrical distribution or emergency cooling design.

Source priority is turbine auxiliary feed, external grid, then available backup diesels.
The turbine must be running, connected and producing at least pump demand plus 1 MW.
Normal sources have a fictional 20 MW auxiliary capacity. Pump demand is 0.2 MW of
base load plus 3.5 MW (primary) and 2.5 MW (secondary) multiplied by requested speed
cubed. Each diesel has 8 MW capacity, enough for the two simplified loops. Generator
output and the grid-controller target remain **gross electrical generation**, separate
from this auxiliary-load indication; no net-export model is implied.

The dialog permits grid disconnection/restoration and turbine-feed isolation/reconnection.
Each diesel has automatic standby, manual Start/Stop, remaining fuel, startup countdown,
actual bus load/capacity and manual refuelling. Armed diesels start when both normal
sources are unavailable, taking eight simulated seconds. Initial fuel differs (A 82%,
B 95%). Nominal full-load endurance is 30 simulated minutes; an unloaded running engine
still consumes 20% of its full-load rate. Exhaustion stops the engine. Fuel below 15%
raises an alarm. No controller refuels a diesel.

Manual Stop disables that engine's automatic standby so the command persists. Re-arm
standby explicitly when wanted. Refuelling requires a stopped/non-starting engine.
Diesels remain running, unloaded, after normal power returns until manually stopped.
One available diesel carries both pumps; two share the load. Pump enabled states and
speed requests remain separate from energized/actual speed. Without AC the pumps coast
down and coolant animations slow, even if their requested enable state is still on.
Restoring power ramps the requested pumps back up but never resets a trip or restarts
the reactor. Instruments have an independent, idealized simulated DC supply; battery
capacity and charging are not modeled.

Source transitions, starts/stops and refuelling enter the journal. Total AC loss begins
an incident, raises an alarm and interacts with existing low-flow protection, retained
heat and progressive core damage. Backup availability does not heal damaged pumps.
Supply, diesel fuel and startup time freeze with the rest of the paused/hidden simulation.

## Expanded live trends

Click the Live trends panel (or focus it and press Enter/Space) to open a larger
movable, nonmodal chart window, up to 1200 px wide. Both charts share the current
two-minute history, temperature scale, output mode and electrical target; they
update together. Drag the title bar or use its arrow keys, close with × or Escape,
and reopen at the retained position. The window clamps to narrow viewports and
closes when the feature is hidden or detached. Pause freezes its data while keeping
the chart available for inspection. Both themes use the existing chart palette.

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
- `src/main/frontend/nuclear-power-fuel.js` owns separate per-assembly inventory,
  uneven exposure, capability, acceleration and explicit replacement interlocks.
- `src/main/frontend/nuclear-power-components.js` owns component settings/read-backs,
  transfer/cooling factors, turbine shaft/breaker, disturbances, wear and exercises.
- `src/main/frontend/nuclear-power-equipment.js` renders the movable consoles and
  exercises, settings ownership, local trends and maintenance journal.
- `src/main/frontend/nuclear-power-audio.js` owns quiet Start/Stop cues;
  the main element retains alarm scheduling and shared-context lifecycle.
- `src/main/frontend/nuclear-power-supply.js` owns auxiliary-source selection,
  standby/manual diesel startup, fuel, capacity and pump power availability.
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
node --test src/test/frontend/nuclear-power-failure.test.mjs
node --test src/test/frontend/nuclear-power-resources.test.mjs
node --test src/test/frontend/nuclear-power-components.test.mjs
node --test src/test/frontend/nuclear-power-audio.test.mjs
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
pass. Thirty model/motion/failure/resource tests cover the controls and regulation, including
a two-minute settled-error window and zero-flow behavior with disturbances enabled.
Failure tests cover warning time, intervention, accumulated damage/repair, local hot
spots, automatic protection, sustained cooling loss, slow meltdown and report retention.
Resource tests cover unequal fuel use, acceleration, manual-only replacement, Auto
compensation/limits, override ownership, grid transfer, standby startup, persistent manual
stop, fuel exhaustion/refuelling and blackout recovery without automatic reactor restart.
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
pause/minimize/detach/reduced-motion cleanup. Generator checks also cover concurrent
floating windows, background-control focus, pointer/keyboard movement, retained position,
Escape/close, narrow-viewport clamping, centered animated fans, stopped/reduced-motion
visuals and hidden-view cleanup. Use the disposable local profile
(`./run-local.sh 18080`, admin/admin) for authenticated application checks.
