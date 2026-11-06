/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
const approach = (value, target, seconds, response) => target + (value - target) * Math.exp(-seconds / response);

/** Illustrative, deterministic animation model; not a physical reactor model.
 * Signals are deliberately separate from rendering so a later read-only measurement
 * adapter can supply explicitly sourced values without changing the schematic.
 */
export class NuclearPowerSimulation {
    constructor() {
        this.reset();
    }

    reset() {
        this.elapsed = 0;
        this.running = true;
        this.tripped = false;
        this.paused = false;
        this.demand = 64;
        this.rodTarget = 36;
        this.rods = 36;
        this.power = 64;
        this.temperature = 286;
        this.returnTemperature = 258;
        this.secondaryTemperature = 184;
        this.pumpA = true;
        this.pumpB = true;
        this.primaryFlow = 100;
        this.secondaryFlow = 100;
        this.history = [];
        this.events = [];
        this.alarms = [];
        this.record('Simulation initialized. Unit 01 at steady load.', 'info');
        this.sample();
    }

    record(message, severity = 'info') {
        this.events.unshift({ time: this.elapsed, message, severity });
        this.events.length = Math.min(this.events.length, 40);
    }

    start() {
        if (this.tripped || this.running) return;
        this.running = true;
        this.rodTarget = 100 - this.demand;
        this.record('Startup initiated. Rods moving to load setpoint.', 'success');
        this.updateAlarms();
    }

    stop() {
        if (!this.running) return;
        this.running = false;
        this.rodTarget = 100;
        this.record('Normal shutdown. Coolant circulation maintained.');
        this.updateAlarms();
    }

    setLoad(value) {
        if (this.tripped || !Number.isFinite(value)) return;
        this.demand = clamp(Math.round(value), 0, 100);
        if (this.running) this.rodTarget = 100 - this.demand;
        this.record(`Load setpoint changed to ${this.demand}%.`);
    }

    setRods(value) {
        if (this.tripped || !this.running || !Number.isFinite(value)) return;
        this.rodTarget = clamp(Math.round(value), 0, 100);
        this.demand = 100 - this.rodTarget;
        this.record(`Rod insertion commanded to ${this.rodTarget}%.`);
    }

    togglePump(pump) {
        if (pump !== 'A' && pump !== 'B') return;
        const key = `pump${pump}`;
        this[key] = !this[key];
        this.record(`Pump ${pump} ${this[key] ? 'started' : 'stopped'}.`, this[key] ? 'success' : 'warning');
        this.updateAlarms();
    }

    trip(reason = 'Operator emergency shutdown') {
        if (this.tripped) return;
        this.tripped = true;
        this.running = false;
        this.rodTarget = 100;
        this.record(`${reason}. Rods inserting; reset demo to restart.`, 'alarm');
        this.updateAlarms();
    }

    togglePause() {
        this.paused = !this.paused;
        this.record(this.paused ? 'Simulation paused.' : 'Simulation resumed.');
    }

    acknowledge() {
        const pending = this.alarms.filter(alarm => !alarm.acknowledged);
        if (!pending.length) return;
        pending.forEach(alarm => { alarm.acknowledged = true; });
        this.record('Alarms acknowledged. Active conditions remain listed.');
    }

    updateAlarms() {
        const conditions = [
            ['trip', this.tripped, 'Emergency shutdown latched', 'alarm'],
            ['primary', this.running && !this.pumpA, 'Primary circulation unavailable', 'warning'],
            ['secondary', this.running && !this.pumpB, 'Secondary circulation unavailable', 'warning'],
            ['temperature', this.temperature > 335, 'Core temperature high', 'alarm']
        ];
        const next = [];
        for (const [id, active, message, severity] of conditions) {
            const previous = this.alarms.find(alarm => alarm.id === id);
            if (active) {
                next.push(previous || { id, message, severity, acknowledged: false });
                if (!previous) this.record(message, severity);
            } else if (previous) {
                this.record(`Condition cleared: ${previous.message}.`, 'success');
            }
        }
        this.alarms = next;
    }

    tick(seconds = 0.25) {
        if (this.paused || !Number.isFinite(seconds) || seconds <= 0) return;
        // Do not fast-forward a retained window after browser suspension.
        const dt = Math.min(seconds, 1);
        this.elapsed += dt;
        this.rods = approach(this.rods, this.rodTarget, dt, this.tripped ? 0.5 : 2.5);
        this.power = approach(this.power, this.running ? 100 - this.rods : 0, dt, this.tripped ? 2 : 5);
        this.primaryFlow = approach(this.primaryFlow, this.pumpA ? 100 : 0, dt, 2);
        this.secondaryFlow = approach(this.secondaryFlow, this.pumpB ? 100 : 0, dt, 2);
        const coolingLoss = (1 - this.primaryFlow / 100) * 1.2 + (1 - this.secondaryFlow / 100) * 0.6;
        const coreTarget = 30 + this.power * (4 + coolingLoss) + Math.sin(this.elapsed / 11) * 0.4;
        this.temperature = approach(this.temperature, coreTarget, dt, 18);
        this.returnTemperature = approach(this.returnTemperature,
            this.temperature - 28 * this.primaryFlow / 100 * this.power / 64, dt, 7);
        this.secondaryTemperature = approach(this.secondaryTemperature,
            25 + (this.temperature - 25) * 0.61 * this.primaryFlow / 100, dt, 12);
        if (this.running && this.power > 10 && this.primaryFlow < 15) {
            this.trip('Simulated protection: primary flow lost');
        } else if (this.running && this.temperature > 360) {
            this.trip('Simulated protection: high core temperature');
        }
        this.updateAlarms();
        if (this.elapsed - this.history.at(-1).time >= 1) this.sample();
    }

    sample() {
        this.history.push({ time: this.elapsed, temperature: this.temperature, power: this.power });
        this.history = this.history.filter(point => point.time >= this.elapsed - 120);
    }

    snapshot() {
        const signal = (value, unit) => ({ value, unit, source: 'SIMULATED', quality: 'GOOD', time: this.elapsed });
        return {
            elapsed: this.elapsed,
            status: this.tripped ? 'TRIPPED' : !this.running ? 'COOLING DOWN' : this.power < 2 ? 'STANDBY' : 'RUNNING',
            signals: {
                coreTemperature: signal(this.temperature, '°C'),
                returnTemperature: signal(this.returnTemperature, '°C'),
                secondaryTemperature: signal(this.secondaryTemperature, '°C'),
                thermalOutput: signal(this.power * 12, 'MW'),
                electricalOutput: signal(this.power * 4.1 * this.secondaryFlow / 100, 'MW'),
                primaryFlow: signal(this.primaryFlow * 48, 'm³/h'),
                rodInsertion: signal(this.rods, '%'),
                load: signal(this.power, '%')
            }
        };
    }
}
