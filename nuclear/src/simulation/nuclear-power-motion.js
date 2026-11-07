/* Phase-preserving SVG motion driven by reported simulation speed, not commands. */
export function advancePumpMotion(previous, speedPercent, seconds) {
    const speed = Number.isFinite(speedPercent) ? Math.min(100, Math.max(0, speedPercent)) / 100 : 0;
    const dt = Number.isFinite(seconds) ? Math.min(0.1, Math.max(0, seconds)) : 0;
    return {
        angle: (previous.angle + 180 * speed * dt) % 360,
        offset: (previous.offset - 38 * speed * dt) % 19
    };
}

// Instrument illustrations follow actual reported output/flow, never setpoints.
export function advanceInstrumentMotion(previous, signals, seconds) {
    const dt = Number.isFinite(seconds) ? Math.max(0, Math.min(.1, seconds)) : 0;
    const next = {};
    for (const key of ['thermal', 'electrical', 'flow']) {
        const activity = Number.isFinite(signals[key]) ? Math.max(0, Math.min(1, signals[key])) : 0;
        next[key] = ((previous[key] || 0) + activity * dt * .9) % 1;
    }
    return next;
}

// Illustration state only: these rates are not simulated RPM or extra plant physics.
export function advanceProcessMotion(previous, signals, seconds) {
    const bounded = value => Number.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    const dt = Number.isFinite(seconds) ? Math.max(0, Math.min(.1, seconds)) : 0;
    const steam = bounded((signals.secondaryTemperature - 65) / 140) * bounded(signals.secondaryFlow / 100);
    const heat = bounded((signals.temperature - signals.secondaryTemperature) / 140)
        * bounded(Math.min(signals.primaryFlow, signals.secondaryFlow) / 100);
    // Output is authoritative for rotation; the toy model can generate during
    // warm-up before the separate illustrative steam-temperature threshold.
    const target = Number.isFinite(signals.shaftSpeed) ? bounded(signals.shaftSpeed / 100) : bounded(signals.electricalOutput / 300);
    const speed = target + (previous.speed - target) * Math.exp(-dt / (target > previous.speed ? 3 : 6));
    return {
        speed: speed < .0001 ? 0 : speed,
        angle: (previous.angle + speed * 540 * dt) % 360,
        steamPhase: (previous.steamPhase + steam * dt * .35) % 1,
        heatPhase: (previous.heatPhase + heat * dt * .6) % 1,
        supplyPhase: (previous.supplyPhase + bounded(signals.supplyFactor) * dt * .6) % 1,
        thermalPhase: ((previous.thermalPhase || 0) + bounded((signals.fuelTemperature - 100) / 550) * dt * .4) % 1,
        steam, heat
    };
}
