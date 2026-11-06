import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NuclearPowerSimulation } from '../../main/frontend/nuclear-power-model.js';

const advance = (sim, seconds) => { for (let i = 0; i < seconds * 4; i++) sim.tick(0.25); };
const loseCooling = sim => { sim.togglePump('A'); sim.togglePump('B'); };

test('retained heat rises after a cooling-loss trip, with warning time before damage', () => {
    const sim = new NuclearPowerSimulation();
    const fuel = sim.condition.fuelTemperature;
    loseCooling(sim);
    advance(sim, 20);
    assert.equal(sim.tripped, true);
    assert.ok(sim.condition.heatReserve > 50);
    assert.ok(sim.condition.fuelTemperature > fuel);
    assert.equal(sim.condition.coreDamage, 0);
    assert.equal(sim.condition.meltdown, false);
    assert.ok(sim.events.some(event => event.message.includes('STRAINED')));
    sim.togglePump('A'); sim.togglePump('B');
    advance(sim, 120);
    assert.equal(sim.condition.coreDamage, 0);
    assert.ok(sim.condition.peakTemperature < 200);
    assert.ok(sim.temperature < 150);
    assert.equal(sim.tripRecovery().ready, true);
});

test('continued cooling loss progresses through overheating and damage before a slow meltdown', () => {
    const sim = new NuclearPowerSimulation();
    loseCooling(sim);
    let firstDamage;
    while (!sim.condition.meltdown && sim.elapsed < 360) {
        sim.tick(0.25);
        if (sim.condition.coreDamage >= 1 && !firstDamage) firstDamage = sim.elapsed;
    }
    assert.equal(sim.condition.meltdown, true);
    assert.ok(firstDamage > 20);
    assert.ok(sim.elapsed - firstDamage > 20);
    assert.equal(sim.snapshot().status, 'MELTDOWN');
    assert.equal(sim.running, false);
    assert.equal(sim.condition.stage, 'MELTDOWN');
    assert.ok(sim.events.some(event => event.message.includes('OVERHEATING')));
    assert.ok(sim.events.some(event => event.message.includes('CORE DAMAGE')));
    assert.ok(sim.condition.meltProgress < 0.05);
    advance(sim, 20);
    assert.ok(sim.condition.meltProgress > 0.7);
    sim.resetTrip(); sim.repairComponents(); sim.start();
    assert.equal(sim.running, false);
    assert.equal(sim.condition.meltdown, true);
    const initialEvent = sim.condition.incident.timeline[0];
    for (let i = 0; i < 250; i++) sim.record(`Later event ${i}`);
    assert.equal(sim.events.length, 200);
    assert.equal(sim.condition.incident.timeline.length, 160);
    assert.equal(sim.condition.incident.timeline[0], initialEvent);
    assert.match(sim.condition.incident.cause, /pump A/);
    sim.reset();
    assert.equal(sim.condition.meltdown, false);
    assert.equal(sim.condition.coreDamage, 0);
    assert.equal(sim.condition.incident, null);
});

test('fault restriction develops gradually and early intervention prevents damage', () => {
    const sim = new NuclearPowerSimulation();
    sim.setAutoMode(true);
    sim.injectFault('primary-restriction');
    assert.equal(sim.primaryFlow, 100);
    advance(sim, 10);
    assert.ok(sim.primaryFlow > 50 && sim.primaryFlow < 95);
    assert.equal(sim.condition.coreDamage, 0);
    sim.injectFault('none');
    advance(sim, 180);
    assert.equal(sim.condition.damaged, false);
    assert.equal(sim.condition.meltdown, false);
    assert.ok(sim.primaryFlow > 80);
});

test('component wear survives fault clearing and trip reset; explicit cool repair restores it', () => {
    const sim = new NuclearPowerSimulation();
    sim.injectFault('pump-wear');
    while (sim.condition.components.A.damage < 2 && sim.elapsed < 150) sim.tick(0.25);
    assert.ok(sim.condition.components.A.stress > 60);
    assert.ok(sim.condition.components.A.damage >= 2);
    sim.injectFault('none');
    const damage = sim.condition.components.A.damage;
    sim.trip(); sim.resetTrip();
    assert.ok(sim.condition.components.A.damage >= damage);
    assert.equal(sim.tripped, true);
    sim.repairComponents();
    assert.ok(sim.condition.components.A.damage >= damage);
    advance(sim, 180);
    assert.equal(sim.condition.canRepair(sim), true);
    const reserve = sim.condition.heatReserve;
    sim.repairComponents();
    assert.equal(sim.condition.damaged, false);
    assert.equal(sim.condition.heatReserve, reserve);
    sim.resetTrip();
    assert.equal(sim.running, false);
    sim.start();
    assert.equal(sim.running, true);
});

test('individual rod imbalance reveals a local hot spot before average coolant overheats', () => {
    const sim = new NuclearPowerSimulation();
    sim.setRod('CR-01', 0);
    advance(sim, 40);
    assert.ok(sim.condition.zones[0].temperature > 410);
    assert.ok(sim.temperature < 335);
    assert.equal(sim.condition.coreDamage, 0);
    assert.ok(sim.alarms.some(alarm => alarm.id === 'fuel'));
    sim.setAutoMode(true);
    advance(sim, 180);
    assert.equal(sim.condition.meltdown, false);
    assert.equal(sim.condition.coreDamage, 0);
});

test('normal automatic generation remains protected and pause freezes failure evolution', () => {
    const sim = new NuclearPowerSimulation();
    sim.setAutoObjective('power'); sim.setElectricalTarget(400); sim.setAutoMode(true);
    advance(sim, 900);
    assert.equal(sim.condition.damaged, false);
    assert.equal(sim.condition.meltdown, false);
    sim.injectFault('secondary-loss');
    advance(sim, 10);
    sim.togglePause();
    const before = JSON.stringify(sim.condition);
    advance(sim, 120);
    assert.equal(JSON.stringify(sim.condition), before);
});

test('automatic control reduces output but cannot remove retained heat without either cooling loop', () => {
    const sim = new NuclearPowerSimulation();
    sim.setAutoMode(true);
    loseCooling(sim);
    advance(sim, 3);
    assert.ok(sim.power < 64);
    assert.equal(sim.condition.coreDamage, 0);
    advance(sim, 300);
    assert.equal(sim.condition.meltdown, true);
    assert.equal(sim.autoMode, true);
    assert.equal(sim.running, false);
    assert.equal(sim.tripped, true);
});
