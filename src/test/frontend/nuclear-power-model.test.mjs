import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CORE_CHANNELS, NuclearPowerSimulation } from '../../main/frontend/nuclear-power-model.js';
import { advancePumpMotion } from '../../main/frontend/nuclear-power-motion.js';

// These scenarios explicitly exercise manual operation or opt into temperature Auto.
const manualSimulation = () => {
    const sim = new NuclearPowerSimulation();
    sim.setAutoMode(false);
    sim.setAutoObjective('temperature');
    return sim;
};

const advance = (simulation, seconds) => {
    for (let i = 0; i < seconds * 4; i++) simulation.tick(0.25);
};

test('load and rod controls move the same plant state and temperatures respond gradually', () => {
    const sim = manualSimulation();
    sim.setFluctuations(false);
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
    const sim = manualSimulation();
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
    const sim = manualSimulation();
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
    const sim = manualSimulation();
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
    const sim = manualSimulation();
    const other = manualSimulation();
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
    const sim = manualSimulation();
    sim.setLoad(500);
    assert.equal(sim.demand, 100);
    sim.setRods(-500);
    assert.equal(sim.rodTarget, 0);
    sim.setLoad(NaN);
    sim.setRods(Infinity);
    sim.togglePump('invalid');
    advance(sim, 180);
    assert.ok(sim.history.length <= 121);
    assert.ok(sim.events.length <= 200);
    for (const signal of Object.values(sim.snapshot().signals)) {
        assert.equal(signal.source, 'SIMULATED');
        assert.equal(signal.quality, 'GOOD');
        assert.ok(Number.isFinite(signal.value));
    }
});

test('individual rods retain independent targets, positions and requested/reported snapshots', () => {
    const sim = manualSimulation();
    sim.fluctuationsEnabled = false;
    sim.setRod('CR-01', 84);
    assert.equal(sim.controlRods.find(rod => rod.id === 'CR-01').target, 84);
    assert.ok(sim.controlRods.filter(rod => rod.id !== 'CR-01').every(rod => rod.target === 36));
    assert.equal(sim.rods, 36);
    assert.equal(sim.rodTarget, 40);
    const requested = sim.snapshot();
    assert.equal(requested.commands.rods[0].insertionPercent, 84);
    assert.equal(requested.rods[0].insertion.value, 36);
    advance(sim, 20);
    assert.ok(sim.controlRods[0].position > 83);
    assert.equal(sim.controlRods[1].position, 36);
    assert.ok(sim.power < 61);
    sim.stop();
    advance(sim, 20);
    sim.start();
    assert.equal(sim.controlRods[0].target, 84);
    assert.equal(sim.controlRods[1].target, 36);
    sim.moveRods(5);
    assert.equal(sim.controlRods[0].target, 89);
    assert.equal(sim.controlRods[1].target, 41);
    assert.equal(new Set(CORE_CHANNELS.filter(channel => channel.rodId).map(channel => channel.rodId)).size, 12);
});

test('pump speed changes coast gradually and independently, including zero and restart', () => {
    const sim = manualSimulation();
    sim.fluctuationsEnabled = false;
    sim.setPumpSpeed('A', 55);
    sim.setPumpSpeed('B', 75);
    assert.equal(sim.snapshot().commands.pumps.A.speedPercent, 55);
    assert.equal(sim.snapshot().signals.pumpASpeed.value, 100);
    advance(sim, 10);
    assert.ok(Math.abs(sim.primaryFlow - 55) < 0.5);
    assert.ok(Math.abs(sim.secondaryFlow - 75) < 0.5);
    sim.stop();
    sim.setPumpSpeed('B', 0);
    advance(sim, 15);
    assert.ok(sim.secondaryFlow < 0.1);
    assert.ok(sim.primaryFlow > 54.9);
    sim.togglePump('A');
    advance(sim, 15);
    sim.togglePump('A');
    assert.equal(sim.pumpASpeed, 55);
    advance(sim, 15);
    assert.ok(sim.primaryFlow > 54.9);
});

test('pump and flow motion is proportional to actual speed and preserves phase', () => {
    const initial = { angle: 73, offset: -4 };
    const full = advancePumpMotion(initial, 100, 0.1);
    const half = advancePumpMotion(initial, 50, 0.1);
    assert.equal(full.angle - initial.angle, (half.angle - initial.angle) * 2);
    assert.ok(Math.abs((full.offset - initial.offset) - (half.offset - initial.offset) * 2) < 1e-9);
    assert.deepEqual(advancePumpMotion(initial, 0, 0.1), initial);
    assert.equal(advancePumpMotion(full, 25, 0.1).angle, full.angle + 4.5);
});

test('fluctuations do not create flow at zero speed or generation with every rod inserted', () => {
    const sim = manualSimulation();
    sim.setRods(100);
    sim.setPumpSpeed('A', 0);
    sim.setPumpSpeed('B', 0);
    advance(sim, 120);
    assert.ok(sim.primaryFlow < 0.001);
    assert.ok(sim.secondaryFlow < 0.001);
    assert.ok(sim.power < 0.001);
});

test('trip recovery preserves tuning and history and requires ready circulation, rods and cooling', () => {
    const sim = manualSimulation();
    sim.setRod('CR-01', 75);
    sim.setPumpSpeed('B', 70);
    sim.trip();
    sim.resetTrip();
    assert.equal(sim.tripped, true);
    advance(sim, 5);
    sim.togglePump('A');
    sim.resetTrip();
    assert.equal(sim.tripped, true);
    sim.togglePump('A');
    advance(sim, 5);
    sim.temperature = 350;
    sim.resetTrip();
    assert.equal(sim.tripped, true);
    advance(sim, 30);
    assert.equal(sim.tripRecovery().ready, true);
    const elapsed = sim.elapsed;
    sim.resetTrip();
    assert.equal(sim.tripped, false);
    assert.equal(sim.running, false);
    assert.equal(sim.elapsed, elapsed);
    assert.equal(sim.pumpBSpeed, 70);
    assert.ok(sim.events.some(event => event.category === 'alarm'));
    sim.start();
    assert.equal(sim.running, true);
    assert.equal(sim.controlRods[0].target, 75);
});

test('automatic control holds changing temperature/flow targets against fluctuations and respects trips', () => {
    const sim = manualSimulation();
    sim.setRod('CR-01', 56);
    const offset = sim.controlRods[0].target - sim.controlRods[1].target;
    sim.setAutoMode(true);
    advance(sim, 180);
    assert.equal(sim.tripped, false);
    assert.ok(Math.abs(sim.temperature - 286) < 3, `temperature ${sim.temperature}`);
    assert.ok(Math.abs(sim.primaryFlow - 85) < 4);
    assert.ok(Math.abs(sim.secondaryFlow - 85) < 4);
    assert.ok(Math.abs(sim.controlRods[0].target - sim.controlRods[1].target - offset) < 1e-8);
    sim.setAutoTargets(260, 75);
    advance(sim, 180);
    assert.ok(Math.abs(sim.temperature - 260) < 3, `new temperature ${sim.temperature}`);
    assert.ok(Math.abs(sim.primaryFlow - 75) < 4);
    let maxError = 0;
    for (let i = 0; i < 120 * 4; i++) {
        sim.tick(0.25);
        maxError = Math.max(maxError, Math.abs(sim.temperature - 260));
    }
    assert.ok(maxError < 3, `maximum settled error ${maxError}`);
    assert.ok(sim.events.some(event => event.category === 'automation'));
    assert.ok(sim.events.some(event => event.category === 'process'));
    sim.trip();
    advance(sim, 15);
    assert.ok(sim.rods > 99.9);
    assert.equal(sim.running, false);
    sim.setRod('CR-01', 0);
    assert.equal(sim.controlRods[0].target, 100);
    sim.resetTrip();
    assert.equal(sim.running, false);
});

test('fluctuations remain bounded and event retention does not grow without limit', () => {
    const sim = manualSimulation();
    sim.fuel.rate = 0; // Isolate disturbance settling from the separately tested fuel drift.
    advance(sim, 30);
    assert.notEqual(sim.power, 64);
    assert.ok(Math.abs(sim.power - 64) < 2);
    sim.setFluctuations(false);
    advance(sim, 180);
    assert.ok(Math.abs(sim.power - 64) < 0.01);
    assert.ok(Math.abs(sim.temperature - 286) < 0.1);
    for (let i = 0; i < 250; i++) sim.setLoad(i % 100);
    assert.equal(sim.events.length, 200);
    const lastEvent = sim.events[0];
    sim.setLoad(NaN);
    sim.setRod('unknown', 50);
    sim.setPumpSpeed('invalid', 50);
    assert.equal(sim.events[0], lastEvent);
});

test('grid-style automatic control follows electrical MW targets and can reduce output to zero', () => {
    const sim = manualSimulation();
    sim.setAutoObjective('power');
    sim.setElectricalTarget(150);
    sim.setAutoMode(true);
    advance(sim, 240);
    assert.equal(sim.tripped, false);
    assert.ok(Math.abs(sim.snapshot().signals.electricalOutput.value - 150) < 2);
    assert.equal(sim.regulationLimit, '');
    sim.setElectricalTarget(200);
    advance(sim, 240);
    assert.ok(Math.abs(sim.snapshot().signals.electricalOutput.value - 200) < 2);
    assert.ok(sim.temperature < 290);
    assert.equal(sim.snapshot().commands.electricalTargetMw, 200);
    sim.setElectricalTarget(0);
    advance(sim, 120);
    assert.ok(sim.snapshot().signals.electricalOutput.value < 0.01);
});

test('unachievable electrical demand respects the core limit and reports the constraint', () => {
    const sim = manualSimulation();
    sim.setAutoObjective('power');
    sim.setElectricalTarget(400);
    sim.setAutoMode(true);
    advance(sim, 240);
    assert.equal(sim.tripped, false);
    assert.equal(sim.regulationLimit, 'Core temperature limit');
    assert.ok(sim.snapshot().signals.electricalOutput.value < 220);
    assert.ok(sim.temperature < 290);
    sim.setElectricalTarget(150);
    advance(sim, 240);
    assert.ok(Math.abs(sim.snapshot().signals.electricalOutput.value - 150) < 2);
    assert.equal(sim.regulationLimit, '');
    assert.ok(sim.events.some(event => event.message.includes('Core temperature limit')));
});

test('electrical targets are bounded and selecting temperature regulation remains independent', () => {
    const sim = manualSimulation();
    sim.setElectricalTarget(10000);
    assert.equal(sim.autoElectricalTarget, 400);
    sim.setElectricalTarget(-100);
    assert.equal(sim.autoElectricalTarget, 0);
    sim.setElectricalTarget(NaN);
    sim.setAutoObjective('unknown');
    assert.equal(sim.autoObjective, 'temperature');
    sim.setAutoMode(true);
    advance(sim, 180);
    assert.ok(Math.abs(sim.temperature - 286) < 3);
    assert.ok(sim.snapshot().signals.electricalOutput.value > 180);
});


test('new and reset sessions start in Auto electrical output without bypassing trip recovery', () => {
    const sim = new NuclearPowerSimulation();
    assert.equal(sim.autoMode, true);
    assert.equal(sim.autoObjective, 'power');
    assert.equal(sim.history[0].electricalTarget, 200);
    advance(sim, 180);
    assert.equal(sim.tripped, false);
    assert.equal(sim.condition.damaged, false);
    sim.trip(); advance(sim, 120); sim.resetTrip();
    assert.equal(sim.running, false);
    sim.setAutoMode(false); sim.setAutoObjective('temperature'); sim.reset();
    assert.equal(sim.autoMode, true);
    assert.equal(sim.autoObjective, 'power');
});
