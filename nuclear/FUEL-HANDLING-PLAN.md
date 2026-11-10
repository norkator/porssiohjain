# Fuel-handling design and continuation plan

Requested 2026-11-10. Read `../AGENTS.md` and `../doc/nuclear-power.md` before
continuing. This is a fictional browser-local two-loop plant; the RBMK reference
is for the appearance of the channel deck, not a change to reactor physics.

## User's idea

- Add a spent nuclear fuel cooling/storage pool behind the reactor vessel in the
  existing interactive 3D cutaway.
- Replace the conical vessel lid with a flat, circular, densely tiled channel deck
  inspired by the RBMK reactor-top reference: pale square caps, green/yellow
  identifiers and an orange perimeter.
- Put a lift/handling crane above the reactor and pool. Leave enough vertical and
  horizontal clearance to lift a fuel assembly, carry it behind the vessel and
  lower it into a pool rack.
- Eventually animate changing fuel assemblies and turn refuelling into gameplay.
  Fuel assemblies and control rods must remain separate objects.
- The user also wants to see control rods moving during normal operation. Keep
  the tiled caps stationary and expose the upper guide chamber in the cutaway;
  aqua collars travel with actual rod insertion beneath the deck. Do not make
  normal regulation look like the crane lifting bare rods into the hall.
- Small read-back sliders on the green control-channel caps provide a second
  visible cue from above. They follow actual insertion; the square cap does not
  lift or become a fuel assembly.

The distinction is informed by the [IAEA RBMK design description, section 2.2](https://nucleus-qa.iaea.org/sites/gsan/lib/Safety%20Assessment%20Safety%20Series/Accident%20Analysis%20for%20Nuclear%20Power%20Plants%20with%20Graphite%20moderated%20Boiling%20Water%20RBMK%20Reactors-SRS-43.pdf):
control rods travel in their own channels, suspended from drives at the channel
cappings. The cutaway's exposed moving collars are an illustrative presentation.

References supplied by the user (local files, not portable assets):

- `/home/norkator/Pictures/Screenshots/Screenshot From 2026-11-10 18-09-25.png`
  — the original plant cutaway and vessel location.
- `/home/norkator/Pictures/Screenshots/Screenshot From 2026-11-10 18-12-38.png`
  — the tiled reactor-top appearance.

## Work steps

### 1. Visual foundation — current implementation pass

- [x] Replace the conical reactor lid with the channel deck, keeping the open-front
  cutaway and actual moving control rods.
- [x] Reuse `CORE_CHANNELS` for deck positions/identifiers so later lifting aligns
  with the existing core map and fuel inventory.
- [x] Add the rear pool with blue water, visible empty storage racks, a concrete
  cutaway enclosure, a connecting service deck and rails.
- [x] Add overhead runway rails, a separate bridge, trolley, hoist and grabber;
  park the crane over the pool, clear of the reactor top.
- [x] Keep the new pool/crane as clearly labeled scenery for this pass. No fake
  telemetry, automatically replaced fuel, simulated pool cooling or transfer
  controls until the underlying state exists.
- [x] Verify production build, existing unit/browser checks and visual appearance
  in dark/light themes, mobile framing, camera extremes and lifecycle cleanup.

### 2. Fuel-handling state and inventory

- Add a deterministic state module (suggested `nuclear-power-refuelling.js`).
  Keep simulation state outside Three.js and React rendering.
- Give discharged assemblies unique identities and history (source core channel,
  remaining reserve/exposure, replacement number and discharge time). Reserve a
  destination rack before lifting. Start with empty pool slots.
- Define explicit states such as idle, aligning, gripping, lifting, traversing,
  lowering, releasing and returning. Store requested versus actual crane position
  and held assembly separately. Model an empty core channel while fuel is held.
- Decide how new fuel enters the scene (a separate fresh-fuel staging rack is a
  proposal, not yet a user requirement). Handle a full pool and interrupted jobs
  without duplicating, losing or silently renewing assemblies.
- Reuse `FuelInventory.replacementReadiness()` and existing cooled/stopped checks.
  Preserve heat, damage, trips, settings and stopped state. Automation must not
  initiate refuelling. Reactor Start must be blocked during an incomplete transfer
  or when a core channel is empty.
- Reconcile the current immediate `replace`/`replaceAll` behavior deliberately:
  either retain the existing quick replacement as a clearly separate mode or route
  it through the new operation; do not accidentally renew inventory twice.

### 3. Crane animation and operator controls

- Use the scene's named bridge/trolley/hoist groups and shared channel/rack anchors.
  Align over the selected channel, lower the grabber, lift vertically clear of the
  deck, traverse to the reserved pool slot and lower the discharged assembly.
- Add fresh insertion as the next explicit operation and confirm completion only
  when the assembly reaches its destination. Keep control rods in their channels.
- Animate actual handling state through the existing visibility-aware frame loop.
  Pause/hide/tab switches freeze work; reopening must show current state without
  replaying a completed lift. Reduced motion changes presentation, not inventory
  correctness. Detach disposes resources; Reset demo clears jobs and pool state.
- Add a movable, nonmodal fuel-handling window with native keyboard controls,
  channel/slot selection, current operation, readiness reasons and cancellation
  behavior. Preserve core-map/component dialogs and camera/picking controls.
- Expose equivalent state/action access when WebGL is unavailable. Record explicit
  operator operations in the existing categorized journal and snapshot/export.

### 4. Gameplay (future, agree details before broadening scope)

- Candidate tasks: replace depleted assemblies, choose/reserve storage slots and
  complete a shutdown/refuelling/restart objective.
- Candidate scoring: correct sequence, fuel usage and successful placement; avoid
  rewarding bypassing interlocks or treating toy limits as real procedures.
- Pool capacity, cooling/retained heat, crane faults, time penalties, fresh-fuel
  supply and persistent campaigns are proposals, not implemented requirements.
- Keep the current normal plant operation available; never let a game objective
  silently start/reset the reactor, repair equipment or replenish fuel.

### 5. Verification for handling behavior

- Unit tests: slot reservation/full pool, identity conservation, empty channels,
  correct completion, readiness changes, interruption/cancellation, pause/reset
  and preserved trip/heat/damage. Test selected and batch operations.
- Browser tests: aligned pickup/drop, visible held assembly and occupied racks,
  keyboard controls, both themes, camera bounds, mobile, fallback, tab switches,
  reduced motion and resource cleanup.
- From `nuclear/`: `npm ci`, `npm test`, `npm run build`, `npm run test:browser`.

## Existing implementation entry points

- `src/simulation/nuclear-power-scene.js`: Three.js overview, camera, batching,
  picking, shared render lifecycle and GPU disposal.
- `src/simulation/nuclear-power-refuelling-scene.js`: visual deck, pool and parked
  crane; named anchors/groups for future movement (added in step 1).
- `src/simulation/nuclear-power-model.js`: `CORE_CHANNELS`, simulation state,
  commands and snapshots.
- `src/simulation/nuclear-power-fuel.js`: fuel inventory, readiness and immediate
  replacement operations.
- `src/simulation/nuclear-power.js`: core map, replacement actions, existing
  cosmetic exchange animation and visibility-aware animation loop.
- `test/browser.mjs`: real production app/WebGL regression and screenshots.

## Handoff checkpoint

Step 1 is complete and verified. Start continuation at step 2. Steps 2–5 describe
future work, not completed behavior. No crane transfers or gameplay were added
in this design pass.

Implementation notes:

- The deck has 61 shared channels: 49 fuel assemblies and 12 control rods. Internal
  fuel meshes now match the same coordinates instead of the earlier simplified
  twelve-box layout. Heat/reaction visuals use the nearest existing rod zone.
- The pool has 35 empty, named rack positions. Choose how to handle capacity
  before implementing full-core transfers; the current immediate Replace all
  action does not use the pool.
- `overviewScene.refuelling` holds `deckChannels` (world-space pickup anchors),
  `rodIndicators`, `pool.slots` (world-space rack-top anchors), and separate
  `crane.bridge`, `crane.trolley`, `crane.hoist` and `crane.grabber` groups.
  The hall/group geometry is still batched and disposed by the existing renderer.
- Current rod movement is a cutaway illustration beneath the closed deck. The
  cap sliders are actual-insertion indicators, not moving channel caps or
  requested positions.
- The platform extends rearward; the default camera frames the taller crane.
  User orbit/zoom, semantic equipment controls and the SVG fallback remain.
- Verification passed: `npm ci`, all 86 unit checks (`npm test`), production build
  (`npm run build`) and full Chrome/WebGL regression (`npm run test:browser`).
  The browser checks include shared fuel/channel identities, deck alignment,
  the entire pool/crane fitting the default camera, actual rod/cap-indicator
  travel beneath the stationary deck, and existing themes/mobile/pause/fallback/
  tab/lifecycle behavior.
- Final browser screenshots/exports: `/tmp/nuclear-power-check-UrVJZx/`.
  Additional cropped design views: `/tmp/nuclear-fuel-design/`. These temporary
  artifacts may disappear; regenerate with the browser test if needed.
