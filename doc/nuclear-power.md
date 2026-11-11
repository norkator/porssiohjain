# Nuclear Power

**Nuclear Power** is the standalone English-only React/Vite app in `nuclear/`,
served at `https://nuclear.porssiohjain.fi` after DNS and hosting are configured.
The Vaadin desktop and Start menu retain its pixel-icon shortcut and open that URL
in a separate tab. The old `/nuclear-power` Vaadin view and its frontend assets have
been removed from the Java application. Both locale bundles deliberately retain
the name **Nuclear Power**. No account login or backend connection is required by
the standalone simulation.

React mounts the retained `nuclear-power-console` custom element, preserving its
renderer, isolated styles and browser lifecycle. The independent npm project,
tests, public audio and Nginx container live together in `nuclear/`.
`.github/workflows/nuclear-container.yml` verifies it on pull requests and publishes
`ghcr.io/norkator/porssiohjain-nuclear:latest` for amd64/arm64 on relevant production
pushes and manual runs. The workflow does not configure DNS/TLS or deploy the image.
See [local development and deployment](../nuclear/README.md).

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

The 3D reactor has a flat RBMK-inspired tiled channel deck, a rear spent-fuel
pool with 35 initially empty display racks, and an overhead handling crane. The
appearance does not change the fictional two-loop model. Deck caps, 49 fuel
assemblies and twelve control rods share `CORE_CHANNELS` positions/identities.
The caps stay stationary; actual rod movement and aqua collars remain visible
inside the open-front upper guide chamber beneath them. The crane bridge,
trolley, hoist and grabber animate one representative assembly for each
successful selected or batch fuel replacement: align, lower, lift clear of
the deck, traverse to the pool, lower/release and return to park (10.5 s).
Only the pickup cap opens; regulation keeps all caps fixed. Fuel renewal
remains immediate through its existing cooldown checks. Each replacement
stores one representative batch (with its actual replaced assembly count)
in one of 35 racks. Batches remain until manual dispatch; a full pool blocks
further UI replacement until a rack is freed. This is not a complete physical
spent-fuel inventory or pool-temperature model. Repeated commands restart
one illustration, without queueing the entire core. Starting/meltdown cancels
an unfinished illustration; pause, hiding, tab/view switches and detach freeze
it. Returning resumes; reduced motion/fallback places the sample immediately.
Reset demo clears active motion and samples, including at T+0. The animation
never changes heat, damage, trips, settings or the stopped state. See [the continuation plan](../nuclear/FUEL-HANDLING-PLAN.md)
for the user references, implementation checkpoint and future gameplay steps.

Click the pool or **Spent fuel ↗** to open its movable, nonmodal window, also
available from the schematic/WebGL fallback. Unique session batches show
source channel, represented count, rack and a 60-second simulated cooling
countdown starting at placement. Cooling follows plant time across tabs and
freezes with pause/hide/detach. Reduced motion skips crane motion but never
skips cooling. Explicit selected/all-cooled dispatch to **Long-term storage**
or **Reprocessing** frees racks and removes their 3D fuel, without changing
core fuel, retained heat, damage, settings or trips. Invalid routes, missing/
cooling batches, duplicates and crane-occupied racks cannot dispatch. The
journal records destination/count; a bounded 20-shipment history and total
dispatched count remain in the session. Cost is zero; future gameplay can
extend the shipment cost contract. Reset demo clears pool/cooling/history.
The pool shares dialog dragging, keyboard movement, clamping, focus return,
Escape and close-on-hide/detach behavior with the component windows.

The overview defaults to a low-poly 3D cutaway, with a retained SVG schematic
available through the view selector. Both include moving rods and fuel channels,
separate primary and secondary circuits, animated pumps and coolant flow, a heat
exchanger, a turbine/generator, and a condenser. Click or keyboard-activate outlined
equipment to open its movable component console. The reactor opens the core map;
its former Equipment detail readings are inside that window. The main view has no
Equipment detail card. The 3D scene supports bounded orbit, pan and zoom; the SVG diagram can be zoomed and
scrolled. Controls and instruments adapt to the browser viewport's width.

Click the reactor vessel (or **Core map ↗**) to open the core map in a movable,
nonmodal floating dialog above the process (up to 900 px wide, with a
650 px core drawing and viewport clamping). The process schematic stays
visible and interactive underneath, using the same model and rod positions. Drag
the dialog title bar to move it; when the title bar has keyboard focus, arrow keys
move it in 16 px steps. The frame is clamped to the browser viewport on movement
and resize. Close with × or Escape. The dialog closes when the feature is hidden
or detached; rod selection, settings, and its position remain in the retained view.
It uses the native manual-popover top layer with `role="dialog"`, so it can float
above the process without making the process view inert.

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
audio. Sound settings belong to this view instance; bundled audio files load only after opt-in.
The same switch enables distinct quiet ascending Start and descending Stop cues,
only when that command changes the reactor's running state. Blocked/repeated commands
produce no cue. Kenney UI Audio samples provide quiet on/off switch clicks and a short click when
an adjustment is committed (not on every slider movement). They share Enable plant
sounds and mute/pause/visibility cleanup, preload only when sound is enabled and
never loop. One sampled UI voice plays at a time, with 80 ms input throttling and
0.26 default gain (100% of the bounded UI-sample range). The Clicks slider and
Test click button have been removed. Missing samples show an unavailable message.
The context stays running while sounds are enabled and the view is active, even
between sounds, to avoid suspend/resume races. Mute/pause/hide still suspend it.
Unloaded/late clicks are skipped rather than replayed. Sample files and
the included CC0 license/source record are under
`nuclear/public/audio/nuclear-power/kenney-ui`. Original pack:
https://kenney.nl/assets/ui-audio; downloaded from the Calinou/kenney-ui-audio mirror
because the original download returned 403. No external audio request occurs at runtime.
Opt-in operating audio now uses two original seamless 16-second stereo WAV loops
under `nuclear/public/audio/nuclear-power/plant-state`: steady steam/turbine/generator
operation and a rough fault texture. Actual running reactor power and turbine
shaft speed drive intensity, bounded to 0.10 gain. Cold sessions are silent; Stop
allows audible shaft coast-down. Stress, damage, overheating, trips and actual
component disturbance values select the fault texture independently of alarm
acknowledgement, with approximately one-second crossfades. Requested MW or fault
selection alone never controls playback. Both decoded loops share phase and are
reused across state changes; missing files show an unavailable status. Late loading
only stores buffers and never starts audio independently. Start/Stop cues retain
their 0.0004 peak and do not interrupt operating loops. Warning and critical alarm
sounds retain separate escalation. Mute, pause, hidden views and detach stop all
loop, cue and alarm nodes immediately; Reset demo returns to cold silence.
Machinery audio is scoped to the selected tab. **Reactor overview** mixes the
reactor operating loops/Start/Stop cues and independent actual-state diesel
startup, running and shutdown sounds. **Maintenance** stops all machinery
immediately; UI clicks and alarms remain shared. Reactor command cues never
interrupt diesel voices. Returning resumes current operation without replaying
past starts/stops. Opt-in, bounded gain, mute/pause/hide/detach and Reset cleanup
remain unchanged.

Original stereo PCM WAVs under `nuclear/public/audio/nuclear-power/diesel` provide
an eight-second starter/ignition/acceleration cue, a seamless twelve-second exhaust,
clatter and fan loop, and a 3.2-second shutdown. Read-back states drive playback,
including automatic starts, cancelled starts and fuel exhaustion. Running gain
follows actual bus load (0.035 unloaded to 0.05 loaded per engine). The two engines
have independent voices; startup resumes at the current simulated countdown after
pause/tab changes, while an already-running engine resumes directly into its loop.
Past shutdowns and blocked/repeated commands are never replayed. Mute, pause, hide,
detach and Reset demo clear all diesel voices; late loading never starts playback
independently. Generation script, source notes and asset checks live in `nuclear/`.

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

The **Maintenance** overview tab collects retained core/pump damage, equipment wear
and exchanger fouling in a live list, with a count visible on the tab. Core damage
is grouped with its affected-channel count; temporary stress and depleted fuel
are separate. **Inspect** opens the existing movable component/core console.
**Repair all** services the listed components together through the existing
cooled repair path. A shared readiness checklist shows Stop, fault clearance,
coolant below 150 °C, peak fuel below 200 °C and no meltdown, with current
temperatures. **Clear active faults** explicitly clears injected plant/component
deterioration without healing damage or changing other periodic disturbances.
Repairs preserve retained heat, fuel reserves, settings, operating hours and both
reactor/turbine trip latches. They cannot start equipment or close a breaker.
The list clears as repairs complete; Reset demo clears maintenance feedback.

Inspecting the reactor now exposes a damage summary and every affected local
zone directly above the core map. Entries identify the nearby CR rod, retained
damage percentage and local fuel temperature; selecting one selects that rod
and its existing detail readings. All nonzero retained damage has a red map
border, including values below 1%. The dialog also offers **Repair all damage**
through the same core/component bulk repair path, with explicit blocking
reasons and current temperatures. Repairing removes the list entries and red
borders while preserving fuel and trips; fuel replacement remains separate.

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

The console uses the full browser width with no fixed maximum width. At
container widths of at least 1400 px, it has three columns. Regulation, plant
sound enable/mute, theme, simulation information and session clock share a header
card beside the brand/title, leaving more room for the main overview:

- left: stacked temperature, thermal output, electrical output, flow and rod cards,
  followed by the collapsed fault/incident tools; equipment details live in dialogs;
- middle: tabbed reactor/auxiliary overview beside the operator console;
- right: live trends above the filterable event journal.

The operator console uses beveled metal mounting plates, round green/red Start
and Stop pushbuttons, engraved labels and pilot lamps. Lamps use actual running
state rather than command availability: Start remains visibly lit while the
reactor runs even though the button is disabled. Text distinguishes READY,
INTERLOCKED, RUNNING, STOPPED and TRIPPED. Pump lamps follow actual motor speed,
with amber stress and red retained damage/wear indication; an amber Reset trip
lamp marks completed recovery readiness.

The load setpoint and both pump speed targets share a metal regulator rail,
grooved slider handle, engraved 0–100% scale and amber percentage display. Load
also retains its fine-adjust buttons and AUTO/MANUAL/TRIP LOCK ownership label.
The fill/readout follow a live adjustment until commit without changing the
requested-versus-actual distinction. Native pointer/keyboard behavior, Auto
ownership, individual pump overrides, trip recovery and speed/flow read-backs
remain intact in both themes.

The Start/Stop caps have restrained illuminated lenses and a soft nearby halo: green
Start while running and red Stop while stopped. Emergency shutdown has a soft
red locator halo while armed and a much brighter red cap/halo while latched.
These are steady lights with no flashing or extra timers, retained when paused
and under reduced motion. Pump cards use separate recessed ON/OFF rocker
switches with native keyboard activation and switch semantics. Their position
shows the requested enable state; the adjacent actual-state lamp/status can
remain RUNNING/COASTING after OFF until the motor slows. Pump name, circuit,
speed target, actual read-back, flow and stress/damage indication are retained.

Emergency shutdown is a red mushroom pushbutton on a hazard-marked mounting
plate, behind a transparent hinged plastic cover. First select **Lift cover**,
then press the exposed button; **Close cover** blocks manual access again.
The native cover toggle comes before the button in keyboard order. Enter/Space
opens the cover, then Tab reaches the button. Cover movement never changes plant
state or clears a trip, and automatic trips work with either cover position.
New sessions and **Reset demo** close the cover; pause and reduced motion skip
the lid transition. Its head remains depressed, its red trip lamp stays lit, and it reads
**TRIP LATCHED** for every latched reactor trip, including automatic trips.
The disabled latched button retains full visual contrast. Pressing it again
cannot clear the trip. Only successful explicit **Reset trip** releases the
head and extinguishes the trip lamp; the reactor remains stopped until Start.
Start/Stop, protection thresholds and recovery interlocks are unchanged. Native
keyboard/focus behavior, pause and reduced motion are preserved in both themes;
the lights and physical button position introduce no animation timer.

The default reactor overview fits 1920×1080 and 2560×1440 screens without page scrolling.
The thermal output, electrical output and primary flow cards include heat wisps, a
rotating generator symbol, flowing coolant markers and traveling meter highlights.
Their phase and intensity follow actual reported output/flow, remain still at zero,
and share pause, visibility, detach and reduced-motion cleanup. Numeric read-backs
are not interpolated or substituted with requested settings.
Long event lists scroll inside the journal. Narrower/resized windows progressively
use two columns and then stacked sections, with mobile-sized controls retained.

Dark is the default. The header's **Light theme** switch changes this feature's
panels, controls, diagrams, chart and logs, including the React page background.
The browser preference uses `porssiohjain.nuclear-power.theme` in local storage;
unavailable storage falls back to dark and never prevents switching themes. Theme
changes do not reset simulation state or alter control settings.
Light mode uses darker secondary labels and full-opacity disabled controls, so
Auto-owned actuator settings remain readable while disabled.

## 3D reactor overview

The main overview opens as an isometric, low-poly Three.js scene, hosted inside the
retained custom element. Vessel and heat-exchanger cutaways expose the internal
fuel, control rods and transfer tubes. Separate primary and secondary pipework,
pump impellers, turbine blades, generator and condenser share the existing plant
state. No additional physics or command path is introduced.

The taller viewport starts at 120% zoom. Drag to orbit within bounded angles, use
the mouse wheel or +/− buttons to zoom (65–300%), and select **Reset view** to restore
the initial camera, including its center. Hold the mouse wheel and drag to pan
in the camera plane; Shift+drag also pans. Panning is bounded to eight scene
units in the unified 3D view. When the canvas has keyboard focus, arrow
keys orbit, Shift+arrow keys pan, +/− zoom and Home resets. Equipment can be selected directly through canvas picking or
through semantic HTML buttons with live readings. They open the same movable
component/core dialogs and receive focus again when those dialogs close.

**Schematic** returns to the original SVG process view without resetting the
simulation. Both themes, actual rod insertion, motor/shaft coast-down, coolant
flow, temperature-dependent steam/heat, retained fuel glow, molten drips and the
growing melt pool are preserved. The scene uses the existing visibility-aware frame
loop; pause and reduced motion freeze illustrative motion, hidden views suspend
work, and detach disposes GPU resources and observers. Reconnection recreates the
renderer around the retained simulation. Unsupported/lost WebGL 2 falls back to
the usable SVG view. Stationary equipment surfaces and rotor blades are batched
by material to reduce draw calls.

The main panel has **Reactor overview** and **Maintenance** tabs, with keyboard
arrow/Home/End navigation and retained plant state. Reactor overview contains the
unified 3D plant and SVG selector. Small diesels, grid, bus and electrical routes
share the plant scene. Maintenance stops rendering without changing simulation
state. The left Pump power source panel sits above Grid controller. Its source
controls and each diesel's own dialog remain usable in the SVG/WebGL fallback.
There is no Auxiliary power tab or header EXTERNAL GRID/source button. Component,
core, pool and electrical windows can coexist and retain the shared lifecycle.

## Loop temperatures, valves and flow

SVG and 3D pipework, bypass and moving markers share one absolute illustrative
temperature palette (blue/teal at low temperatures, yellow/orange as warmer, red
above 335 °C). Equal temperatures have equal colors regardless of hot/return role.
The legend shows the scale. Structural equipment paint remains distinct from fluid
temperature. The reactor label shows core inlet and outlet; pump/exchanger/condenser
labels expose the local return temperatures. Numeric SVG readings remain high
contrast and the existing persistent over-temperature indication remains red.

The primary return calculation now includes actual exchanger/condenser heat removal,
both flows, generated power and retained heat. A 100% actual bypass or unavailable
heat rejection removes the nominal primary temperature drop; read-back responds
gradually. The new `secondaryReturnTemperature` follows secondary outlet temperature,
actual condenser inlet/cooling factor and circulation, also gradually. Both are
explicit simulated signals. Closing condenser cooling removes its return cooling
drop. The bypass route shows flow only while its actual valve is open; exchanger
heat-transfer highlights also obey actual transfer factor.

Valve geometry is aligned to its local pipe axis, including vertical exchanger
valves. At 100% actual opening the lever is parallel to the pipe; at 0% it is
perpendicular, with intermediate angles following read-back. Main SVG and component
consoles use the same opening-angle rule. Requested settings still ramp gradually.

All 3D paths in a loop advect markers using one actual-flow-driven travel distance,
then map that distance into each path's length. Marker spacing is comparable across
short/long routes. SVG steam particles and dash motion use the corresponding shared
distance. Steam density/visibility remains temperature-driven, but steam-line and
return-line marker speed are no longer unrelated fractions of pipe length. These
are illustrative animation distances, not measured water/steam velocities.

## Process animation

The selected 3D scene, retained SVG schematic and instrument cards use the shared
visibility-aware frame loop for illustrative motion:

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
  Secondary-side steam puffs rise inside its cutaway and turn toward the steam
  outlet in both 3D and SVG views. Density/opacity and travel phase reuse the same
  secondary temperature/actual-flow steam signal as the outlet pipe. Cold
  circulation and zero secondary flow show no steam; retained hot circulation can
  keep producing visible steam after shutdown. Puffs never represent an external leak.
- The 3D vessel has moving reaction particles among the fuel channels and staggered
  per-bundle emissive pulses. They use actual running thermal power and local rod
  insertion, independently of shaft/output or retained-heat motion. Stop, trip and
  meltdown remove reaction particles; retained fuel heat keeps its existing glow.
  These are process illustrations, not added neutronics. The shared frame loop
  freezes particle travel and pulses on pause/reduced motion and suspends hidden
  views; particles are excluded from static batching and disposed with the scene.
- Side-view fuel colors and core-map fuel glow follow local simulated temperatures.
  Retained fuel heat keeps the core glowing after shutdown; uneven channels remain
  visible. Meltdown gradually slumps the fuel, adds molten drips and grows a gently
  shifting molten pool. No explosion or random plant-state changes are introduced.
- The electrical connection window's 2D power route identifies turbine, grid or diesels and
  animates the energized route into the pump bus. Loss of AC dims the bus; source
  restoration does not restart the reactor. Its 3D diesels show pistons, shaft/fans,
  actual fuel fill and electrical-output markers only while supplying load.

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
- Heat exchanger: a compact interactive SVG cutaway (up to 520 px wide) sits
  beside a right-hand local trend and collapsed details, fitting the default
  window without vertical scrolling at 1440×900 and larger. Below 850 px
  viewport width the columns stack. The narrow trend has its own plot bounds
  and retains range legends/operator markers. The cutaway replaces numeric setting fields. Drag
  the primary bypass, secondary valve, tube bundle or fouling deposits horizontally
  and release to apply one command; actual response continues through existing
  inertia. Arrow keys adjust 1% (10% with Shift), Home/End select endpoints, and
  part-selection buttons expose a native slider and ±5% buttons for touch/keyboard
  use. Dashed valve handles show requests separately from solid actual handles.
  Temperature colors, flow, heat arrows, secondary steam and deposits follow actual
  state on the shared visibility/pause/reduced-motion-aware animation loop. The
  cutaway scrolls horizontally on narrow screens while the controls fit the window.
  Disturbance strength/period also use sliders with readouts; trends, faults, service
  interlocks and the movable nonmodal lifecycle remain intact.
  Settings remain primary bypass, secondary valve, effectiveness and fouling.
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
(minimum 40 °C, default 286 °C, no upper entry cap) and circulation
(25–100%; default 85%) targets. Removing the old 330 °C input/model clamp does
not change the independent over-temperature alarms or Auto trip conditions.
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

### Simulated grid-controller requests

The **Grid controller** card beneath the left instruments offers an explicit
**Follow requests** checkbox and **Request now**. New/reset sessions select following
by default and retain the stopped Auto/Electrical output 200 MW initial target. This is a local,
deterministic dispatch exercise; there is no grid server or device connection.

With the feed enabled and the reactor running in Auto → Electrical output, the
first message arrives after 45 active simulated seconds, followed by one every
60 active seconds. Request now immediately delivers the next message and restarts
the interval. Requests alternate illustrative raise/lower changes and stay within
normal available capacity, capped at 300 MW with healthy equipment. Capacity uses
a 330 °C normal operating envelope and available pump circulation, with headroom
and five-MW rounding; it derates for exchanger/condenser/turbine restrictions,
pump trim/overrides, faults/wear, retained heat, fuel capability and fixed rod
overrides. It is independent of the operator's uncapped manual temperature field.
No request is delivered without usable circulation and generation capacity.

Each message coordinates the MW target, circulation target and core limit. Flow
is chosen according to requested load relative to available capacity (60–100%),
then expected conversion/cooling determines the needed core limit with 8 °C
control headroom. A healthy 240 MW request therefore adjusts both settings and
does not remain stuck at the old 85% / 286 °C default ceiling. The message retains
ID, elapsed time, direction, previous/requested MW, planned core/flow settings,
available capacity and explicit simulated source. Actual output follows the
existing regulator/actuators, with all changes recorded in the Automation journal.

The card distinguishes actual output, current target, last request, countdown and
limiting/waiting status and available dispatch capacity. The operating temperature
and circulation settings are owned by each incoming request; independent protection
thresholds, cooling/fuel capability and manual pump/rod overrides remain authoritative.
Messages cannot reset trips, start equipment, close breakers or change protection thresholds.
Countdown and request delivery suspend during Stop, trip/meltdown, pause, Manual
mode or temperature regulation. Hiding the console freezes simulated time as usual.
No request can reset a trip, start a reactor/turbine, refuel or repair equipment.
Resume after a stop/trip still requires the existing explicit operator actions.

Disabling the feed retains all current operating targets. Operator MW, temperature
and circulation edits apply immediately and are identified in the card; a later
grid message may replace them while following is enabled. Reset demo clears the
last message/sequence, restores following and holds the initial 45-second countdown
until the reactor is running and dispatch-ready.
Snapshots expose `commands.followGridRequests` separately from `gridDispatch`
message/countdown/status data and measured/simulated electrical-output signals.

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

Two small low-poly diesel cutaways (46% of their former standalone scale) sit
behind the turbine/generator in the main 3D plant. Grid, AC bus and energized
source-to-pump electrical routes share the same renderer, camera, picking,
batching, themes and GPU disposal. Actual engine state drives pistons/fans/shaft;
actual fuel drives tank fill, and actual bus load drives lamps/wire markers.
Stopped/empty engines remain still; running unloaded differs from supplying.

Click either engine or its label to open its own movable, nonmodal diesel window.
Each retains automatic standby, countdown, fuel/load, manual refuelling and a
single pump-style ON/OFF rocker with switch semantics. OFF disables standby;
starting/stopping/refuelling interlocks are unchanged. Actual state remains
separate from the run switch. Both diesel dialogs can remain open independently.

The compact **Pump power source** panel appears above Grid controller at left,
showing source/load and buttons for each engine. Its ↗ button, or the 3D grid/bus,
opens **Electrical supply & connections** with the retained 2D route. External-grid
availability and turbine-feed connection remain native checkboxes/switches with
pump-style rocker faces, keyboard activation and existing change handlers. The
former Auxiliary power view and top-right EXTERNAL GRID button are removed.

Scene info cards are 55% opaque until hover, keyboard focus or opening their
dialog; reduced motion removes their transition. They remain usable over the
process. The unified scene freezes on pause/hide/reduced motion and while
Maintenance is selected. Detach disposes one renderer; reconnect restores current
plant state. SVG fallback keeps the sidebar and all electrical dialogs usable.

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

The electrical connection window permits grid disconnection/restoration and turbine-feed isolation/reconnection.
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

Each page's custom-element instance owns its state. Reloading or closing the page
discards that simulation. Scrolling the entire console out of view or hiding the browser
tab suspends background simulation work without fast-forwarding on return. Detaching
the element clears its timer and observers. Reduced-motion preferences disable visual
animation while instruments and controls continue to work.

## Implementation and future measurements

- `nuclear/src/App.tsx` mounts the console through React, independently of Vaadin.
- `nuclear/src/simulation/nuclear-power.js` renders the native custom element, SVG
  schematic, controls, recent two-minute trends, and bounded event journal.
- `nuclear/src/simulation/nuclear-power-scene.js` owns the low-poly overview,
  bounded camera, picking, semantic labels, geometry batching and renderer cleanup.
- `nuclear/src/simulation/nuclear-power-electrical-scene.js` builds diesel cutaways,
  grid/bus and actual-state electrical routes in the main plant renderer.
- `nuclear/src/simulation/nuclear-power-electrical.js` supplies the left source panel,
  electrical connection window and independent diesel control windows.
- `nuclear/src/simulation/nuclear-power.css` is isolated in the element's shadow root.
- `nuclear/src/simulation/nuclear-power-light.css` supplies the optional light palette.
- `nuclear/src/simulation/nuclear-power-model.js` contains the deterministic, browser-local
  plant, individual rod/channel layout, controller, disturbances, alarms and log.
  Its snapshot separates `commands` (requested mode/objective, temperature/flow/MW
  targets, enable states) from
  reported `signals` and per-rod insertion. Signals contain value, unit, source,
  quality, and simulated elapsed time; that time is not a sensor timestamp.
- `nuclear/src/simulation/nuclear-power-condition.js` owns stored heat, local fuel zones,
  progressive faults, component stress/wear, damage, meltdown and incident retention.
- `nuclear/src/simulation/nuclear-power-fuel.js` owns separate per-assembly inventory,
  uneven exposure, capability, acceleration and explicit replacement interlocks.
- `nuclear/src/simulation/nuclear-power-components.js` owns component settings/read-backs,
  transfer/cooling factors, turbine shaft/breaker, disturbances, wear and exercises.
- `nuclear/src/simulation/nuclear-power-maintenance.js` renders the live damage/wear/fouling
  list, repair readiness and explicit bulk repair/fault-clear controls.
- `nuclear/src/simulation/nuclear-power-equipment.js` renders the movable consoles and
  exercises, settings ownership, local trends and maintenance journal.
- `nuclear/src/simulation/nuclear-power-audio.js` owns operating-state loops, UI samples and quiet Start/Stop cues;
  the main element retains alarm scheduling and shared-context lifecycle.
- `nuclear/src/simulation/nuclear-power-supply.js` owns auxiliary-source selection,
  standby/manual diesel startup, fuel, capacity and pump power availability.
- `nuclear/src/simulation/nuclear-power-temperature.js` maps local loop temperatures
  to the shared absolute SVG/WebGL palette.
- `nuclear/src/simulation/nuclear-power-grid.js` owns simulated grid messages with following selected by default,
  active-time cadence, readiness, requested MW changes and dispatch status.
- `nuclear/src/simulation/nuclear-power-motion.js` integrates speed-dependent impeller
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
# Desktop external-link and window-navigation regressions:
JAVA_HOME=/path/to/java21 ./gradlew compileJava test \
  --tests com.nitramite.porssiohjain.views.MainLayoutTest \
  --tests com.nitramite.porssiohjain.views.DesktopWindowNavigationTest

# Independent React app, from nuclear/:
npm ci
npm test
npm run build
npm run test:browser
# Optionally test an already running preview/container:
NUCLEAR_POWER_APP_URL=http://127.0.0.1:8080 npm run test:browser
```

Browser tests use Chrome at `/opt/google/chrome/chrome` by default; `CHROME_BIN` can
select another installed Chromium executable. Without `NUCLEAR_POWER_APP_URL`, a
temporary loopback server tests the actual production build in `nuclear/dist/`,
including the React entry point and bundled assets. Chrome uses software WebGL
for headless 3D coverage. Each run prints its temporary
screenshot/CSV artifact directory. No extra browser test dependency is required
beyond Node and Chrome. The simulation tests require no Spring application.

Migration checkpoint (2026-11-07): all 59 model/motion/failure/resource/component/audio
checks and 23 focused Java desktop tests pass, as do the React production build,
Chrome regression and Nginx container build/HTTP smoke checks. The clean Vaadin
production bundle contains no references to the removed console.
Browser coverage includes React startup, cold
start and controls, wide/mobile layouts, themes, floating dialogs, exports, animation
and pause/visibility/detach/reduced-motion cleanup. It also checks actual WebGL
rendering, canvas picking and semantic equipment controls, camera zoom/reset,
state-preserving overview/tab switching, the header controls, actual-driven
instrument motion, 3D diesels and their 2D source route, WebGL-loss fallback,
and renderer disposal/reconnection. No real MQTT/model devices are
connected.

The model tests cover independent rod positions, bank behavior, speed/flow inertia,
phase-preserving motion, automatic regulation under disturbances, categorized bounded
logging, normal restart, and trip recovery without losing settings. Browser checks
must also verify fixed pump hub centers over multiple frames, individual selection,
both view modes, control disabling in Auto, CSV export, mobile/container resizing,
wide-screen fitting and column ordering, theme default/persistence, electrical MW
tracking and limiting, floating core-map opening/movement/closing with the process
still visible, persistent overheat indication after acknowledgement, and
pause/visibility/detach/reduced-motion cleanup. Generator checks cover tab keyboard navigation, core-map coexistence, 3D
rendering/picking, fuel fill, start/stop/manual refuelling, loaded versus unloaded
read-back, camera controls, mobile layout, stopped/reduced-motion visuals and
hidden-view/GPU cleanup. Build the standalone app before running its browser
regression; a running Nginx container can be checked with `NUCLEAR_POWER_APP_URL`.

Grid/visual checkpoint (2026-11-07): 71 unit checks cover grid tracking, cadence,
pause/mode/stop/trip guards, temperature/rod/breaker ownership and independent
reaction/retained-heat phases. The browser regression also covers 3D/SVG exchanger
steam, vessel reactions, cold/no-flow/Stop behavior, pause/reduced motion, grid
message delivery and requested-versus-actual output.
