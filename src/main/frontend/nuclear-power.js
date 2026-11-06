/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
import { CORE_CHANNELS, NuclearPowerSimulation } from './nuclear-power-model.js';
import { advancePumpMotion } from './nuclear-power-motion.js';
import styles from './nuclear-power.css?inline';
import lightStyles from './nuclear-power-light.css?inline';

const radiation = `<svg viewBox="0 0 40 40" fill="currentColor" aria-hidden="true"><circle cx="20" cy="20" r="3.4"/><path d="M16 13.1 12 6.2a16 16 0 0 1 16 0l-4 6.9a8 8 0 0 0-8 0ZM12 20H4a16 16 0 0 0 8 13.9l4-6.9a8 8 0 0 1-4-7Zm12 7 4 6.9A16 16 0 0 0 36 20h-8a8 8 0 0 1-4 7Z"/></svg>`;
// Explicit local (0,0) rotation avoids the asymmetric SVG bounding-box pivot.
const pumpRotor = `<g class="rotor" transform="rotate(0 0 0)">${[0, 120, 240].map(angle => `<path transform="rotate(${angle})" d="M0-18C13-18 19-9 6 0 6-10-3-9 0-18Z"/>`).join('')}</g><circle class="pump-hub" r="5"/>`;

const schematic = `
<svg class="schematic" viewBox="0 0 1000 550" xmlns="http://www.w3.org/2000/svg" role="group" aria-label="Interactive simulated reactor, primary coolant circuit, heat exchanger and turbine circuit">
 <defs>
  <pattern id="grid" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0V24" fill="none" stroke="#263542" stroke-width=".6"/></pattern>
  <linearGradient id="vessel" x1="0" x2="1"><stop stop-color="#253642"/><stop offset=".45" stop-color="#435565"/><stop offset="1" stop-color="#23323e"/></linearGradient>
  <linearGradient id="coolant" x2="0" y2="1"><stop stop-color="#f7ab5b" stop-opacity=".16"/><stop offset="1" stop-color="#59cbbd" stop-opacity=".18"/></linearGradient>
  <linearGradient id="fuel" x2="0" y2="1"><stop stop-color="#ffd997"/><stop offset=".5" stop-color="#e79246"/><stop offset="1" stop-color="#ac5832"/></linearGradient>
  <radialGradient id="core-glow"><stop stop-color="#ffa35a" stop-opacity=".45"/><stop offset="1" stop-color="#ffa35a" stop-opacity="0"/></radialGradient>
  <radialGradient id="failure-glow"><stop stop-color="#ff443b" stop-opacity=".75"/><stop offset="1" stop-color="#ff443b" stop-opacity="0"/></radialGradient>
  <pattern id="hatch" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><path d="M0 0V8" stroke="#52616c" stroke-width="2"/></pattern>
 </defs>
 <rect width="1000" height="550" fill="url(#grid)"/>
 <text x="30" y="33" class="drawing-label">P&amp;ID / UNIT 01</text><text x="970" y="33" text-anchor="end" class="drawing-label">SIMPLIFIED PROCESS DIAGRAM</text>

 <!-- Independent primary and secondary loops. Static pipework remains visible without flow. -->
 <path class="pipe-shell" d="M298 220H365V160H530M530 365H460V438H298V350"/>
 <path class="pipe hot" d="M298 220H365V160H530"/>
 <path class="pipe cold" d="M530 365H460V438H298V350"/>
 <path class="flow primary hot-flow" d="M298 220H365V160H530"/>
 <path class="flow primary cold-flow" d="M530 365H460V438H298V350"/>
 <path class="pipe-shell" d="M610 160H790V210M840 270V355M790 410V470H610V365"/>
 <path class="pipe steam" d="M610 160H790V210"/>
 <path class="pipe cold" d="M840 270V355M790 410V470H610V365"/>
 <path class="flow secondary steam-flow" d="M610 160H790V210"/>
 <path class="flow secondary cold-flow" d="M840 270V355M790 410V470H610V365"/>
 <path class="signal-line" d="M866 238H926V308"/><text x="926" y="329" text-anchor="middle" class="equipment-tag">GRID</text>
 <path d="m920 300 6 8 6-8" fill="none" stroke="#a9c5bf" stroke-width="2"/>

 <!-- Reactor vessel / rods and fuel channels. -->
 <g class="equipment" data-equipment="reactor" role="button" tabindex="0" aria-label="Open reactor core map" aria-haspopup="dialog" aria-controls="core-map-window" aria-expanded="false">
  <rect class="selection-ring" x="100" y="70" width="218" height="355" rx="30"/>
  <text x="208" y="63" text-anchor="middle" class="equipment-tag">R-01 / REACTOR VESSEL</text>
  <path d="M144 155Q144 105 208 105T272 155V345Q272 392 208 392T144 345Z" fill="url(#vessel)" stroke="#83929b" stroke-width="2"/>
  <path d="M155 161Q155 117 208 117T261 161V339Q261 379 208 379T155 339Z" fill="#152430" stroke="#647580"/>
  <path d="M155 161Q155 117 208 117T261 161V339Q261 379 208 379T155 339Z" fill="url(#coolant)"/>
  <ellipse class="core-glow" cx="208" cy="278" rx="100" ry="108" fill="url(#core-glow)"/>
  <g class="fuel-bundle" fill="url(#fuel)" stroke="#ffc785" stroke-width=".7">
   ${[170, 186, 202, 218, 234].map(x => `<rect x="${x}" y="230" width="12" height="112" rx="3"/>`).join('')}
  </g>
  <ellipse class="melt-pool" cx="208" cy="350" rx="48" ry="9" fill="#ff6043" opacity="0"/>
  <g stroke="#ffd29a" stroke-opacity=".35">${[250, 270, 290, 310, 330].map(y => `<path d="M168 ${y}h80"/>`).join('')}</g>
  <path d="M158 223h100M158 348h100" stroke="#98a7ac" stroke-width="5"/>
  <g class="rod-bank" fill="#bdccd2" stroke="#e4edf0" stroke-width=".6">
   ${Array.from({ length: 12 }, (_, index) => `<rect data-rod-side="CR-${String(index + 1).padStart(2, '0')}" x="${166 + index * 7}" y="95" width="4" height="150" rx="2"/>`).join('')}
  </g>
  <path d="M144 156h128M144 169h128" stroke="#94a5ae" stroke-width="3"/>
  <g fill="#c7d4d9">${[151, 172, 193, 214, 235, 256].map(x => `<rect x="${x}" y="155" width="5" height="15" rx="1"/>`).join('')}</g>
  <path d="M272 208h26v24h-26M272 338h26v24h-26" fill="url(#vessel)" stroke="#83929b" stroke-width="2"/>
  <path d="M165 383v27m86-27v27M151 410h28m58 0h28" stroke="#647580" stroke-width="6"/>
  <text x="208" y="447" text-anchor="middle" class="component-subtitle">BANK MEAN INSERTION</text>
  <text x="208" y="474" text-anchor="middle" class="diagram-value" data-value="rods">36.0%</text>
 </g>

 <!-- Core outlet and return instruments. -->
 <g class="instrument core-temperature-instrument" transform="translate(330 240)"><path d="M35-20V0"/><rect width="110" height="51" rx="5"/><text x="12" y="18" class="instrument-label">CORE OUTLET</text><text x="12" y="39" class="instrument-value warm" data-value="core">286.0 °C</text></g>
 <g class="instrument" transform="translate(325 349)"><path d="M40 51V89"/><rect width="110" height="51" rx="5"/><text x="12" y="18" class="instrument-label">RETURN</text><text x="12" y="39" class="instrument-value cool" data-value="return">258.0 °C</text></g>
 <text x="393" y="137" class="circuit-label warm">01 / PRIMARY</text>
 <path d="m464 155 8 5-8 5" class="arrow warm"/>
 <path d="m369 433-8 5 8 5" class="arrow cool"/>

 <!-- Heat exchanger: separated hot and cold tube paths. -->
 <g class="equipment" data-equipment="exchanger" role="button" tabindex="0" aria-label="Inspect heat exchanger">
  <rect class="selection-ring" x="499" y="101" width="142" height="300" rx="22"/>
  <text x="570" y="88" text-anchor="middle" class="equipment-tag">HX-01</text>
  <rect x="522" y="124" width="96" height="270" rx="38" fill="url(#vessel)" stroke="#83929b" stroke-width="2"/>
  <rect x="533" y="140" width="74" height="236" rx="28" fill="#14232f" stroke="#5d6f79"/>
  <path d="M530 160h15v25h50v23h-50v23h50v23h-50v23h50v23h-50v23h50v23h-50v19h-15" fill="none" stroke="#d69a60" stroke-width="5"/>
  <path d="M610 365h-12V160h12" fill="none" stroke="#6ac9bc" stroke-width="4"/>
  <path d="M531 152v217" stroke="#e4edf0" stroke-opacity=".2" stroke-width="2"/>
  <text x="570" y="421" text-anchor="middle" class="component-subtitle">HEAT EXCHANGER</text>
 </g>

 <!-- Pump A, with animated impeller. -->
 <g class="equipment pump pump-a" data-equipment="pumpA" role="button" tabindex="0" aria-label="Inspect primary pump A">
  <rect class="selection-ring" x="424" y="402" width="72" height="74" rx="36"/>
  <circle cx="460" cy="438" r="28" fill="#172a34" stroke="#8fa2ab" stroke-width="2"/>
  <g transform="translate(460 438)">${pumpRotor}</g>
  <text x="460" y="495" text-anchor="middle" class="equipment-tag">P-01</text><text x="460" y="513" text-anchor="middle" class="pump-state" data-value="pumpA">RUNNING</text>
 </g>

 <!-- Steam turbine and generator. -->
 <text x="669" y="137" class="circuit-label warm">02 / SECONDARY</text>
 <g class="instrument" transform="translate(649 63)"><path d="M45 51v46"/><rect width="120" height="51" rx="5"/><text x="12" y="18" class="instrument-label">STEAM OUTLET</text><text x="12" y="39" class="instrument-value warm" data-value="secondary">184.0 °C</text></g>
 <g class="equipment turbine" data-equipment="turbine" role="button" tabindex="0" aria-label="Inspect turbine and generator">
  <rect class="selection-ring" x="739" y="187" width="146" height="106" rx="12"/>
  <path d="m752 216 102-15v75l-102-15Z" fill="url(#vessel)" stroke="#8c9ca6" stroke-width="2"/>
  <path d="M767 220v36m17-39v42m17-45v48m17-50v53m17-55v58" class="turbine-blades" stroke="#d7c7aa" stroke-width="4"/>
  <path d="M742 238h132" stroke="#d2dcd9" stroke-width="3"/>
  <circle cx="898" cy="238" r="25" fill="#203c3d" stroke="#89b9ac" stroke-width="2"/><text x="898" y="245" text-anchor="middle" class="generator-letter">G</text>
  <text x="805" y="315" text-anchor="middle" class="equipment-tag">TG-01 / TURBINE</text>
 </g>
 <g class="instrument" transform="translate(861 136)"><rect width="111" height="51" rx="5"/><text x="12" y="18" class="instrument-label">GENERATOR</text><text x="12" y="39" class="instrument-value cool" data-value="electric">262 MW</text></g>

 <!-- Condenser and return pump. -->
 <rect x="747" y="355" width="139" height="55" rx="9" fill="#1c303b" stroke="#7d96a1" stroke-width="2"/>
 <path d="M758 369h116m-116 12h116m-116 12h116" stroke="#6db7b3" stroke-width="3"/>
 <text x="914" y="378" class="equipment-tag">C-01</text><text x="914" y="395" class="component-subtitle">CONDENSER</text>
 <g class="equipment pump pump-b" data-equipment="pumpB" role="button" tabindex="0" aria-label="Inspect secondary pump B">
  <rect class="selection-ring" x="652" y="434" width="72" height="74" rx="36"/>
  <circle cx="688" cy="470" r="28" fill="#172a34" stroke="#8fa2ab" stroke-width="2"/>
  <g transform="translate(688 470)">${pumpRotor}</g>
  <text x="746" y="468" class="equipment-tag">P-02</text><text x="746" y="486" class="pump-state" data-value="pumpB">RUNNING</text>
 </g>
 <path d="m725 465-8 5 8 5" class="arrow cool"/>
 <text x="30" y="528" class="drawing-label">SIMULATED SIGNALS / NO DEVICE CONNECTION</text>
 <text x="970" y="528" text-anchor="end" class="drawing-label">NP-001 · REV 01</text>
</svg>`;

const coreMap = `<section class="core-map" aria-label="Top-down reactor core and individual control rods">
  <div class="core-map-heading"><span class="local-tag">R-01 / TOP-DOWN CORE MAP</span><span class="local-tag">FICTIONAL TWO-LOOP REACTOR</span></div>
  <div class="core-map-layout"><div class="core-map-drawing"><svg viewBox="0 0 500 500" class="core-map-svg" role="group" aria-label="Core channel map; select a numbered control rod">
    <circle cx="250" cy="250" r="227" class="core-vessel-outer"/><circle cx="250" cy="250" r="214" class="core-vessel-inner"/>
    <path d="M250 9v29m0 424v29M9 250h29m424 0h29" class="core-crosshair"/>
    ${CORE_CHANNELS.map(channel => `<g transform="translate(${250 + channel.x * 43} ${250 + channel.y * 43})" class="core-channel ${channel.rodId ? 'control-channel' : 'fuel-channel'}" ${channel.rodId ? `data-rod="${channel.rodId}" role="button" tabindex="0" aria-label="Select control rod ${channel.rodId}" aria-pressed="false"` : `data-fuel-x="${channel.x}" data-fuel-y="${channel.y}" data-fuel-id="${channel.fuelId}" role="button" tabindex="0" aria-label="Select fuel assembly ${channel.fuelId}" aria-pressed="false"`}>
      <rect x="-18" y="-18" width="36" height="36" rx="6"/>
      ${channel.rodId ? `<circle class="rod-depth-ring" r="14" transform="rotate(-90)"/><text text-anchor="middle" y="3.5">${channel.rodId.slice(3)}</text>` : '<circle r="8" class="fuel-center"/><circle r="3" class="fuel-pin"/><circle r="14" class="fuel-reserve-ring" transform="rotate(-90)"/>'}
    </g>`).join('')}
  </svg><div class="core-map-legend"><span><i class="legend-dot"></i> Control rod / insertion ring</span><span><i class="legend-dot warm-dot"></i> Fuel / indicative activity</span></div></div>
  <section class="individual-rod" aria-label="Individual rod controls">
    <div class="eyebrow">INDIVIDUAL CONTROL ROD</div><label class="sr-only" for="rod-select">Selected control rod</label><select id="rod-select">${Array.from({ length: 12 }, (_, i) => `<option>CR-${String(i + 1).padStart(2, '0')}</option>`).join('')}</select>
    <div class="rod-depth-display"><div><span class="control-label">ACTUAL INSERTION</span><strong data-value="selectedRodActual">36.0%</strong><small data-value="selectedRodMotion">AT TARGET</small></div><div class="rod-depth-track"><i></i></div></div>
    <label for="individual-rod">Insertion target <strong data-value="selectedRodTarget">36%</strong></label><input id="individual-rod" type="range" min="0" max="100" step="1" value="36"/><div class="range-labels"><span>WITHDRAWN / 0%</span><span>INSERTED / 100%</span></div>
    <div class="button-pair"><button class="control-button compact" data-action="rod-withdraw">↑ Withdraw 1%</button><button class="control-button compact" data-action="rod-insert">↓ Insert 1%</button></div>
    <label class="rod-override"><input id="rod-override" type="checkbox"/> Manual override in Auto</label><p data-value="rodOwner"></p>
    <p>Override holds this rod under your control while Auto compensates with the others. Release it to return control to Auto. Stop and trip always insert every rod.</p>
    <dl><div><dt>Bank mean actual</dt><dd data-value="rods"></dd></div><div><dt>Core temperature</dt><dd data-value="core"></dd></div><div><dt>Local fuel temperature</dt><dd data-value="selectedZoneTemperature"></dd></div><div><dt>Channel damage</dt><dd data-value="selectedZoneDamage"></dd></div><div><dt>Control mode</dt><dd data-value="mode"></dd></div></dl>
  </section>
  <section class="individual-rod fuel-inspector" hidden aria-label="Selected fuel assembly">
    <div class="eyebrow">FUEL ASSEMBLY</div><h3 data-value="selectedFuelId"></h3><div class="rod-depth-display"><div><span class="control-label">REMAINING RESERVE</span><strong data-value="selectedFuelReserve"></strong><small data-value="selectedFuelState"></small></div></div>
    <dl><div><dt>Effectiveness</dt><dd data-value="selectedFuelEffectiveness"></dd></div><div><dt>Exposure</dt><dd data-value="selectedFuelExposure"></dd></div><div><dt>Replacements</dt><dd data-value="selectedFuelReplacements"></dd></div><div><dt>Core mean reserve</dt><dd data-value="fuelReserve"></dd></div><div><dt>Core capability</dt><dd data-value="fuelCapability"></dd></div></dl>
    <label for="fuel-rate">Fuel exposure speed</label><select id="fuel-rate"><option value="1">1× demo exposure</option><option value="10">10× demo exposure</option><option value="60">60× demo exposure</option></select><p>Six demo full-power hours consumes a fresh assembly at 1×. Exposure varies by channel and load. This is an accelerated illustration.</p>
    <button class="control-button" data-action="replace-fuel" disabled>Replace selected fuel</button><p data-value="fuelReplacementReadiness"></p><button class="small-button" data-action="select-control-rod">Back to control rods</button><p>Fuel replacement is always manual. Auto never replaces fuel.</p>
  </section></div><p class="core-map-note">Select a numbered control rod or an amber fuel assembly. Rings show actual insertion or remaining fuel reserve. Fictional layout and accelerated fuel model.</p>
</section>`;

const auxiliaryDialog = `<dialog class="auxiliary-dialog" aria-labelledby="auxiliary-title">
  <div class="core-dialog-titlebar"><h2 id="auxiliary-title">Auxiliary power &amp; backup generators</h2><button class="small-button" data-action="close-power" aria-label="Close auxiliary power">×</button></div>
  <div class="auxiliary-content"><div class="supply-overview"><span class="control-label">PUMP POWER SOURCE</span><strong data-value="powerSource"></strong><span data-value="auxiliaryLoad"></span><div class="supply-path"><span data-supply="turbine">TURBINE</span><span data-supply="grid">GRID</span><span data-supply="diesel">DIESELS</span><b>→ AC BUS → PUMPS</b></div></div>
    <div class="supply-switches"><label><input id="grid-supply" type="checkbox" checked/> External grid available</label><label><input id="turbine-supply" type="checkbox" checked/> Turbine auxiliary feed connected</label></div>
    <p>The turbine supplies the pumps while generation is sufficient. Otherwise the grid takes over. Armed diesels start on loss of both normal sources. Instruments have an independent simulated DC supply; coolant pumps require AC.</p>
    <div class="diesel-grid">${['A', 'B'].map(id => `<section class="diesel-card" data-diesel="${id}"><div class="diesel-title"><span class="diesel-symbol">ϟ</span><h3>Diesel ${id}</h3><strong data-diesel-value="${id}-state"></strong></div><div class="diesel-meter"><i data-diesel-meter="${id}"></i></div><dl><div><dt>Fuel reserve</dt><dd data-diesel-value="${id}-fuel"></dd></div><div><dt>Bus load / capacity</dt><dd data-diesel-value="${id}-load"></dd></div><div><dt>Startup</dt><dd data-diesel-value="${id}-startup"></dd></div></dl><label><input type="checkbox" data-diesel-auto="${id}" checked/> Automatic standby</label><div class="button-pair"><button class="control-button compact" data-generator="${id}" data-generator-action="start">Start</button><button class="control-button compact" data-generator="${id}" data-generator-action="stop">Stop</button></div><button class="small-button" data-generator="${id}" data-generator-action="refuel">Refuel diesel</button></section>`).join('')}</div>
    <p>Manual Stop disables that diesel's automatic standby. Running diesels stay running after normal power returns until you stop them. Refuelling requires a stopped diesel. Demo startup: 8 seconds; nominal full-load fuel endurance: 30 simulated minutes. No automatic refuelling.</p>
  </div></dialog>`;

const equipmentDetails = {
    reactor: { tag: 'R-01', name: 'Reactor vessel', description: 'Rod position sets simulated heat output. The primary loop carries heat to the exchanger.', fields: [['Core outlet', 'core'], ['Coolant return', 'return'], ['Rod insertion', 'rods']] },
    exchanger: { tag: 'HX-01', name: 'Heat exchanger', description: 'Transfers simulated heat between two separate circuits. Primary and secondary coolant remain separate.', fields: [['Primary inlet', 'core'], ['Primary return', 'return'], ['Secondary outlet', 'secondary']] },
    pumpA: { tag: 'P-01', name: 'Primary circulation', description: 'Circulates coolant through the reactor and heat exchanger. Rotation and flow follow actual speed, including coast-down.', fields: [['Pump state', 'pumpA'], ['Speed target', 'pumpATarget'], ['Actual speed', 'pumpAActual'], ['Primary flow', 'flow'], ['Stress', 'pumpAStress'], ['Damage', 'pumpADamage']] },
    pumpB: { tag: 'P-02', name: 'Secondary circulation', description: 'Returns cooled secondary coolant to the exchanger. Rotation and flow follow actual speed, including coast-down.', fields: [['Pump state', 'pumpB'], ['Speed target', 'pumpBTarget'], ['Actual speed', 'pumpBActual'], ['Secondary flow', 'secondaryFlow'], ['Stress', 'pumpBStress'], ['Damage', 'pumpBDamage']] },
    turbine: { tag: 'TG-01', name: 'Turbine & generator', description: 'Converts a portion of simulated thermal output to electricity. Output responds to reactor load and secondary flow.', fields: [['Thermal output', 'thermal'], ['Electrical output', 'electric'], ['Actual load', 'load']] }
};

export class NuclearPowerConsole extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        this.simulation = new NuclearPowerSimulation();
        this.selectedEquipment = 'reactor';
        this.selectedRod = 'CR-01';
        this.selectedFuel = null;
        this.logFilter = 'all';
        this.motion = { A: { angle: 0, offset: 0 }, B: { angle: 0, offset: 0 } };
        this.reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
        this.intersecting = true;
        this.onVisibilityChange = () => this.updateActivity();
        this.onViewportResize = () => this.positionCoreMap();
    }

    connectedCallback() {
        if (!this.initialized) this.initialize();
        document.addEventListener('visibilitychange', this.onVisibilityChange);
        this.reducedMotion.addEventListener('change', this.onVisibilityChange);
        window.addEventListener('resize', this.onViewportResize);
        this.intersectionObserver = new IntersectionObserver(entries => {
            this.intersecting = entries[0].isIntersecting;
            this.updateActivity();
        });
        this.intersectionObserver.observe(this);
        this.updateActivity();
        this.update();
    }

    disconnectedCallback() {
        this.closeCoreMap(false);
        this.shadowRoot.querySelector('.auxiliary-dialog')?.close();
        this.stopAlarmSound();
        clearInterval(this.timer);
        this.timer = null;
        cancelAnimationFrame(this.animationFrame);
        this.animationFrame = null;
        this.dataset.animating = 'false';
        this.intersectionObserver?.disconnect();
        document.removeEventListener('visibilitychange', this.onVisibilityChange);
        this.reducedMotion.removeEventListener('change', this.onVisibilityChange);
        window.removeEventListener('resize', this.onViewportResize);
    }

    initialize() {
        this.initialized = true;
        this.setAttribute('lang', 'en');
        this.shadowRoot.innerHTML = `<style>${styles}\n${lightStyles}</style>
        <main class="console">
          <section class="meltdown-banner" hidden role="alert" aria-label="Simulated core meltdown">
            <strong class="meltdown-title">CORE MELTDOWN</strong>
            <p>SIMULATED · Incident recorded · Irreversible core damage</p>
            <p>Reset demo to start a new session. Export the incident before resetting.</p>
            <div><button class="control-button" data-action="export-incident">Export incident report</button> <button class="control-button" data-action="reset">↺ Reset demo</button></div>
          </section>
          <header class="masthead">
            <div class="brand-mark">${radiation}</div>
            <div class="heading"><div class="eyebrow">PÖRSSIOHJAIN / UNIT 01</div><h1>Nuclear Power<span class="title-dot">.</span></h1><p>Energy, under control.</p></div>
            <div class="appearance-tools"><button class="small-button" data-action="sound" aria-pressed="false">Enable alarm sounds</button><button class="small-button power-supply-button" data-action="power-supply" aria-haspopup="dialog"><span>ϟ <span data-value="powerSource">TURBINE GENERATOR</span></span><small>Auxiliary power / manage ↗</small></button><label class="theme-toggle"><input id="light-theme" type="checkbox" role="switch"/> Light theme</label><div class="session"><span class="simulation-badge"><i></i> SIMULATION</span><div class="session-clock" data-value="clock">T+00:00:00</div></div></div>
          </header>

          <div class="console-layout">
          <aside class="instrument-column" aria-label="Plant instruments and equipment detail">
          <section class="metrics" aria-label="Simulated plant instruments">
            <article class="metric core-temperature-metric"><div class="metric-label"><span class="metric-icon warm">↗</span> Core temperature <span class="channel">TI-01</span></div><div class="metric-reading"><span data-value="coreNumber">286.0</span><span class="unit">°C</span></div><div class="metric-foot"><span><i class="status-dot"></i> <span data-value="temperatureState">WITHIN DEMO RANGE</span></span><span>SIMULATED</span></div><div class="metric-track warm-track"><i data-meter="temperature"></i></div></article>
            <article class="metric"><div class="metric-label"><span class="metric-icon warm">ϟ</span> Thermal output <span class="channel">PI-01</span></div><div class="metric-reading"><span data-value="thermalNumber">768</span><span class="unit">MW<span class="subscript">th</span></span></div><div class="metric-foot"><span><span data-value="load">64.0%</span> OF DEMO CAPACITY</span><span>SIMULATED</span></div><div class="metric-track warm-track"><i data-meter="power"></i></div></article>
            <article class="metric"><div class="metric-label"><span class="metric-icon cool">ϟ</span> Electrical output <span class="channel">EI-01</span></div><div class="metric-reading"><span data-value="electricNumber">262</span><span class="unit">MW<span class="subscript">e</span></span></div><div class="metric-foot"><span data-value="electricalTargetLabel">GENERATOR OUTPUT</span><span>SIMULATED</span></div><div class="metric-track"><i data-meter="electric"></i></div></article>
            <article class="metric"><div class="metric-label"><span class="metric-icon cool">≋</span> Primary flow <span class="channel">FI-01</span></div><div class="metric-reading"><span data-value="flowNumber">4,800</span><span class="unit">m³/h</span></div><div class="metric-foot"><span><i class="status-dot" data-indicator="primary"></i> <span data-value="primaryState">CIRCULATING</span></span><span>SIMULATED</span></div><div class="metric-track"><i data-meter="flow"></i></div></article>
            <article class="metric"><div class="metric-label"><span class="metric-icon cool">Ⅱ</span> Rod insertion <span class="channel">ZI-01</span></div><div class="metric-reading"><span data-value="rodsNumber">36.0</span><span class="unit">%</span></div><div class="metric-foot"><span>TARGET <span data-value="rodTarget">36%</span></span><span>SIMULATED</span></div><div class="metric-track"><i data-meter="rods"></i></div></article>
          </section>
          <section class="panel equipment-panel" aria-label="Selected equipment details"><div class="panel-heading"><div><span class="section-index">↳</span><h2>Equipment detail</h2></div><span class="local-tag">SIM</span></div><div class="equipment-content"></div></section>
          <details class="fault-console"><summary>Faults &amp; incident</summary><div><label for="fault-scenario">Simulated fault scenario</label><select id="fault-scenario"><option value="none">No injected fault</option><option value="primary-restriction">Primary-loop obstruction</option><option value="secondary-loss">Secondary cooling loss</option><option value="pump-wear">Primary pump deterioration</option></select><button class="small-button" data-action="clear-fault">Clear injected fault</button><button class="small-button" data-action="repair" disabled>Repair components</button><p>Stop, clear the fault and cool down to enable repair. Meltdown requires Reset demo.</p><p data-value="incidentSummary">No incident recorded.</p><button class="small-button" data-action="export-incident" disabled>Export incident report</button></div></details>
          </aside>

          <section class="control-column" aria-label="Reactor and operator controls">
          <section class="automation-strip" aria-label="Process regulation">
            <div><span class="control-label">CONTROL MODE</span><div class="mode-switch" role="group" aria-label="Control mode"><button data-action="manual" aria-pressed="true">Manual</button><button data-action="auto" aria-pressed="false">Auto</button></div></div>
            <label for="auto-objective">Regulate <select id="auto-objective"><option value="temperature">Core temperature</option><option value="power">Electrical output</option></select></label>
            <label for="auto-electrical" class="electrical-target" hidden>Output target <span><input id="auto-electrical" type="number" min="0" max="400" step="1" value="200"/> MW<span class="subscript">e</span></span></label>
            <label for="auto-temperature"><span data-value="temperatureLabel">Core target</span><span><input id="auto-temperature" type="number" min="40" max="330" step="1" value="286"/> °C</span></label>
            <label for="auto-flow">Circulation target <span><input id="auto-flow" type="number" min="25" max="100" step="1" value="85"/> %</span></label>
            <div class="regulation-status"><strong data-value="regulation">MANUAL CONTROL</strong><span data-value="regulationDetail">Individual rods and pump speeds available.</span><button class="small-button" data-action="fluctuations" aria-pressed="true">Fluctuations: On</button></div>
          </section>

          <div class="workspace">
            <section class="panel process-panel" aria-label="Plant process schematic">
              <div class="panel-heading"><div><span class="section-index">01</span><h2>Reactor overview</h2></div><div class="diagram-tools"><button class="small-button" data-action="core-view" aria-haspopup="dialog" aria-controls="core-map-window" aria-expanded="false">Core map ↗</button><button class="small-button" data-action="zoom" aria-label="Zoom process diagram" aria-pressed="false">Zoom +</button></div></div>
              <div class="annunciator" role="status"><span class="annunciator-icon">●</span><span data-value="annunciator">ALL SYSTEMS NORMAL</span><span class="plant-state" data-value="status">RUNNING</span></div>
              <div class="condition-strip" aria-label="Reactor condition"><strong data-value="conditionStage">NORMAL</strong><span>Fuel <b data-value="peakFuel"></b></span><span>Cooling margin <b data-value="coolingMargin"></b></span><span>Retained heat <b data-value="retainedHeat"></b></span><span>Rate <b data-value="temperatureRate"></b></span><span>Core damage <b data-value="coreDamage"></b></span><span>Pump stress <b data-value="pumpStress"></b></span></div>
              <p class="diagram-scroll-hint">Scroll sideways to explore · select equipment to inspect</p>
              <div class="diagram-viewport" tabindex="0" aria-label="Scrollable plant diagram">${schematic}</div>
              <div class="diagram-legend"><span><i class="legend-line warm-line"></i> Hot / steam</span><span><i class="legend-line cool-line"></i> Coolant return</span><span><i class="legend-dot"></i> Simulated signal</span><span class="diagram-note">Illustrative process · independent demo session</span></div>
            </section>

            <aside class="operator-column" aria-label="Operator console">
              <section class="panel operator-panel"><div class="panel-heading"><div><span class="section-index">02</span><h2>Operator console</h2></div><span class="local-tag">LOCAL</span></div>
                <div class="operator-content">
                  <div class="operation-state"><span><i class="status-dot" data-indicator="reactor"></i> REACTOR STATE</span><strong data-value="status">RUNNING</strong></div>
                  <div class="button-pair"><button class="control-button start-button" data-action="start">▶ Start</button><button class="control-button" data-action="stop">■ Stop</button></div>
                  <section class="trip-recovery" hidden aria-label="Simulated trip recovery"><strong>SHUTDOWN RECOVERY</strong><ul></ul><button class="control-button" data-action="reset-trip" disabled>Reset trip</button><p>Reset clears the latch. Then press Start to resume.</p></section>
                  <div class="setpoint"><label for="load">Load setpoint <strong data-value="demand">64%</strong></label><input id="load" type="range" min="0" max="100" value="64" aria-label="Load setpoint"/><div class="range-labels"><span>0%</span><span>100%</span></div><div class="button-pair"><button class="control-button compact" data-action="load-down">− Decrease</button><button class="control-button compact" data-action="load-up">+ Increase</button></div></div>
                  <div class="rod-control"><div class="control-label">CONTROL RODS <span>IN <span data-value="rodTarget">36%</span></span></div><div class="button-pair"><button class="control-button compact" data-action="insert">↓ Insert</button><button class="control-button compact" data-action="withdraw">↑ Withdraw</button></div></div>
                  <div class="pump-controls">${['A', 'B'].map(pump => `<div class="pump-control"><button class="pump-button" data-action="pump${pump}" aria-pressed="true"><span class="pump-symbol">✣</span><span><strong>Pump ${pump}</strong><small>${pump === 'A' ? 'Primary' : 'Secondary'} circuit</small></span><span class="pump-badge" data-value="pump${pump}">RUNNING</span></button><label for="pump-${pump}-speed">Speed target <strong data-value="pump${pump}Target">100%</strong></label><input id="pump-${pump}-speed" data-pump-speed="${pump}" type="range" min="0" max="100" step="1" value="100"/><div class="pump-readback"><span>ACTUAL <strong data-value="pump${pump}Actual">100.0%</strong></span><span data-value="${pump === 'A' ? 'flow' : 'secondaryFlow'}"></span></div></div>`).join('')}</div>
                  <button class="emergency-button" data-action="trip"><span class="emergency-symbol">!</span><span>Emergency shutdown<small>INSERT ALL RODS / SIMULATION</small></span><span>↘</span></button>
                  <p class="control-note">Every control operates this simulation only.</p>
                </div>
              </section>
            </aside>
          </div>
          </section>
          <aside class="analysis-column" aria-label="Trends and event journal">
            <section class="panel trend-panel" aria-label="Simulated temperature and output trends"><div class="panel-heading"><div><span class="section-index">03</span><h2>Live trends</h2></div><span class="local-tag">SIMULATED / 2 MIN</span></div>
              <div class="trend-legend"><span><i class="legend-dot warm-dot"></i> Core temperature <strong data-value="core">286.0 °C</strong></span><span><i class="legend-dot"></i> <span data-value="trendOutputLabel">Reactor output</span> <strong data-value="trendOutput">64.0%</strong></span><span class="target-legend" hidden><i class="legend-target"></i> Electrical target <strong data-value="electricalTarget"></strong></span></div>
              <svg class="trend-chart" viewBox="0 0 760 300" role="img" aria-label="Simulated temperature and reactor output over the last two minutes">
                <defs><linearGradient id="trend-fill" x2="0" y2="1"><stop stop-color="#efa35c" stop-opacity=".12"/><stop offset="1" stop-color="#efa35c" stop-opacity="0"/></linearGradient></defs>
                <g class="chart-grid"><path d="M40 40H715M40 145H715M40 250H715M40 40V250M208 40V250M377 40V250M546 40V250M715 40V250"/></g>
                <g class="chart-labels"><text x="0" y="22">°C</text><text x="725" y="22" data-value="chartOutputUnit">%</text><text x="0" y="44" data-value="chartTempMax">400</text><text x="0" y="149" data-value="chartTempMid">200</text><text x="12" y="254">0</text><text x="725" y="44" data-value="chartOutputMax">100</text><text x="731" y="149" data-value="chartOutputMid">50</text><text x="739" y="254">0</text><text x="40" y="282">−2 MIN</text><text x="355" y="282">−1 MIN</text><text x="675" y="282">NOW</text></g>
                <path class="trend-area"/><path class="temperature-trace"/><path class="power-trace"/><path class="target-trace"/><circle class="temperature-dot" r="3"/><circle class="power-dot" r="3"/>
              </svg><p class="trend-note">Trends build during this session. Both axes show simulated values.</p>
            </section>
            <section class="panel events-panel" aria-label="Simulation event log"><div class="panel-heading"><div><span class="section-index">04</span><h2>Event journal</h2></div><span class="event-count">0 EVENTS</span></div><div class="log-toolbar"><label for="log-filter">Show <select id="log-filter"><option value="all">All events</option><option value="operator">Operator</option><option value="automation">Automation</option><option value="process">Process readings</option><option value="alarm">Alarms</option><option value="system">System</option></select></label><span>Latest 200 events · this session</span><button class="small-button" data-action="export-log">↓ Export CSV</button></div><div class="alarm-list" aria-live="polite"></div><ol class="event-list" aria-label="Recent simulation events"></ol><button class="ack-button" data-action="acknowledge" disabled>✓ Acknowledge alarms</button></section>
          </aside>
          </div>
          <footer class="console-footer"><span><i class="status-dot"></i> <span data-value="sessionState">SIMULATION ACTIVE</span><span class="footer-divider">/</span> ALL SIGNALS SIMULATED</span><div><button data-action="pause" class="footer-button">Ⅱ Pause</button><button data-action="reset" class="footer-button">↺ Reset demo</button></div><span class="footer-signature">PÖRSSIOHJAIN <span>NUCLEAR DIVISION</span></span></footer>
          <div id="core-map-window" class="core-dialog" popover="manual" role="dialog" aria-modal="false" aria-labelledby="core-map-title">
            <div class="core-dialog-titlebar" tabindex="0" aria-label="Move core map with arrow keys or drag this title bar"><span aria-hidden="true">⠿</span><h2 id="core-map-title">Reactor core map</h2><span class="local-tag">DRAG TO MOVE</span><button class="small-button" data-action="close-core" aria-label="Close core map">×</button></div>
            ${coreMap}
          </div>
          ${auxiliaryDialog}
        </main>`;

        let savedTheme = 'dark';
        try { if (localStorage.getItem('porssiohjain.nuclear-power.theme') === 'light') savedTheme = 'light'; } catch { /* Storage can be disabled. */ }
        this.setTheme(savedTheme, false);
        this.shadowRoot.querySelector('#light-theme').addEventListener('change', event => this.setTheme(event.target.checked ? 'light' : 'dark'));
        this.shadowRoot.querySelector('#auto-objective').addEventListener('change', event => {
            this.simulation.setAutoObjective(event.target.value);
            this.update();
        });
        this.shadowRoot.querySelector('#auto-electrical').addEventListener('change', event => {
            if (!event.target.checkValidity() || event.target.value === '') { event.target.value = this.simulation.autoElectricalTarget; return; }
            this.simulation.setElectricalTarget(Number(event.target.value));
            this.update();
        });
        this.shadowRoot.querySelector('#fault-scenario').addEventListener('change', event => {
            this.simulation.injectFault(event.target.value);
            this.update();
        });

        this.shadowRoot.addEventListener('click', event => {
            const fuel = event.target.closest('[data-fuel-id]');
            if (fuel) this.selectFuel(fuel.dataset.fuelId);
            const generator = event.target.closest('[data-generator-action]');
            if (generator && !generator.disabled) {
                this.simulation.supply.command(this.simulation, generator.dataset.generator, generator.dataset.generatorAction);
                this.update();
            }
            const rod = event.target.closest('[data-rod]');
            if (rod) this.selectRod(rod.dataset.rod);
            const equipment = event.target.closest('[data-equipment]');
            if (equipment) {
                this.inspect(equipment.dataset.equipment);
                if (equipment.dataset.equipment === 'reactor') this.openCoreMap();
            }
            const button = event.target.closest('button[data-action]');
            if (button && !button.disabled) this.act(button.dataset.action);
        });
        this.shadowRoot.addEventListener('keydown', event => {
            if (event.key === 'Escape' && this.shadowRoot.querySelector('.core-dialog').matches(':popover-open')) {
                event.preventDefault();
                this.closeCoreMap();
            }
            const rod = event.target.closest('[data-rod]');
            const fuel = event.target.closest('[data-fuel-id]');
            if (fuel && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault();
                this.selectFuel(fuel.dataset.fuelId);
            }
            if (rod && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault();
                this.selectRod(rod.dataset.rod);
            }
            const equipment = event.target.closest('[data-equipment]');
            if (equipment && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault();
                this.inspect(equipment.dataset.equipment);
                if (equipment.dataset.equipment === 'reactor') this.openCoreMap();
            }
        });
        this.configureCoreMapDrag();
        this.shadowRoot.querySelector('#rod-override').addEventListener('change', event => {
            this.simulation.setRodOverride(this.selectedRod, event.target.checked);
            this.update();
        });
        this.shadowRoot.querySelector('#fuel-rate').addEventListener('change', event => {
            this.simulation.fuel.setRate(this.simulation, Number(event.target.value));
            this.update();
        });
        for (const [id, method] of [['grid-supply', 'setGrid'], ['turbine-supply', 'setTurbine']]) {
            this.shadowRoot.querySelector(`#${id}`).addEventListener('change', event => {
                this.simulation.supply[method](this.simulation, event.target.checked);
                this.update();
            });
        }
        for (const input of this.shadowRoot.querySelectorAll('[data-diesel-auto]')) input.addEventListener('change', () => {
            this.simulation.supply.command(this.simulation, input.dataset.dieselAuto, 'auto');
            this.update();
        });
        this.shadowRoot.querySelector('#load').addEventListener('input', event => {
            // Update the requested setting immediately; journal once on commit.
            this.shadowRoot.querySelectorAll('[data-value="demand"]').forEach(element => { element.textContent = `${event.target.value}%`; });
        });
        this.shadowRoot.querySelector('#load').addEventListener('change', event => {
            this.simulation.setLoad(Number(event.target.value));
            this.update();
        });
        for (const input of this.shadowRoot.querySelectorAll('[data-pump-speed]')) {
            input.addEventListener('input', () => {
                this.shadowRoot.querySelector(`[data-value="pump${input.dataset.pumpSpeed}Target"]`).textContent = `${input.value}%`;
            });
            input.addEventListener('change', () => {
                this.simulation.setPumpSpeed(input.dataset.pumpSpeed, Number(input.value));
                this.update();
            });
        }
        this.shadowRoot.querySelector('#rod-select').addEventListener('change', event => this.selectRod(event.target.value));
        this.shadowRoot.querySelector('#individual-rod').addEventListener('input', event => {
            this.shadowRoot.querySelector('[data-value="selectedRodTarget"]').textContent = `${event.target.value}%`;
        });
        this.shadowRoot.querySelector('#individual-rod').addEventListener('change', event => {
            this.simulation.setRod(this.selectedRod, Number(event.target.value));
            this.update();
        });
        for (const id of ['auto-temperature', 'auto-flow']) {
            this.shadowRoot.querySelector(`#${id}`).addEventListener('change', event => {
                if (!event.target.checkValidity() || event.target.value === '') {
                    event.target.value = id === 'auto-temperature' ? this.simulation.autoTemperature : this.simulation.autoFlow;
                    return;
                }
                this.simulation.setAutoTargets(Number(this.shadowRoot.querySelector('#auto-temperature').value),
                    Number(this.shadowRoot.querySelector('#auto-flow').value));
                this.update();
            });
        }
        this.shadowRoot.querySelector('#log-filter').addEventListener('change', event => {
            this.logFilter = event.target.value;
            this.updateEvents();
        });
        this.inspect('reactor');
    }

    setTheme(theme, persist = true) {
        this.dataset.theme = theme === 'light' ? 'light' : 'dark';
        this.shadowRoot.querySelector('#light-theme').checked = this.dataset.theme === 'light';
        if (persist) {
            try { localStorage.setItem('porssiohjain.nuclear-power.theme', this.dataset.theme); } catch { /* Theme still works without storage. */ }
        }
    }

    selectRod(id) {
        if (!this.simulation.controlRods.some(rod => rod.id === id)) return;
        this.selectedRod = id;
        this.selectedFuel = null;
        this.shadowRoot.querySelector('#rod-select').value = id;
        this.update();
    }

    selectFuel(id) {
        if (!this.simulation.fuel.assemblies.some(a => a.id === id)) return;
        this.selectedFuel = id;
        this.update();
    }

    openCoreMap() {
        const dialog = this.shadowRoot.querySelector('.core-dialog');
        if (!dialog.matches(':popover-open')) dialog.showPopover();
        this.positionCoreMap();
        for (const trigger of this.shadowRoot.querySelectorAll('[aria-controls="core-map-window"]')) trigger.setAttribute('aria-expanded', 'true');
        this.shadowRoot.querySelector(this.selectedFuel ? '[data-action="replace-fuel"]' : '#rod-select').focus({ preventScroll: true });
    }

    closeCoreMap(restoreFocus = true) {
        const dialog = this.shadowRoot?.querySelector('.core-dialog');
        if (!dialog?.matches(':popover-open')) return;
        dialog.hidePopover();
        for (const trigger of this.shadowRoot.querySelectorAll('[aria-controls="core-map-window"]')) trigger.setAttribute('aria-expanded', 'false');
        if (restoreFocus && this.isConnected) this.shadowRoot.querySelector('[data-equipment="reactor"]').focus({ preventScroll: true });
    }

    positionCoreMap(left, top) {
        const dialog = this.shadowRoot?.querySelector('.core-dialog');
        if (!dialog?.matches(':popover-open')) return;
        const rect = dialog.getBoundingClientRect();
        const x = left ?? (dialog.style.left ? rect.left : innerWidth - rect.width - 20);
        const y = top ?? (dialog.style.top ? rect.top : 80);
        dialog.style.left = `${Math.max(12, Math.min(x, innerWidth - rect.width - 12))}px`;
        dialog.style.top = `${Math.max(12, Math.min(y, innerHeight - rect.height - 12))}px`;
    }

    configureCoreMapDrag() {
        const header = this.shadowRoot.querySelector('.core-dialog-titlebar');
        let drag;
        header.addEventListener('pointerdown', event => {
            if (event.button !== 0 || event.target.closest('button')) return;
            const rect = this.shadowRoot.querySelector('.core-dialog').getBoundingClientRect();
            drag = { x: event.clientX, y: event.clientY, left: rect.left, top: rect.top };
            header.setPointerCapture(event.pointerId);
            event.preventDefault();
        });
        header.addEventListener('pointermove', event => {
            if (drag) this.positionCoreMap(drag.left + event.clientX - drag.x, drag.top + event.clientY - drag.y);
        });
        for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) header.addEventListener(type, () => { drag = null; });
        header.addEventListener('keydown', event => {
            const deltas = { ArrowLeft: [-16, 0], ArrowRight: [16, 0], ArrowUp: [0, -16], ArrowDown: [0, 16] };
            if (event.target !== header || !deltas[event.key]) return;
            event.preventDefault();
            const rect = this.shadowRoot.querySelector('.core-dialog').getBoundingClientRect();
            this.positionCoreMap(rect.left + deltas[event.key][0], rect.top + deltas[event.key][1]);
        });
    }

    inspect(key) {
        const equipment = equipmentDetails[key];
        if (!equipment) return;
        this.selectedEquipment = key;
        this.shadowRoot.querySelectorAll('[data-equipment]').forEach(element => {
            element.classList.toggle('selected', element.dataset.equipment === key);
            element.setAttribute('aria-pressed', String(element.dataset.equipment === key));
        });
        // Only static, developer-owned equipment descriptions enter this template.
        this.shadowRoot.querySelector('.equipment-content').innerHTML = `<div class="equipment-title"><span>${equipment.tag}</span><h3>${equipment.name}</h3></div><p>${equipment.description}</p><dl>${equipment.fields.map(([label, value]) => `<div><dt>${label}</dt><dd data-value="${value}"></dd></div>`).join('')}</dl>`;
        this.update();
    }

    act(action) {
        const sim = this.simulation;
        if (this.alarmAudio && this.soundEnabled) this.alarmAudio.resume().catch(() => {});
        const actions = {
            sound: () => {
                this.soundEnabled = !this.soundEnabled;
                if (this.soundEnabled) {
                    const Audio = window.AudioContext || window.webkitAudioContext;
                    if (Audio) {
                        this.alarmAudio ||= new Audio();
                        this.alarmAudio.resume().catch(() => {});
                    } else this.soundEnabled = false;
                }
                if (!this.soundEnabled) this.stopAlarmSound();
            },
            start: () => sim.start(), stop: () => sim.stop(),
            'load-down': () => sim.setLoad(sim.demand - 5), 'load-up': () => sim.setLoad(sim.demand + 5),
            insert: () => sim.moveRods(5), withdraw: () => sim.moveRods(-5),
            pumpA: () => sim.togglePump('A'), pumpB: () => sim.togglePump('B'),
            trip: () => sim.trip(), acknowledge: () => sim.acknowledge(),
            'reset-trip': () => sim.resetTrip(),
            'rod-insert': () => sim.setRod(this.selectedRod, sim.controlRods.find(rod => rod.id === this.selectedRod).target + 1),
            'rod-withdraw': () => sim.setRod(this.selectedRod, sim.controlRods.find(rod => rod.id === this.selectedRod).target - 1),
            'core-view': () => this.openCoreMap(), 'close-core': () => this.closeCoreMap(),
            'replace-fuel': () => sim.replaceFuel(this.selectedFuel),
            'select-control-rod': () => this.selectRod(this.selectedRod),
            'power-supply': () => { this.closeCoreMap(false); this.shadowRoot.querySelector('.auxiliary-dialog').showModal(); },
            'close-power': () => this.shadowRoot.querySelector('.auxiliary-dialog').close(),
            manual: () => sim.setAutoMode(false), auto: () => sim.setAutoMode(true),
            fluctuations: () => sim.setFluctuations(!sim.fluctuationsEnabled),
            'export-log': () => this.exportLog(),
            'clear-fault': () => sim.injectFault('none'), repair: () => sim.repairComponents(),
            'export-incident': () => this.exportIncident(),
            pause: () => sim.togglePause(), reset: () => sim.reset(),
            zoom: () => {
                const viewport = this.shadowRoot.querySelector('.diagram-viewport');
                const zoomed = viewport.classList.toggle('zoomed');
                const button = this.shadowRoot.querySelector('[data-action="zoom"]');
                button.textContent = zoomed ? 'Fit view' : 'Zoom +';
                button.setAttribute('aria-pressed', String(zoomed));
                button.setAttribute('aria-label', zoomed ? 'Fit process diagram' : 'Zoom process diagram');
            }
        };
        actions[action]?.();
        this.updateActivity();
        this.update();
    }

    updateActivity() {
        const active = this.isConnected && this.intersecting && !document.hidden && !this.simulation.paused;
        this.dataset.animating = String(active);
        if (!active) this.stopAlarmSound();
        if (!active) {
            this.closeCoreMap(false);
            // Pause keeps the management dialog open; hiding the feature closes it.
            if (!this.isConnected || !this.intersecting || document.hidden) this.shadowRoot.querySelector('.auxiliary-dialog')?.close();
        }
        if (active && !this.timer) {
            this.lastTick = performance.now();
            this.timer = setInterval(() => {
                const now = performance.now();
                this.simulation.tick((now - this.lastTick) / 1000);
                this.lastTick = now;
                this.update();
            }, 250);
        } else if (!active && this.timer) {
            clearInterval(this.timer);
            this.timer = null;
        }
        if (active && !this.reducedMotion.matches && !this.animationFrame) {
            this.lastFrame = performance.now();
            const frame = now => {
                const dt = (now - this.lastFrame) / 1000;
                this.lastFrame = now;
                this.drawMotion(dt);
                this.animationFrame = requestAnimationFrame(frame);
            };
            this.animationFrame = requestAnimationFrame(frame);
        } else if ((!active || this.reducedMotion.matches) && this.animationFrame) {
            cancelAnimationFrame(this.animationFrame);
            this.animationFrame = null;
        }
    }

    drawMotion(seconds) {
        for (const [pump, flow, circuit] of [['A', this.simulation.primaryFlow, 'primary'], ['B', this.simulation.secondaryFlow, 'secondary']]) {
            const motion = this.motion[pump] = advancePumpMotion(this.motion[pump], flow, seconds);
            this.shadowRoot.querySelector(`.pump-${pump.toLowerCase()} .rotor`).setAttribute('transform', `rotate(${motion.angle} 0 0)`);
            for (const path of this.shadowRoot.querySelectorAll(`.flow.${circuit}`)) path.style.strokeDashoffset = motion.offset;
        }
    }

    stopAlarmSound() {
        for (const node of this.alarmNodes || []) {
            try { node.stop(); } catch { /* Already stopped. */ }
            node.disconnect();
        }
        this.alarmNodes = [];
        this.alarmAudio?.suspend().catch(() => {});
        this.nextAlarmAt = 0;
    }

    updateAlarmSound() {
        const sim = this.simulation;
        const meltdown = sim.condition.meltdown;
        const pending = sim.alarms.some(alarm => !alarm.acknowledged);
        const enabled = this.soundEnabled && this.isConnected && this.intersecting && !document.hidden && !sim.paused;
        const button = this.shadowRoot.querySelector('[data-action="sound"]');
        button.textContent = this.soundEnabled ? 'Mute alarm sounds' : 'Enable alarm sounds';
        button.setAttribute('aria-pressed', String(!!this.soundEnabled));
        if (!enabled || (!meltdown && !pending)) { this.stopAlarmSound(); return; }
        const context = this.alarmAudio;
        if (!context) return;
        if (context.state === 'suspended') { context.resume().catch(() => {}); return; }
        const now = context.currentTime;
        const mode = meltdown ? 'meltdown' : 'attention';
        if (this.alarmMode !== mode) { this.nextAlarmAt = 0; this.alarmMode = mode; }
        if (now < (this.nextAlarmAt || 0)) return;
        this.alarmNodes = (this.alarmNodes || []).filter(node => {
            if (node.alarmEnd > now) return true;
            node.disconnect(); return false;
        });
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        oscillator.type = meltdown ? 'triangle' : 'sine';
        oscillator.frequency.setValueAtTime(meltdown ? 520 : 880, now);
        oscillator.frequency.linearRampToValueAtTime(meltdown ? 1050 : 660, now + 0.35);
        oscillator.frequency.linearRampToValueAtTime(meltdown ? 520 : 660, now + 0.7);
        gain.gain.setValueAtTime(0, now);
        gain.gain.linearRampToValueAtTime(0.09, now + 0.03);
        gain.gain.setValueAtTime(0.09, now + 0.55);
        gain.gain.linearRampToValueAtTime(0, now + 0.75);
        oscillator.connect(gain); gain.connect(context.destination);
        oscillator.alarmEnd = now + 0.8;
        oscillator.onended = () => gain.disconnect();
        oscillator.start(now); oscillator.stop(now + 0.8);
        this.alarmNodes.push(oscillator);
        this.nextAlarmAt = now + (meltdown ? 0.85 : 3);
    }

    update() {
        if (!this.initialized) return;
        const sim = this.simulation;
        const { signals, status } = sim.snapshot();
        this.classList.toggle('meltdown-active', sim.condition.meltdown);
        this.shadowRoot.querySelector('.meltdown-banner').hidden = !sim.condition.meltdown;
        this.updateAlarmSound();
        const n = (value, decimals = 0) => value.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
        const percent = value => `${value.toLocaleString('en-US', { maximumFractionDigits: 1 })}%`;
        const selectedRod = sim.controlRods.find(rod => rod.id === this.selectedRod);
        const powerObjective = sim.autoObjective === 'power';
        const condition = sim.condition;
        const zone = condition.zones.find(zone => zone.id === this.selectedRod);
        const fuel = sim.fuel.assemblies.find(a => a.id === this.selectedFuel) || sim.fuel.assemblies[0];
        const fuelReadiness = sim.fuel.replacementReadiness(sim);
        const rodBlocked = sim.tripped || !sim.running || (sim.autoMode && !selectedRod.manualOverride);
        const pumpState = (enabled, target, actual) => actual < 0.5 ? 'STOPPED' : !enabled || target === 0 || sim.supply.factor === 0 ? 'COASTING' : 'RUNNING';
        const values = {
            core: `${n(signals.coreTemperature.value, 1)} °C`, coreNumber: n(signals.coreTemperature.value, 1),
            return: `${n(signals.returnTemperature.value, 1)} °C`, secondary: `${n(signals.secondaryTemperature.value, 1)} °C`,
            thermal: `${n(signals.thermalOutput.value)} MW`, thermalNumber: n(signals.thermalOutput.value),
            electric: `${n(signals.electricalOutput.value)} MW`, electricNumber: n(signals.electricalOutput.value),
            electricalTargetLabel: sim.autoObjective === 'power' ? `TARGET ${n(sim.autoElectricalTarget)} MW ELECTRIC` : 'GENERATOR OUTPUT',
            temperatureLabel: sim.autoObjective === 'power' ? 'Core limit' : 'Core target',
            trendOutputLabel: powerObjective ? 'Electrical output' : 'Reactor output',
            trendOutput: powerObjective ? `${n(signals.electricalOutput.value, 1)} MW` : `${n(sim.power, 1)}%`,
            electricalTarget: `${n(sim.autoElectricalTarget)} MW`,
            chartOutputUnit: powerObjective ? 'MW' : '%', chartOutputMax: powerObjective ? '400' : '100', chartOutputMid: powerObjective ? '200' : '50',
            flow: `${n(signals.primaryFlow.value)} m³/h`, flowNumber: n(signals.primaryFlow.value),
            rods: `${n(sim.rods, 1)}%`, rodsNumber: n(sim.rods, 1), rodTarget: percent(sim.rodTarget),
            load: `${n(sim.power, 1)}%`, demand: percent(sim.demand), status,
            secondaryFlow: `${n(signals.secondaryFlow.value)} m³/h`,
            pumpA: pumpState(sim.pumpA, sim.pumpASpeed, sim.primaryFlow), pumpB: pumpState(sim.pumpB, sim.pumpBSpeed, sim.secondaryFlow),
            pumpATarget: percent(sim.pumpASpeed), pumpBTarget: percent(sim.pumpBSpeed),
            pumpAActual: `${n(sim.primaryFlow, 1)}%`, pumpBActual: `${n(sim.secondaryFlow, 1)}%`,
            selectedRodActual: `${n(selectedRod.position, 1)}%`, selectedRodTarget: percent(selectedRod.target),
            selectedRodMotion: Math.abs(selectedRod.target - selectedRod.position) < 0.5 ? 'AT TARGET' : selectedRod.target > selectedRod.position ? 'INSERTING' : 'WITHDRAWING',
            rodOwner: sim.autoMode ? selectedRod.manualOverride ? 'MANUAL OVERRIDE · Auto controls other rods' : 'AUTO OWNED · enable override to disturb regulation' : 'MANUAL CONTROL',
            selectedFuelId: fuel.id, selectedFuelReserve: `${n(fuel.remaining, 1)}%`,
            selectedFuelState: fuel.remaining <= 0 ? 'DEPLETED' : fuel.remaining <= 20 ? 'LOW RESERVE' : 'IN SERVICE',
            selectedFuelEffectiveness: percent(sim.fuel.effectiveness(fuel) * 100),
            selectedFuelExposure: `${n(fuel.exposure, 2)} demo FPH`, selectedFuelReplacements: String(fuel.replacements),
            fuelReserve: percent(sim.fuel.remaining), fuelCapability: percent(sim.fuel.capability * 100),
            fuelReplacementReadiness: fuelReadiness.ready ? 'Ready for manual replacement. Reactor will remain stopped.' : fuelReadiness.reasons.join('. ') + '.',
            powerSource: sim.supply.source, auxiliaryLoad: `${n(sim.supply.demand, 2)} MW requested / ${n(sim.supply.capacity, 1)} MW available`,
            mode: sim.autoMode ? 'AUTO' : 'MANUAL',
            conditionStage: condition.stage,
            peakFuel: `${n(condition.peakTemperature, 1)} °C`,
            retainedHeat: `${n(condition.heatReserve * 1.5)} MW`,
            pumpAStress: percent(condition.components.A.stress), pumpBStress: percent(condition.components.B.stress),
            pumpADamage: percent(condition.components.A.damage), pumpBDamage: percent(condition.components.B.damage),
            coolingMargin: `${condition.coolingMargin >= 0 ? '+' : ''}${n(condition.coolingMargin)} MW`,
            temperatureRate: `${condition.temperatureRate >= 0 ? '+' : ''}${n(condition.temperatureRate, 1)} °C/min`,
            coreDamage: `${n(condition.coreDamage, 1)}%`,
            pumpStress: `A ${n(condition.components.A.stress)}% / B ${n(condition.components.B.stress)}%`,
            selectedZoneTemperature: `${n(zone.temperature, 1)} °C`, selectedZoneDamage: `${n(zone.damage, 1)}%`,
            incidentSummary: condition.incident ? `${this.time(condition.incident.startedAt)}: ${condition.incident.cause}. ${condition.incident.timeline.length} retained incident events${condition.incident.resolvedAt ? ' · recovered' : ''}.` : 'No incident recorded.',
            regulation: sim.tripped ? 'TRIP / AUTOMATION INHIBITED' : !sim.running ? 'STOPPED / WAITING FOR START' : sim.autoMode
                ? sim.autoObjective === 'power' ? `AUTO / ${n(signals.electricalOutput.value, 1)} / ${n(sim.autoElectricalTarget)} MW ELECTRIC` : `AUTO / ERROR ${n(sim.autoTemperature - sim.temperature, 1)} °C` : 'MANUAL CONTROL',
            regulationDetail: sim.autoMode ? sim.regulationLimit || (sim.autoObjective === 'power' ? 'Tracking electrical output; core setting limits temperature.' : 'Rods and pump speeds adjust to hold your targets.') : 'Individual rods and pump speeds available.',
            clock: `T+${this.time(sim.elapsed)}`,
            sessionState: sim.paused ? 'SIMULATION PAUSED' : 'SIMULATION ACTIVE',
            temperatureState: sim.temperature > 335 ? 'HIGH TEMPERATURE' : sim.running ? 'WITHIN DEMO RANGE' : 'COOLING DOWN',
            primaryState: sim.primaryFlow > 20 ? 'CIRCULATING' : 'LOW FLOW',
            annunciator: condition.meltdown ? 'CORE MELTDOWN · INCIDENT RECORDED · RESET DEMO REQUIRED' : sim.alarms.length ? `${sim.alarms.length} ACTIVE ${sim.alarms.length === 1 ? 'CONDITION' : 'CONDITIONS'} · ${sim.alarms[0].message.toUpperCase()}` : 'ALL SYSTEMS NORMAL'
        };
        for (const element of this.shadowRoot.querySelectorAll('[data-value]')) {
            if (values[element.dataset.value] !== undefined && element.textContent !== values[element.dataset.value]) element.textContent = values[element.dataset.value];
        }
        this.dataset.state = sim.tripped ? 'tripped' : sim.running ? 'running' : 'stopped';
        this.dataset.overheated = String(sim.temperature > 335);
        this.dataset.condition = condition.stage.toLowerCase().replaceAll(' ', '-');
        this.dataset.pumpA = String(sim.pumpA);
        this.dataset.pumpB = String(sim.pumpB);
        this.dataset.primaryFlow = String(sim.primaryFlow >= 0.5);
        this.dataset.secondaryFlow = String(sim.secondaryFlow >= 0.5);
        this.dataset.turbine = String(sim.power > 2 && sim.secondaryFlow > 10);
        this.style.setProperty('--core-intensity', Math.min(1, Math.max(0.1, sim.power / 100, (condition.peakTemperature - 300) / 400)));
        const meltScale = 1 - condition.meltProgress * 0.25;
        this.shadowRoot.querySelector('.fuel-bundle').setAttribute('transform', `translate(0 ${350 * (1 - meltScale) + condition.meltProgress * 8}) scale(1 ${meltScale})`);
        this.shadowRoot.querySelector('.melt-pool').setAttribute('opacity', condition.meltProgress);
        this.shadowRoot.querySelector('.melt-pool').setAttribute('ry', 9 + condition.meltProgress * 15);
        this.updateCore();
        const meters = { temperature: Math.min(100, sim.temperature / 4), power: sim.power, electric: signals.electricalOutput.value / 4.1, flow: sim.primaryFlow, rods: sim.rods };
        for (const [key, value] of Object.entries(meters)) this.shadowRoot.querySelector(`[data-meter="${key}"]`).style.width = `${value}%`;
        for (const pump of ['A', 'B']) this.shadowRoot.querySelector(`[data-action="pump${pump}"]`).setAttribute('aria-pressed', String(sim[`pump${pump}`]));
        for (const pump of ['A', 'B']) {
            const component = condition.components[pump];
            const button = this.shadowRoot.querySelector(`[data-action="pump${pump}"]`);
            button.dataset.condition = component.damage >= 1 ? 'damaged' : component.stress >= 25 ? 'strained' : 'normal';
            button.title = `Actual speed ${n(sim[pump === 'A' ? 'primaryFlow' : 'secondaryFlow'], 1)}%; stress ${n(component.stress)}%; damage ${n(component.damage, 1)}%`;
        }
        this.shadowRoot.querySelector('[data-indicator="primary"]').classList.toggle('off', !sim.pumpA);
        this.shadowRoot.querySelector('[data-indicator="reactor"]').classList.toggle('off', !sim.running);
        const disabled = {
            start: sim.running || sim.tripped || !sim.circulationReady() || sim.temperature >= 335 || condition.damaged || condition.meltdown || sim.fuel.capability <= 0, stop: !sim.running, trip: sim.tripped,
            insert: sim.autoMode || !sim.running || sim.tripped || sim.rodTarget >= 100,
            withdraw: sim.autoMode || !sim.running || sim.tripped || sim.rodTarget <= 0,
            'rod-insert': rodBlocked || selectedRod.target >= 100,
            'rod-withdraw': rodBlocked || selectedRod.target <= 0,
            'replace-fuel': !fuelReadiness.ready || !this.selectedFuel,
            'load-down': sim.autoMode || sim.tripped || sim.demand <= 0, 'load-up': sim.autoMode || sim.tripped || sim.demand >= 100,
            'reset-trip': !sim.tripped || !sim.tripRecovery().ready,
            repair: !condition.canRepair(sim), 'export-incident': !condition.incident,
            acknowledge: !sim.alarms.some(alarm => !alarm.acknowledged)
        };
        for (const [action, value] of Object.entries(disabled)) this.shadowRoot.querySelector(`[data-action="${action}"]`).disabled = value;
        const load = this.shadowRoot.querySelector('#load');
        // Preserve a pointer/keyboard adjustment until its change event is committed.
        if (this.shadowRoot.activeElement !== load) load.value = sim.demand;
        load.disabled = sim.tripped || sim.autoMode;
        const inputs = {
            '#individual-rod': [selectedRod.target, rodBlocked],
            '#pump-A-speed': [sim.pumpASpeed, sim.autoMode && sim.running],
            '#pump-B-speed': [sim.pumpBSpeed, sim.autoMode && sim.running],
            '#auto-temperature': [sim.autoTemperature, false], '#auto-flow': [sim.autoFlow, false],
            '#auto-electrical': [sim.autoElectricalTarget, false]
        };
        for (const [selector, [value, isDisabled]] of Object.entries(inputs)) {
            const input = this.shadowRoot.querySelector(selector);
            if (this.shadowRoot.activeElement !== input) input.value = value;
            input.disabled = isDisabled;
        }
        this.shadowRoot.querySelector('#auto-objective').value = sim.autoObjective;
        this.shadowRoot.querySelector('#rod-override').checked = selectedRod.manualOverride;
        this.shadowRoot.querySelector('#rod-override').disabled = !sim.autoMode || !sim.running || sim.tripped;
        this.shadowRoot.querySelector('#fuel-rate').value = String(sim.fuel.rate);
        this.shadowRoot.querySelector('.individual-rod:not(.fuel-inspector)').hidden = !!this.selectedFuel;
        this.shadowRoot.querySelector('.fuel-inspector').hidden = !this.selectedFuel;
        this.updateSupply();
        this.shadowRoot.querySelector('#fault-scenario').value = condition.fault;
        this.shadowRoot.querySelector('.electrical-target').hidden = sim.autoObjective !== 'power';
        for (const mode of ['manual', 'auto']) this.shadowRoot.querySelector(`[data-action="${mode}"]`).setAttribute('aria-pressed', String(sim.autoMode === (mode === 'auto')));
        const fluctuations = this.shadowRoot.querySelector('[data-action="fluctuations"]');
        fluctuations.textContent = `Fluctuations: ${sim.fluctuationsEnabled ? 'On' : 'Off'}`;
        fluctuations.setAttribute('aria-pressed', String(sim.fluctuationsEnabled));
        const recovery = this.shadowRoot.querySelector('.trip-recovery');
        recovery.hidden = !sim.tripped;
        const checks = sim.tripRecovery().checks;
        const recoverySignature = JSON.stringify(checks);
        if (this.recoverySignature !== recoverySignature) {
            this.recoverySignature = recoverySignature;
            recovery.querySelector('ul').replaceChildren(...checks.map(check => {
                const item = document.createElement('li');
                item.textContent = `${check.ready ? '✓' : '○'} ${check.label}`;
                item.classList.toggle('ready', check.ready);
                return item;
            }));
        }
        this.shadowRoot.querySelector('[data-action="pause"]').textContent = sim.paused ? '▶ Resume' : 'Ⅱ Pause';
        const pending = sim.alarms.some(alarm => !alarm.acknowledged);
        const annunciator = this.shadowRoot.querySelector('.annunciator');
        annunciator.classList.toggle('has-alarm', sim.alarms.length > 0);
        annunciator.classList.toggle('unacknowledged', pending);
        this.updateEvents();
        this.updateTrend();
    }

    updateCore() {
        const sim = this.simulation;
        for (const rod of sim.controlRods) {
            this.shadowRoot.querySelector(`[data-rod-side="${rod.id}"]`).setAttribute('transform', `translate(0 ${-15 + rod.position * 1.12})`);
            const channel = this.shadowRoot.querySelector(`[data-rod="${rod.id}"]`);
            const zone = sim.condition.zones.find(zone => zone.id === rod.id);
            channel.dataset.condition = zone.damage >= 1 ? 'damaged' : zone.temperature > 410 ? 'hot' : 'normal';
            channel.classList.toggle('selected', !this.selectedFuel && rod.id === this.selectedRod);
            channel.classList.toggle('manual-override', rod.manualOverride);
            channel.setAttribute('aria-pressed', String(!this.selectedFuel && rod.id === this.selectedRod));
            channel.setAttribute('aria-label', `${rod.id}: actual ${rod.position.toFixed(1)}%, target ${rod.target.toFixed(1)}% insertion; fuel ${zone.temperature.toFixed(1)} °C; damage ${zone.damage.toFixed(1)}%`);
            const ring = channel.querySelector('.rod-depth-ring');
            ring.style.strokeDasharray = `${2 * Math.PI * 14}`;
            ring.style.strokeDashoffset = `${2 * Math.PI * 14 * (1 - rod.position / 100)}`;
        }
        for (const element of this.shadowRoot.querySelectorAll('[data-fuel-x]')) {
            const fuel = sim.fuel.assemblies.find(a => a.id === element.dataset.fuelId);
            element.classList.toggle('selected', fuel.id === this.selectedFuel);
            element.dataset.reserve = fuel.remaining <= 0 ? 'spent' : fuel.remaining <= 20 ? 'low' : 'normal';
            element.setAttribute('aria-pressed', String(fuel.id === this.selectedFuel));
            element.setAttribute('aria-label', `${fuel.id}: remaining ${fuel.remaining.toFixed(1)}%; exposure ${fuel.exposure.toFixed(2)} demo full-power hours`);
            const ring = element.querySelector('.fuel-reserve-ring');
            ring.style.strokeDasharray = `${Math.PI * 28}`;
            ring.style.strokeDashoffset = `${Math.PI * 28 * (1 - fuel.remaining / 100)}`;
            let total = 0;
            let weight = 0;
            for (const rod of sim.controlRods) {
                const w = 1 / (1 + (rod.x - Number(element.dataset.fuelX)) ** 2 + (rod.y - Number(element.dataset.fuelY)) ** 2);
                total += (100 - rod.position) * w;
                weight += w;
            }
            element.style.setProperty('--channel-activity', Math.max(0.06, total / weight / 100 * sim.power / 100));
            const nearest = sim.controlRods.reduce((best, rod) =>
                (rod.x - Number(element.dataset.fuelX)) ** 2 + (rod.y - Number(element.dataset.fuelY)) ** 2 <
                (best.x - Number(element.dataset.fuelX)) ** 2 + (best.y - Number(element.dataset.fuelY)) ** 2 ? rod : best);
            const zone = sim.condition.zones.find(zone => zone.id === nearest.id);
            element.dataset.condition = zone.damage >= 1 ? 'damaged' : zone.temperature > 410 ? 'hot' : 'normal';
        }
        this.shadowRoot.querySelector('.rod-depth-track i').style.height = `${sim.controlRods.find(rod => rod.id === this.selectedRod).position}%`;
    }

    updateSupply() {
        const supply = this.simulation.supply;
        this.shadowRoot.querySelector('#grid-supply').checked = supply.gridAvailable;
        this.shadowRoot.querySelector('#turbine-supply').checked = supply.turbineConnected;
        this.shadowRoot.querySelector('.power-supply-button').dataset.supply = supply.factor === 0 ? 'lost' : supply.source.startsWith('BACKUP') ? 'backup' : 'normal';
        for (const item of this.shadowRoot.querySelectorAll('[data-supply]')) item.classList.toggle('energized', supply.source.includes(item.dataset.supply === 'turbine' ? 'TURBINE' : item.dataset.supply === 'grid' ? 'GRID' : 'DIESEL'));
        for (const g of supply.generators) {
            const card = this.shadowRoot.querySelector(`[data-diesel="${g.id}"]`);
            card.dataset.state = g.state.toLowerCase();
            for (const [key, value] of Object.entries({ state: g.state, fuel: `${g.fuel.toFixed(1)}%`, load: `${g.load.toFixed(2)} / ${g.capacity} MW`, startup: g.state === 'STARTING' ? `${g.startRemaining.toFixed(1)} s` : '—' })) {
                card.querySelector(`[data-diesel-value="${g.id}-${key}"]`).textContent = value;
            }
            card.querySelector('[data-diesel-meter]').style.width = `${g.fuel}%`;
            card.querySelector('[data-diesel-auto]').checked = g.automatic;
            const active = ['RUNNING', 'STARTING'].includes(g.state);
            card.querySelector('[data-generator-action="start"]').disabled = active || g.fuel <= 0;
            card.querySelector('[data-generator-action="stop"]').disabled = !active;
            card.querySelector('[data-generator-action="refuel"]').disabled = active || g.fuel >= 100;
        }
    }

    updateEvents() {
        const sim = this.simulation;
        const signature = JSON.stringify([sim.events, sim.alarms, this.logFilter]);
        if (signature === this.eventSignature) return;
        this.eventSignature = signature;
        const list = this.shadowRoot.querySelector('.event-list');
        list.replaceChildren();
        const events = sim.events.filter(event => this.logFilter === 'all' || event.category === this.logFilter);
        for (const event of events) {
            const item = document.createElement('li');
            item.className = event.severity;
            const time = document.createElement('span');
            time.className = 'event-time';
            time.textContent = this.time(event.time);
            const message = document.createElement('span');
            message.textContent = event.message;
            const category = document.createElement('span');
            category.className = 'event-category';
            category.textContent = event.category.toUpperCase();
            item.append(time, category, message);
            list.append(item);
        }
        if (!events.length) {
            const empty = document.createElement('li');
            empty.textContent = 'No events in this category yet.';
            list.append(empty);
        }
        this.shadowRoot.querySelector('.event-count').textContent = `${events.length} / ${sim.events.length} EVENTS`;
        const alarms = this.shadowRoot.querySelector('.alarm-list');
        alarms.replaceChildren();
        for (const alarm of sim.alarms) {
            const item = document.createElement('div');
            item.className = 'alarm-item';
            const message = document.createElement('span');
            message.textContent = alarm.message;
            const state = document.createElement('strong');
            state.textContent = alarm.acknowledged ? 'ACK' : 'NEW';
            item.append(message, state);
            alarms.append(item);
        }
    }

    exportLog() {
        const quote = value => `"${String(value).replaceAll('"', '""')}"`;
        const rows = [['elapsed_seconds', 'category', 'severity', 'source', 'message'],
            ...this.simulation.events.toReversed().map(event => [event.time.toFixed(2), event.category, event.severity, 'SIMULATED', event.message])];
        const url = URL.createObjectURL(new Blob([rows.map(row => row.map(quote).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' }));
        const link = document.createElement('a');
        link.href = url;
        link.download = 'nuclear-power-simulation-log.csv';
        this.shadowRoot.append(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }

    exportIncident() {
        if (!this.simulation.condition.incident) return;
        const url = URL.createObjectURL(new Blob([JSON.stringify(this.simulation.condition.incident, null, 2)], { type: 'application/json' }));
        const link = document.createElement('a');
        link.href = url;
        link.download = 'nuclear-power-incident.json';
        this.shadowRoot.append(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }

    updateTrend() {
        const sim = this.simulation;
        const points = sim.history;
        const temperatureMax = Math.max(400, Math.ceil(Math.max(sim.temperature, ...points.map(point => point.temperature)) / 200) * 200);
        this.shadowRoot.querySelector('[data-value="chartTempMax"]').textContent = temperatureMax;
        this.shadowRoot.querySelector('[data-value="chartTempMid"]').textContent = temperatureMax / 2;
        const x = time => 40 + (time - sim.elapsed + 120) / 120 * 675;
        const y = (value, max) => 250 - Math.max(0, Math.min(1, value / max)) * 210;
        const path = (key, max) => points.map((point, index) => `${index ? 'L' : 'M'}${x(point.time).toFixed(1)} ${y(point[key], max).toFixed(1)}`).join(' ');
        this.shadowRoot.querySelector('.temperature-trace').setAttribute('d', path('temperature', temperatureMax));
        const powerObjective = sim.autoObjective === 'power';
        const outputKey = powerObjective ? 'electricalOutput' : 'power';
        const outputMax = powerObjective ? 400 : 100;
        this.shadowRoot.querySelector('.power-trace').setAttribute('d', path(outputKey, outputMax));
        let targetPath = '';
        let gap = true;
        if (powerObjective) for (const point of points) {
            if (point.electricalTarget === null) { gap = true; continue; }
            targetPath += `${gap ? 'M' : 'L'}${x(point.time).toFixed(1)} ${y(point.electricalTarget, 400).toFixed(1)} `;
            gap = false;
        }
        this.shadowRoot.querySelector('.target-trace').setAttribute('d', targetPath);
        this.shadowRoot.querySelector('.target-legend').hidden = !powerObjective || !sim.autoMode;
        this.shadowRoot.querySelector('.trend-chart').setAttribute('aria-label', `Simulated core temperature in Celsius and ${powerObjective ? 'electrical output and target in megawatts' : 'reactor output in percent'} over the last two minutes`);
        const first = points[0];
        const last = points.at(-1);
        this.shadowRoot.querySelector('.trend-area').setAttribute('d', `${path('temperature', temperatureMax)} L${x(last.time).toFixed(1)} 250 L${x(first.time).toFixed(1)} 250 Z`);
        for (const [className, key, max] of [['temperature-dot', 'temperature', temperatureMax], ['power-dot', outputKey, outputMax]]) {
            const dot = this.shadowRoot.querySelector(`.${className}`);
            dot.setAttribute('cx', x(last.time));
            dot.setAttribute('cy', y(last[key], max));
        }
    }

    time(seconds) {
        return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, Math.floor(seconds) % 60].map(value => String(value).padStart(2, '0')).join(':');
    }
}

if (!customElements.get('nuclear-power-console')) customElements.define('nuclear-power-console', NuclearPowerConsole);
