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
bounded angles, scroll or use +/− to zoom, and use **Reset view** to return to the
closer initial framing. Arrow keys, +/− and Home also control the focused canvas.
Select equipment directly or use its labeled button to open the existing component
console. Rods, pump impellers, turbine, flow, fuel heat and melting follow the same
simulation as the instruments. The taller viewport supports both themes and
reduced motion. **Schematic** switches to the retained SVG overview without
changing plant state; browsers without WebGL 2 fall back to it automatically.

The header card beside the title contains regulation targets, the opt-in plant
sound switch, theme, simulation status and clock. UI clicks use 100% of the bounded
sample-volume range (0.26 gain); the volume slider and test button are removed.

**Enable plant sounds** also enables seamless steam/turbine/generator loops.
Actual reactor output and turbine shaft speed drive the sound level; stress,
damage, overheating, trips and actual component disturbances crossfade to a rough
fault texture independently of alarm acknowledgement. The cold plant is silent,
and a stopped turbine fades with its actual coast-down. Mute, pause, hidden views
and detach stop all voices. The original synthesized WAVs and generation notes
are in `public/audio/nuclear-power/plant-state/`; they load only after sound opt-in.

The main panel has **Reactor overview** and **Auxiliary power** tabs. The auxiliary
tab contains two low-poly 3D diesel cutaways, grid/feed switches and the existing
standby, start/stop and manual refuelling controls. Pistons, shaft and fans follow
reported starting/running state; tank fill follows actual fuel reserve, and lit
terminals/flow markers identify engines supplying the bus. Running unloaded remains
distinct from stopped. The source-to-pump route remains a plain 2D diagram inside
this tab. Both 3D views support orbit, zoom, themes and cleanup; unavailable WebGL
leaves auxiliary readings/controls usable. Switching tabs preserves plant state and
only renders the selected scene. Core and component dialogs remain floating.

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
Auto → Electrical output. The optional simulated feed is off initially, sends its
first raise/lower MW request after 45 active seconds, then every 60 active seconds.
**Request now** delivers one immediately. Each message coordinates the electrical
target, circulation target and operating core limit. Requests stay within normal
available capacity (up to about 300 MW healthy), derated for equipment restrictions,
fuel and manual pump/rod overrides. The card shows available capacity, planned
operating targets, actual output, countdown and limiting reason. Actual output
continues through the existing regulator and protections. Dispatch waits during
Stop, trips, pause, Manual or temperature regulation and unavailable circulation/
generation; it never starts the plant or closes a breaker. Disabling retains all
current targets. Reset demo clears the feed/messages. Manual temperature entry has
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
