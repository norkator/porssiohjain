import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CORE_CHANNELS, NuclearPowerSimulation } from '../src/simulation/nuclear-power-model.js';
import { FuelTransferAnimation, FUEL_HANDLING, FUEL_POOL_SLOTS } from '../src/simulation/nuclear-power-refuelling.js';

const advance = (animation, seconds) => {
    for (let step = 0; step < Math.ceil(seconds / .05); step++) animation.advance(.05);
};

test('one representative assembly is lifted clear before traversal and deposited under the pool water', () => {
    const animation = new FuelTransferAnimation(CORE_CHANNELS);
    assert.equal(animation.start('CR-01'), false);
    assert.equal(animation.start('FA-17', 49), true);
    const seen = new Set();
    let prior = animation.pose;
    for (let step = 0; step < 230; step++) {
        animation.advance(.05);
        const pose = animation.pose;
        seen.add(pose.phase);
        if (pose.carrying && prior.carrying && (pose.x !== prior.x || pose.z !== prior.z)) {
            assert.ok(pose.grabberY - FUEL_HANDLING.gripOffset - FUEL_HANDLING.assemblyHeight > FUEL_HANDLING.deckY + .1,
                'The whole assembly must clear the deck before horizontal travel');
        }
        if (pose.phase === 'Lowering into pool') {
            assert.equal(pose.x, FUEL_POOL_SLOTS[0].x);
            assert.equal(pose.z, FUEL_POOL_SLOTS[0].z);
        }
        prior = pose;
    }
    assert.ok(seen.has('Lifting assembly') && seen.has('Moving to pool') && seen.has('Lowering into pool'));
    assert.equal(animation.job, null);
    assert.deepEqual(animation.samples.filter(Boolean), [{ id: 'FA-17', count: 49, slotIndex: 0 }]);
    assert.ok(FUEL_HANDLING.storedFuelY + FUEL_HANDLING.assemblyHeight / 2 < 5.82);
    assert.equal(animation.pose.carrying, false);
    assert.equal(animation.pose.phase, 'Parked');
});

test('repeated actions replace an unfinished illustration, while completed samples use bounded display slots', () => {
    const animation = new FuelTransferAnimation(CORE_CHANNELS);
    animation.start('FA-01');
    advance(animation, 4);
    animation.start('FA-02');
    assert.equal(animation.job.id, 'FA-02');
    assert.equal(animation.samples.filter(Boolean).length, 0);
    advance(animation, 11);
    assert.equal(animation.samples.filter(Boolean).length, 1);
    for (let i = 0; i < 40; i++) animation.start('FA-03', 49, true);
    assert.equal(animation.samples.filter(Boolean).length, 35);
    assert.ok(animation.nextSlot >= 0 && animation.nextSlot < 35);
    animation.cancel();
    assert.equal(animation.samples.filter(Boolean).length, 35);
    animation.reset();
    assert.equal(animation.job, null);
    assert.equal(animation.samples.filter(Boolean).length, 0);
});

test('presentation never changes fuel, heat, damage, settings or trip state, including reduced motion and cancellation', () => {
    const sim = new NuclearPowerSimulation();
    sim.trip();
    sim.condition.heatReserve = 7;
    const state = () => JSON.stringify({ snapshot: sim.snapshot(), fuel: sim.fuel,
        heat: sim.condition.heatReserve, damage: sim.condition.zones });
    const before = state();
    const animation = new FuelTransferAnimation(CORE_CHANNELS);
    animation.start('FA-01', 49, true);
    assert.equal(animation.job, null);
    assert.equal(animation.samples.filter(Boolean).length, 1);
    animation.start('FA-02');
    advance(animation, 5);
    animation.cancel();
    assert.equal(animation.samples.filter(Boolean).length, 1);
    assert.equal(state(), before);
});

test('invalid and stalled frames cannot fast-forward or rewind a transfer', () => {
    const animation = new FuelTransferAnimation(CORE_CHANNELS);
    animation.start('FA-01');
    animation.advance(-1);
    animation.advance(NaN);
    animation.advance(Infinity);
    assert.equal(animation.job.elapsed, 0);
    animation.advance(100);
    assert.equal(animation.job.elapsed, .1);
});
