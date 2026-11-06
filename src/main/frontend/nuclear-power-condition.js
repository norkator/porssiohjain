/* Fictional failure/heat model for the browser demo, not reactor engineering. */
const clamp = (n, min, max) => Math.min(max, Math.max(min, n));
const approach = (n, target, dt, tau) => target + (n - target) * Math.exp(-dt / tau);

export class ReactorCondition {
    constructor(rods) {
        this.fuelTemperature = 323.3;
        this.heatReserve = 96;
        this.temperatureRate = 0;
        this.coolingMargin = 288;
        this.stage = 'NORMAL';
        this.fault = 'none';
        this.faultAge = 0;
        this.restriction = { A: 1, B: 1 };
        this.components = { A: { stress: 0, damage: 0 }, B: { stress: 0, damage: 0 } };
        this.zones = rods.map(rod => ({ id: rod.id, temperature: 323.3, exposure: 0, damage: 0 }));
        this.meltdown = false;
        this.meltProgress = 0;
        this.incident = null;
    }

    get peakTemperature() { return Math.max(...this.zones.map(zone => zone.temperature)); }
    get coreDamage() { return Math.max(...this.zones.map(zone => zone.damage)); }
    get damaged() { return this.coreDamage >= 1 || Object.values(this.components).some(component => component.damage >= 1); }

    beginIncident(sim, cause) {
        if (this.incident && !this.incident.resolvedAt) return;
        this.incident = { source: 'SIMULATED', startedAt: sim.elapsed, cause, resolvedAt: null,
            precedingEvents: sim.events.slice(0, 6).toReversed(), timeline: [] };
    }

    note(sim, event) {
        if (!this.incident || this.incident.resolvedAt) return;
        this.incident.timeline.push({ ...event, coreTemperature: sim.temperature,
            fuelTemperature: this.fuelTemperature, electricalMw: sim.power * 4.1 * sim.secondaryFlow / 100,
            primaryFlow: sim.primaryFlow, secondaryFlow: sim.secondaryFlow, stage: this.stage });
        // Retain the initiating event even after a long incident.
        if (this.incident.timeline.length > 160) this.incident.timeline.splice(1, 1);
    }

    inject(sim, fault) {
        if (!['none', 'primary-restriction', 'secondary-loss', 'pump-wear'].includes(fault)) return false;
        this.fault = fault;
        this.faultAge = 0;
        if (fault !== 'none') this.beginIncident(sim, {
            'primary-restriction': 'Progressive primary-loop obstruction',
            'secondary-loss': 'Progressive secondary cooling loss',
            'pump-wear': 'Primary pump deterioration'
        }[fault]);
        return true;
    }

    flowFactor(pump) {
        const component = this.components[pump];
        return this.restriction[pump] * (component.damage >= 100 ? 0 : 1 - component.damage * 0.0075);
    }

    prepare(dt) {
        this.faultAge += dt;
        const targets = { A: 1, B: 1 };
        if (this.fault === 'primary-restriction') targets.A = Math.max(0, 1 - this.faultAge / 35);
        if (this.fault === 'secondary-loss') targets.B = Math.max(0, 1 - this.faultAge / 35);
        if (this.fault === 'pump-wear') targets.A = Math.max(0.1, 1 - this.faultAge / 90);
        for (const pump of ['A', 'B']) this.restriction[pump] = approach(this.restriction[pump], targets[pump], dt, 2);
    }

    advance(sim, dt, previousTemperature) {
        this.heatReserve = approach(this.heatReserve, sim.power * 1.5, dt, sim.running ? 40 : 240);
        const averageDamage = this.zones.reduce((sum, zone) => sum + zone.damage, 0) / this.zones.length;
        const cooling = Math.min(sim.primaryFlow, sim.secondaryFlow) / 100 * (1 - averageDamage * 0.005);
        this.coolingMargin = 1200 * cooling - sim.power * 12 - this.heatReserve * 1.5;
        const fuelRate = (sim.power * 0.2 + this.heatReserve * 0.1
            - Math.max(0, this.fuelTemperature - sim.temperature) * 0.6 * cooling) / 3;
        this.fuelTemperature = clamp(this.fuelTemperature + clamp(fuelRate, -20, 8) * dt, 25, 1000);
        this.temperatureRate = approach(this.temperatureRate, (sim.temperature - previousTemperature) / dt * 60, dt, 4);

        for (let i = 0; i < this.zones.length; i++) {
            const zone = this.zones[i];
            const rod = sim.controlRods[i];
            const imbalance = Math.max(0, sim.rods - rod.position) * 4.2 * sim.power / 100;
            zone.temperature = approach(zone.temperature, this.fuelTemperature + imbalance, dt, 5);
            zone.exposure = zone.temperature > 410 ? zone.exposure + dt : Math.max(0, zone.exposure - dt * 2);
            if (zone.temperature > 450 && zone.exposure > 15) {
                zone.damage = clamp(zone.damage + Math.min(2, (zone.temperature - 450) / 100) * dt, 0, 100);
            }
        }
        for (const [pump, flow] of [['A', sim.primaryFlow], ['B', sim.secondaryFlow]]) {
            const component = this.components[pump];
            const requested = sim[`pump${pump}`] ? sim[`pump${pump}Speed`] * Math.cbrt(sim.supply.factor) : 0;
            const deficit = requested > 10 ? Math.max(0, 1 - flow / requested) : 0;
            component.stress = clamp(component.stress + (deficit > 0.15 ? deficit * 3 : -2) * dt, 0, 100);
            if (component.stress > 60) component.damage = clamp(component.damage + (component.stress - 60) * 0.012 * dt, 0, 100);
        }
        if (this.coreDamage >= 100 && this.peakTemperature >= 650) this.meltdown = true;
        if (this.meltdown) this.meltProgress = approach(this.meltProgress, 1, dt, 12);
        const stressed = this.coolingMargin < 0 || this.peakTemperature > 380
            || Object.values(this.components).some(component => component.stress >= 25);
        const next = this.meltdown ? 'MELTDOWN' : this.coreDamage >= 1 ? 'CORE DAMAGE' : this.damaged ? 'COMPONENT DAMAGE'
            : sim.temperature > 335 || this.peakTemperature > 410 ? 'OVERHEATING'
                : stressed ? 'STRAINED' : sim.tripped ? 'PROTECTIVE SHUTDOWN' : 'NORMAL';
        if (next !== this.stage) {
            if (next !== 'NORMAL') this.beginIncident(sim, this.coolingMargin < 0 ? 'Heat removal fell below heat load' : `Process entered ${next.toLowerCase()}`);
            this.stage = next;
            sim.record(`Condition: ${next}. Peak fuel ${this.peakTemperature.toFixed(1)} °C; cooling margin ${this.coolingMargin.toFixed(0)} MW.`,
                ['MELTDOWN', 'CORE DAMAGE', 'COMPONENT DAMAGE', 'OVERHEATING'].includes(next) ? 'alarm' : next === 'NORMAL' ? 'success' : 'warning', 'alarm');
            if (next === 'NORMAL' && this.fault === 'none' && this.incident) this.incident.resolvedAt = sim.elapsed;
        }
    }

    canRepair(sim) {
        return !sim.running && !this.meltdown && this.fault === 'none'
            && sim.temperature < 150 && this.peakTemperature < 200;
    }

    repair(sim) {
        if (!this.canRepair(sim)) return false;
        for (const component of Object.values(this.components)) { component.damage = 0; component.stress = 0; }
        for (const zone of this.zones) { zone.damage = 0; zone.exposure = 0; }
        this.stage = sim.tripped ? 'PROTECTIVE SHUTDOWN' : 'NORMAL';
        sim.record('Simulated components repaired after cooldown. Stored heat and operating settings preserved.', 'success', 'operator');
        if (this.incident) this.incident.resolvedAt = sim.elapsed;
        return true;
    }
}
