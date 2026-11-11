# Nuclear Power

The standalone **porssiohjain-nuclear** React/Vite app for
`https://nuclear.porssiohjain.fi`. It is an English-only, fictional two-loop reactor
control room. Every signal and command is simulated in the browser; there are no
backend requests, account login, MQTT connections, or device commands.

React mounts the existing `nuclear-power-console` custom element. Its shadow-root
styles, deterministic simulation modules, dialog behavior and audio
lifecycle are retained under `src/simulation/`. Each page owns its simulation;
reloading or closing it discards the session. Only the light/dark theme preference
is saved locally. The Vaadin desktop and Start menu open this app in a separate tab.

The reactor overview opens as a low-poly Three.js cutaway. Drag to orbit within
bounded angles. Hold the mouse wheel and drag to shift the center, or use
Shift+drag. Scroll or use +/− to zoom. **Reset view** restores the initial center,
rotation and zoom. Arrow keys orbit, Shift+arrow keys pan, +/− zoom and Home resets
the focused canvas. Panning is bounded. Equipment labels fade until hovered,
keyboard-focused or showing an open dialog, keeping the plant visible behind them.
Select equipment directly or use its labeled button to open the existing component
console. Rods, pump impellers, turbine, flow, fuel heat and melting follow the same
simulation as the instruments. The taller viewport supports both themes and
reduced motion. **Schematic** switches to the retained SVG overview without
changing plant state; browsers without WebGL 2 fall back to it automatically.

Plant calculations and instrument refreshes run every 250 ms. Presentation uses
one visibility-aware animation loop capped at 30 fps, which stops when there is
no visible motion. A static cold 3D scene redraws only when its displayed state,
camera or size changes; plant time and controls remain active. Hidden diagrams,
closed component/core/pool windows and inactive maintenance content defer their
expensive updates until shown, including immediate refresh when opened during
pause. Live readings reuse number formatters and avoid cloning component histories.
Software WebGL can still cost substantially more CPU than hardware acceleration.

The reactor has a flat, RBMK-inspired tiled channel deck, a blue spent-fuel pool
behind it with storage racks, and an overhead fuel-handling crane.
The top caps, internal fuel assemblies and control rods share the core map's
channel coordinates. Aqua collars make actual rod movement visible inside the
open-front upper guide chamber; rods stay beneath the deck. Small sliders on green
control-channel caps also show actual insertion from above; the caps themselves
stay stationary during regulation. **Replace selected fuel** and **Replace all
fuel** now trigger one approximately ten-second illustrative crane transfer:
align, lower the grabber, lift one assembly clear of the deck, carry it to the
pool, lower/release it and return to park. A batch replacement shows one assembly
to keep the sequence short. Move or close the floating core map to watch the
process underneath. The selected channel cap opens only during pickup.

Fuel renewal remains immediate and uses the existing cooled/stopped checks; the
animation never changes heat, damage, trips or operating settings. Displayed
pool assemblies represent replacement batches, not a complete physical spent-fuel
inventory. The 35 racks retain their batches until manual dispatch; a full pool
blocks further UI replacement until space is freed. Repeating replacement
restarts a single illustration; starting the reactor cancels an unfinished
illustration. Pause, hidden views, tab/view switches and detach freeze movement;
returning resumes its current position. Reduced motion places the sample
immediately. Reset demo clears the crane and displayed pool samples. The existing
core-map exchange animation and WebGL fallback remain available.

Click the **spent-fuel pool** or **Spent fuel ↗** to open its movable, nonmodal
window, available from the schematic/WebGL fallback too. Each stored batch shows
its source, assembly count and a 60-second demo cooling countdown. Cooling starts
on placement and follows simulated plant time, including while another tab is
selected; pause/hide/detach freeze that clock. Select a cooled batch and dispatch
it to **Long-term storage** or **Reprocessing**, or dispatch all cooled batches.
Dispatch frees the racks, removes their 3D fuel and records the destination/count
in the event journal and a bounded recent-dispatch list. Dispatch is free in this
demo; fee handling is reserved for later gameplay. No measured pool temperature
or real fuel-handling procedure is modeled.

See [the fuel-handling work plan](FUEL-HANDLING-PLAN.md) for the user's references,
the implementation checkpoint, and staged tasks for transfers, inventory and
gameplay, including continuation guidance for another instance.

The **Heat exchanger** console has a compact interactive cutaway with local trends
on its right on wide screens, fitting the default window without vertical scrolling.
The trend and controls stack on narrow screens. Drag the primary bypass,
secondary valve, tube bundle or deposits left/right, then release to apply a setting.
Select a part for a native slider and ±5% buttons; focused model parts support arrow
keys (1%, or 10% with Shift) and Home/End. No numeric entry is needed. Dashed valve
handles show requests, solid handles show actual positions. Fluid colors, flow,
secondary steam, heat arrows and deposits follow simulated read-back through the
shared animation loop, including pause/reduced-motion behavior. Cleaning still
requires a cooled, stopped, fault-free plant. Local trends, faults and maintenance
remain in the same movable window. On narrow screens the cutaway scrolls sideways
while the part selector and adjustment slider fit the window.

The header card beside the title contains regulation targets, the opt-in plant
sound switch, theme, simulation status and clock. UI clicks use 100% of the bounded
sample-volume range (0.26 gain); the volume slider and test button are removed.

The 3D toolbar's **Labels** selector defaults to **Compact** equipment tags. Hover
or keyboard focus reveals the name and live readings; the selected equipment's
card stays expanded. **Full** shows every card. **Off** hides the cards while
keeping canvas picking and keyboard access available: a focused equipment control
still reveals its card. Expansion stays clamped inside the view in both themes.
The choice survives view/tab switches, Reset demo and element reconnection.

All four pump dialogs use a compact interactive cutaway beside speed, flow and
pressure trends. Drag the impeller, discharge valve or trim knob, or choose a
control and use its native slider and ±5 buttons. Arrows adjust one unit, Shift
arrows ten; Home/End choose the control's bounds. A drag previews its request and
commits on release; cancellation, closing or hiding the dialog discards the preview.
Requested speed/valves are dashed amber; actual motor/valve read-back stays solid.
The rocker keeps requested enable separate from actual rotation/coast-down.
Auto ownership, manual override, internal damage and cooled repair rules remain
in force. Disturbances also use sliders; there are no numeric percentage entry
fields in the pump dialogs. Details/history are collapsed, and narrow screens
stack the trends below the cutaway with scrolling confined to the illustration.

The operator console uses recessed metal plates, round Start/Stop pushbuttons and
pilot lights. Green means the reactor or pump motor is actually running; amber
marks a stopped reactor or a ready trip reset. Pump stress/damage has amber/red
indication. Labels distinguish readiness, interlocks and actual operation, including
when a command is disabled. Emergency shutdown has a transparent hinged plastic
cover: first select **Lift cover**, then press the exposed red mushroom button.
The native cover toggle precedes the button in keyboard order; Enter/Space opens
the cover and Tab reaches the button. **Close cover** blocks access again without
changing the plant. New sessions and **Reset demo** close the cover. Automatic
trips work with either cover position, and pause/reduced motion skip the lid animation.
The trip lamp and button stay visible through the lid: the button stays depressed
and reads **TRIP LATCHED** for any latched reactor
trip, and releases only after a successful **Reset trip**. Repeated presses cannot
reset it; clearing the latch leaves the reactor stopped. The controls retain their
existing interlocks, keyboard access and both themes. These are simulated controls.
The button faces have restrained illumination: green Start during running, red Stop
while stopped, and a soft emergency locator glow that becomes bright red when
latched. These are steady lights, including with reduced motion. Pump cards use
recessed **ON/OFF rocker switches**, preserving circuit, speed and flow readings.
Rocker position shows the requested enable state; the separate status and lamp
still show actual motor operation, including coast-down after switching OFF.
The load setpoint uses a metal regulator plate, a grooved slider handle, an
engraved 0–100% rail, an amber percentage display and fine-adjust buttons. Its
AUTO/MANUAL/TRIP LOCK label identifies control ownership. Native slider input,
keyboard adjustment and automatic/trip interlocks are preserved.
All four pump speed targets share the same regulator rail, grooved handle, engraved
scale and illuminated percentage display. Manual overrides, automatic ownership
and actual speed/flow read-backs remain separate from these requested targets.

Each circuit has two manually enabled, parallel pumps: primary **A / A2** and
secondary **B / B2**. A2/B2 are the backup units; all four start OFF with retained
100% speed targets. Either pump can provide its circuit's startup circulation.
Each main/backup pair has independent speed sliders and ON/OFF rockers in the main
view. Adjusting an OFF pump's speed retains its target without starting it. Running
Auto owns each speed unless that pump has an explicit override. **Backup A2/B2 ↗**
opens that pump's speed, discharge valve, Auto override, local trends and internal
inspection. Auto adjusts only enabled pumps and never enables a backup. Each
3D/SVG branch shows its own actual motor/flow, including coast-down and loss of AC.

Two full-speed pumps give an illustrative **135% combined flow** and **182% relative
circulation pressure**, with additional auxiliary demand. Pressure is a normalized
gameplay differential-pressure indication, not an absolute vessel-pressure model.
Auto continues to hold the requested total circulation; use Manual or individual
speed overrides to run both pumps at 100%. Sustained pressure above **145% nominal**
accumulates pipe damage after an eight-second exposure allowance. Damage causes
visible blue water jets/drops in 3D and the schematic and reduces delivered coolant
flow. Leaking fittings also spray. Blades, bearings and fittings retain independent
damage; bearings can slow/stop the motor. Healthy single-pump operation is unchanged.
These values are fictional gameplay parameters. Generator output still depends on
heat, transfer/cooling, shaft/breaker state and the 410 MW gross nameplate limit.

Primary and secondary flow each have a left-side instrument showing actual delivered
flow, actual pressure and the healthy-pipe damage limit (about **5,780 m³/h**, derived
from **145% nominal ΔP**). Each card turns amber from **125% ΔP**, then red above
145% or while pipe damage remains. Text identifies near-limit, overpressure and
retained damage states. Pressure drives the warning even if a leak lowers delivered
flow below the displayed healthy-pipe limit. Alarm acknowledgement cannot hide it.

Click a pipework label, the sidebar pressure readout, or a leaking pipe joint to
inspect pressure, flow, damage and explicit repair. Pipe repair requires both
affected-circuit run switches OFF and actual motor speed/flow below 0.5%, together
with the existing stopped/cooled/fault-free repair checks. Damaged internal pump
parts require that pump OFF and stopped. Turning flow off stops the spray but
does not heal damage; **Repair all** also repairs isolated pipework and internals.
Repairs preserve heat, fuel, settings, operating hours and reactor/turbine trip
latches. Water spray and pump cutaways share the existing frame loop and respect
pause, hidden views, detach, Maintenance and reduced motion. Reset demo clears the
damage/leaks and returns every pump to OFF.

**Enable plant sounds** also enables seamless steam/turbine/generator loops.
Actual reactor output and turbine shaft speed drive the sound level; stress,
damage, overheating, trips and actual component disturbances crossfade to a rough
fault texture independently of alarm acknowledgement. The cold plant is silent,
and a stopped turbine fades with its actual coast-down. Mute, pause, hidden views
and detach stop all voices. The original synthesized WAVs and generation notes
are in `public/audio/nuclear-power/plant-state/`; they load only after sound opt-in.

Machinery audio follows the selected tab: **Reactor overview** mixes reactor
operating loops/Start/Stop cues and independent diesel startup, seamless running
and shutdown sounds. **Maintenance** silences all machinery immediately; alarms
and UI clicks remain shared. Diesel sounds follow actual startup/running/stopped/
empty state, including automatic starts and fuel exhaustion; volume follows bus
load. Returning to the plant resumes current operation without replaying past events.
Original diesel WAVs and regeneration notes are in `public/audio/nuclear-power/diesel/`.

The main panel has **Reactor overview** and **Maintenance** tabs. Small diesel
cutaways sit behind the turbine/generator in the main 3D plant, with the external
grid, AC bus and source-to-pump electrical routes. Pistons/shaft/fans follow actual
engine state; tank fill shows fuel, and lit terminals/moving wire markers show
actual bus load. Clicking either diesel opens its own movable dialog, with a
pump-style ON/OFF rocker, automatic standby, startup countdown, fuel/load and
manual refuelling. OFF disables standby as before. The two dialogs can coexist.

The **Pump power source** panel sits above Grid controller on the left, with source,
requested/available load and buttons to open each diesel. Its ↗ button (or the 3D
grid/bus) opens **Electrical supply & connections**, retaining the 2D route and
native grid/feed switches styled as the same ON/OFF rockers. Their keyboard and
requested/actual behavior remain. There is no Auxiliary power tab or header grid
button. Sidebar controls remain available in the schematic/WebGL fallback.
Maintenance suspends rendering; all dialogs retain the shared movement, clamping,
closing/focus and detach cleanup. No power/supply model behavior has changed.

The **Maintenance** tab shows a live count and list of components with retained
damage, wear or exchanger fouling, including affected core channels. **Inspect**
opens the existing component/core console. **Repair all** uses the existing
stopped, fault-free cooldown conditions, displayed with current temperatures.
**Clear active faults** stops injected deterioration without healing damage.
Repairs preserve heat, fuel reserves, settings and trip latches; meltdown still
requires Reset demo. The healthy list clears as repairs complete.
The reactor's **Inspect** core map also lists every damaged local zone with its
damage percentage and fuel temperature. Select an entry to show that rod's local
readings on the map. All retained damage, including below 1%, has a red border.
The dialog includes **Repair all damage** and current blocking reasons; repairing
clears these entries without renewing fuel or clearing a trip.

Steam rises through the exchanger's secondary-side cutaway and turns into its
outlet in both 3D and schematic views. Its density and speed follow secondary
temperature and circulation, with no steam during cold circulation or zero flow.
The vessel has moving reaction particles and individual fuel pulses driven by
actual running power, fuel capability and rod insertion. Higher reaction activity
visibly increases particle travel and pulse speed; each rod's local activity has
its own continuous phase, so withdrawn rods animate faster than inserted ones.
Requested settings and turbine/electrical speed do not drive these effects.
Stop/trip removes reaction activity while
retained fuel heat keeps its separate glow. These visuals share pause, visibility,
reduced-motion and renderer cleanup with the rest of the scene.

The **Grid controller** card below the instruments can **Follow requests** in
Auto → Electrical output. Following is selected in new sessions and after Reset
demo. The simulated feed sends its first raise/lower MW request after 45 active
seconds, then every 60 active seconds.
**Request now** delivers one immediately. Each message coordinates the electrical
target, circulation target and operating core limit. Requests stay within normal
available capacity (up to about 300 MW healthy), derated for equipment restrictions,
fuel and manual pump/rod overrides. The card shows available capacity, planned
operating targets, actual output, countdown and limiting reason. Actual output
continues through the existing regulator and protections. Dispatch waits during
Stop, trips, pause, Manual or temperature regulation and unavailable circulation/
generation; it never starts the plant or closes a breaker. Disabling retains all
current targets. Reset demo clears messages and restores following. Manual temperature entry has
no 330 °C cap; existing alarms and automatic trip thresholds still apply.

Both coolant loops use the same absolute temperature colors, including return
pipes and bypass. Core inlet/outlet and secondary return temperatures are displayed.
Full exchanger bypass removes the primary cooling drop instead of retaining a
nominal cold return; condenser cooling controls the secondary return temperature.
Valve handles follow actual opening and the pipe axis: parallel when fully open,
perpendicular when closed. All pipe sections in a circuit share distance-based
marker travel, so longer return pipes no longer appear to flow faster than steam.

Thermal output, electrical output and primary flow instruments have small animated
heat, generator and coolant illustrations, plus traveling meter highlights. Their
speed/intensity follows actual output/flow. They stay still at zero and share the
pause, visibility and reduced-motion behavior of the main scene.

## Development and verification

Use Node 22.12+ (or Node 24) and npm, independently of the Java/Gradle application:

```sh
cd nuclear
npm ci
npm run dev
```

Open `http://localhost:5174`. For production checks:

```sh
npm test
npm run build
npm run test:browser
npm run preview
```

`npm test` runs the model, motion, failure, fuel, supply, component and audio tests.
The browser regression serves `dist/` on a temporary loopback port and tests the
actual React production build, including responsive layouts, themes, dialogs,
controls, exports, animations and cleanup, including 3D picking, zoom, view switching,
WebGL fallback and renderer disposal/reconnection. It uses Chrome's software WebGL
renderer for deterministic headless coverage. It requires Chrome at
`/opt/google/chrome/chrome`, or an installed Chromium selected with `CHROME_BIN`.
CI uses that executable directly. Startup allows 60 seconds and reads the profile's
`DevToolsActivePort` file as well as stdout/stderr, with early-exit diagnostics.
Set `NUCLEAR_POWER_APP_URL=http://127.0.0.1:8080` to test a running app/container.
Screenshot and export artifacts are written to the temporary directory printed by
the test. Tests do not need the Spring application.

## Container and deployment

From the repository root:

```sh
docker build -f nuclear/Dockerfile -t porssiohjain-nuclear .
docker run --rm -p 127.0.0.1:8080:80 porssiohjain-nuclear
```

The runtime is Nginx on port 80 serving static files, including bundled CC0 Kenney
UI Audio under `public/audio/nuclear-power/kenney-ui/` with its license/source record.
It needs no environment variables, API proxy, database, or Java runtime.

`.github/workflows/nuclear-container.yml` runs the unit tests, production build and
Chrome regression for pull requests. Relevant pushes to `production` and manual
runs also publish the `linux/amd64` and `linux/arm64` image:

```text
ghcr.io/norkator/porssiohjain-nuclear:latest
```

Deploy that image and point your HTTPS reverse proxy for `nuclear.porssiohjain.fi`
at its port 80. DNS, TLS, and the running deployment are configured separately;
the workflow only builds and pushes the image. The desktop link is already set
to the future URL.

See [the feature specification](../doc/nuclear-power.md) for the simulation rules,
interlocks and future physical-model work. The repository's [license](../LICENSE)
also applies to this app; bundled third-party audio retains its CC0 license.

## Search visibility

The production build includes static introductory content, three linked control-room
guides, canonical and social metadata, structured app data, a sharing screenshot,
`/robots.txt` and `/sitemap.xml`. The guides work without JavaScript and do not mount
the simulator. The interactive homepage retains its existing React/custom-element
lifecycle. See [SEO.md](SEO.md) for content maintenance, verification and the
Search Console steps after deployment.
