# Nuclear Power

`/nuclear-power` is an authenticated, English-only Vaadin feature inside `MainLayout`.
It has its own desktop/Start shortcut, title-bar icon, and taskbar entry. Both locale
bundles deliberately call it **Nuclear Power**. The desktop shell keeps the account's
normal language; the view and all its controls remain English.

## Current scope

The feature is a fictional, interactive control-room simulation. All readings,
equipment states, alarms, events, and trends are simulated. It does not persist
telemetry, access measurement history, send device commands, or implement a physical
reactor model. It starts at a steady operating point with both pumps running.

The schematic includes a reactor cutaway with moving rods and fuel channels,
separate primary and secondary circuits, animated pumps and coolant flow, a heat
exchanger, a turbine/generator, and a condenser. Click or keyboard-activate outlined
equipment to inspect it. The diagram can be zoomed and scrolled. Controls and
instruments adapt to the feature window's width, including desktop window resizing.

Controls:

- Start/Stop: normal startup or cooldown, maintaining the circulation settings.
- Load setpoint: sets requested output and corresponding rod insertion.
- Insert/Withdraw: moves the rod setpoint in five-percentage-point steps and updates
  requested load. Actual rod position, output, and temperatures change gradually.
- Pump A/B: toggles primary/secondary circulation with gradual flow changes.
- Emergency shutdown: latches a simulated trip and inserts rods. Restart requires
  **Reset demo**. Low primary flow or high core temperature can also trigger the
  illustrative protection sequence.
- Acknowledge alarms: acknowledges existing alerts without hiding active conditions.
  New faults require their own acknowledgement.
- Pause/Resume: freezes/resumes simulated time and animations.
- Reset demo: restores the initial operating point, event log, and trend history.

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
  model. Its snapshot separates signals into value, unit, source, quality, and
  simulated elapsed time; the elapsed time is not a sensor measurement timestamp.

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

## Verification

```sh
JAVA_HOME=/path/to/java21 ./gradlew compileJava test \
  --tests com.nitramite.porssiohjain.views.MainLayoutTest \
  --tests com.nitramite.porssiohjain.views.NuclearPowerViewTest \
  --tests com.nitramite.porssiohjain.views.DesktopWindowNavigationTest
node --test src/test/frontend/nuclear-power-model.test.mjs
```
