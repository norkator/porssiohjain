import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NuclearPowerSimulation } from '../../main/frontend/nuclear-power-model.js';

const advance = (simulation, seconds) => {
    for (let i = 0; i < seconds * 4; i++) simulation.tick(0.25);
};

test('load and rod controls move the same plant state and temperatures respond gradually', () => {
    const sim = new NuclearPowerSimulation();
    sim.setLoad(85);
    assert.equal(sim.rodTarget, 15);
    assert.equal(sim.power, 64);
    advance(sim, 30);
    assert.ok(sim.power > 83);
    assert.ok(sim.temperature > 320);
    sim.setRods(75);
    assert.equal(sim.demand, 25);
    advance(sim, 45);
    assert.ok(sim.power < 26);
    assert.ok(sim.temperature < 160);
});

test('stopping preserves circulation, cools the plant, and allows a normal restart', () => {
    const sim = new NuclearPowerSimulation();
    sim.stop();
    advance(sim, 40);
    assert.equal(sim.pumpA, true);
    assert.equal(sim.pumpB, true);
    assert.ok(sim.rods > 99);
    assert.ok(sim.temperature < 100);
    sim.start();
    advance(sim, 30);
    assert.ok(sim.power > 62);
    assert.equal(sim.snapshot().status, 'RUNNING');
});

test('pump failure can be acknowledged without hiding the active fault and primary loss trips', () => {
    const sim = new NuclearPowerSimulation();
    sim.togglePump('A');
    assert.equal(sim.alarms[0].id, 'primary');
    sim.acknowledge();
    assert.equal(sim.alarms[0].acknowledged, true);
    advance(sim, 5);
    assert.equal(sim.tripped, true);
    assert.equal(sim.running, false);
    assert.equal(sim.rodTarget, 100);
    assert.equal(sim.alarms.find(alarm => alarm.id === 'trip').acknowledged, false);
    sim.start();
    assert.equal(sim.running, false);
    sim.reset();
    assert.equal(sim.tripped, false);
    assert.equal(sim.running, true);
    assert.deepEqual(sim.alarms, []);
});

test('secondary pump changes electrical output and its alarm clears when circulation returns', () => {
    const sim = new NuclearPowerSimulation();
    const initial = sim.snapshot().signals.electricalOutput.value;
    sim.togglePump('B');
    advance(sim, 4);
    assert.ok(sim.snapshot().signals.electricalOutput.value < initial / 5);
    assert.equal(sim.alarms.some(alarm => alarm.id === 'secondary'), true);
    sim.togglePump('B');
    assert.equal(sim.alarms.some(alarm => alarm.id === 'secondary'), false);
    advance(sim, 20);
    assert.ok(sim.snapshot().signals.electricalOutput.value > initial * 0.95);
});

test('emergency shutdown latches, pause freezes evolution, and independent sessions stay isolated', () => {
    const sim = new NuclearPowerSimulation();
    const other = new NuclearPowerSimulation();
    sim.trip();
    advance(sim, 5);
    assert.ok(sim.rods > 99.9);
    assert.ok(sim.power < 6);
    sim.togglePause();
    const before = sim.snapshot();
    advance(sim, 10);
    assert.deepEqual(sim.snapshot(), before);
    assert.equal(other.tripped, false);
    assert.equal(other.elapsed, 0);
});

test('inputs are bounded and all signals remain explicitly simulated with bounded history', () => {
    const sim = new NuclearPowerSimulation();
    sim.setLoad(500);
    assert.equal(sim.demand, 100);
    sim.setRods(-500);
    assert.equal(sim.rodTarget, 0);
    sim.setLoad(NaN);
    sim.setRods(Infinity);
    sim.togglePump('invalid');
    advance(sim, 180);
    assert.ok(sim.history.length <= 121);
    assert.ok(sim.events.length <= 40);
    for (const signal of Object.values(sim.snapshot().signals)) {
        assert.equal(signal.source, 'SIMULATED');
        assert.equal(signal.quality, 'GOOD');
        assert.ok(Number.isFinite(signal.value));
    }
});
