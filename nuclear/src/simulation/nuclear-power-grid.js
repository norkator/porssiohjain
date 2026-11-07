// Browser-local dispatch exercise. Requests change the existing electrical target;
// the plant regulator remains responsible for actual output and all interlocks.
const CHANGES_MW = [40, -60, 80, -40, 60, -80];

export class GridDispatcher {
    constructor() {
        this.enabled = false;
        this.remaining = 45;
        this.sequence = 0;
        this.lastRequest = null;
    }

    setEnabled(sim, enabled) {
        if (this.enabled === Boolean(enabled)) return;
        this.enabled = Boolean(enabled);
        if (this.enabled) this.remaining = 45;
        sim.record(`Simulated grid requests ${this.enabled ? 'enabled; Auto electrical control follows incoming MW targets' : 'disabled; current electrical target retained'}.`, 'info', 'automation');
    }

    readiness(sim) {
        if (!this.enabled) return { ready: false, reason: 'Not following grid requests' };
        if (sim.condition.meltdown) return { ready: false, reason: 'Meltdown / dispatch suspended' };
        if (sim.tripped) return { ready: false, reason: 'Trip latched / dispatch suspended' };
        if (!sim.autoMode) return { ready: false, reason: 'Waiting for Auto mode' };
        if (sim.autoObjective !== 'power') return { ready: false, reason: 'Select Electrical output regulation' };
        if (!sim.running) return { ready: false, reason: 'Waiting for reactor Start' };
        if (sim.paused) return { ready: false, reason: 'Simulation paused' };
        return { ready: true, reason: '' };
    }

    request(sim) {
        if (!this.readiness(sim).ready) return null;
        const previousTargetMw = sim.autoElectricalTarget;
        let change = CHANGES_MW[this.sequence % CHANGES_MW.length];
        let targetMw = Math.max(0, Math.min(400, previousTargetMw + change));
        // A request at an endpoint must still ask for an actual change.
        if (targetMw === previousTargetMw) {
            change = -change;
            targetMw = Math.max(0, Math.min(400, previousTargetMw + change));
        }
        const id = `GC-${String(++this.sequence).padStart(3, '0')}`;
        const direction = targetMw > previousTargetMw ? 'RAISE' : 'LOWER';
        const message = `${id}: ${direction === 'RAISE' ? 'Raise' : 'Lower'} electrical output from ${previousTargetMw} to ${targetMw} MW.`;
        this.lastRequest = { id, direction, previousTargetMw, targetMw, time: sim.elapsed, source: 'SIMULATED_GRID_CONTROLLER', message };
        this.remaining = 60;
        sim.setElectricalTarget(targetMw);
        sim.record(`Simulated grid controller — ${message}`, 'info', 'automation');
        return this.lastRequest;
    }

    advance(sim, seconds) {
        if (!this.readiness(sim).ready || !Number.isFinite(seconds) || seconds <= 0) return;
        this.remaining = Math.max(0, this.remaining - seconds);
        if (this.remaining === 0) this.request(sim);
    }

    status(sim) {
        const readiness = this.readiness(sim);
        if (!readiness.ready) return readiness.reason;
        if (!this.lastRequest) return 'Waiting for first grid message';
        if (sim.autoElectricalTarget !== this.lastRequest.targetMw) return 'Operator target changed';
        if (sim.regulationLimit) return `Limited: ${sim.regulationLimit}`;
        return Math.abs(sim.electricalOutput - this.lastRequest.targetMw) <= 5 ? 'Requested output reached' : 'Following grid target';
    }
}
