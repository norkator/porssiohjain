import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PlantAudio, plantSoundState } from '../src/simulation/nuclear-power-audio.js';
import { NuclearPowerSimulation } from '../src/simulation/nuclear-power-model.js';

class Param {
    value = 0;
    events = [];
    setValueAtTime(value, time) { this.value = value; this.events.push(['set', value, time]); }
    linearRampToValueAtTime(value, time) { this.value = value; this.events.push(['ramp', value, time]); }
    cancelScheduledValues(time) { this.events.push(['cancel', time]); }
    cancelAndHoldAtTime(time) { this.events.push(['hold', time]); }
    setTargetAtTime(value, time, constant) { this.value = value; this.events.push(['target', value, time, constant]); }
}

class Node {
    frequency = new Param();
    gain = new Param();
    connections = [];
    stops = [];
    connect(target) { this.connections.push(target); }
    disconnect() { this.disconnected = true; this.connections = []; }
    start(time) { this.started = true; this.startTime = time; }
    stop(time) { this.stops.push(time); }
}

class Context {
    state = 'running';
    currentTime = 10;
    destination = {};
    created = [];
    createOscillator() { const node = new Node(); this.created.push(node); return node; }
    createBufferSource() { const node = new Node(); this.created.push(node); return node; }
    async decodeAudioData(data) { return { duration: 0.32, data }; }
    createGain() { const node = new Node(); this.created.push(node); return node; }
}

test('start and stop have distinct bounded envelopes and onended disconnects both nodes', () => {
    const context = new Context();
    const audio = new PlantAudio(context);
    for (const command of ['start', 'stop']) {
        audio.cue(command);
        assert.equal(audio.hasActiveSounds, true);
        const [voice] = audio.nodes;
        const pitch = voice.oscillator.frequency.events;
        assert.equal(Math.sign(pitch[1][1] - pitch[0][1]), command === 'start' ? 1 : -1);
        assert.deepEqual(voice.gain.gain.events, [['set', 0, 10], ['ramp', 0.0004, 10.04], ['ramp', 0, 10.55]]);
        assert.deepEqual(voice.oscillator.stops, [10.6]);
        voice.oscillator.onended();
        assert.ok(voice.oscillator.disconnected && voice.gain.disconnected);
        assert.equal(audio.hasActiveSounds, false);
    }
});

test('suspended/invalid commands are discarded without replay', () => {
    const context = new Context();
    const audio = new PlantAudio(context);
    context.state = 'suspended'; audio.cue('start');
    assert.equal(context.created.length, 0);
    context.state = 'running'; audio.cue('blocked');
    assert.equal(context.created.length, 0);
    assert.equal(audio.hasActiveSounds, false);
});

test('repeated command cues do not stack and mute stops all voices', () => {
    const context = new Context();
    const audio = new PlantAudio(context);
    assert.equal(audio.hasActiveSounds, false);
    audio.cue('start'); audio.cue('stop');
    assert.equal(audio.nodes.size, 1);
    audio.stop(); audio.stop();
    assert.equal(audio.nodes.size, 0);
    for (const node of context.created) {
        assert.equal(node.disconnected, true);
        if (node.started) assert.ok(node.stops.includes(undefined));
    }
});

const loadPlant = audio => audio.preloadPlant(async () => ({ok:true,arrayBuffer:async()=>new ArrayBuffer(4)}));

test('plant audio follows actual operation and coast-down, with faults independent of acknowledgement', () => {
    const sim = new NuclearPowerSimulation();
    assert.deepEqual(plantSoundState(sim), {activity:0, fault:false});
    sim.autoElectricalTarget = 400;
    assert.equal(plantSoundState(sim).activity, 0);
    sim.running = true; sim.power = 64;
    assert.equal(plantSoundState(sim).activity, .64);
    sim.components.items.turbine.actual.shaftSpeed = 80;
    sim.running = false;
    assert.equal(plantSoundState(sim).activity, .8);
    sim.condition.stage = 'STRAINED';
    sim.alarms = [{acknowledged:true}];
    assert.equal(plantSoundState(sim).fault, true);
    sim.condition.stage = 'NORMAL';
    sim.components.items.condenser.disturbance.value = .2;
    assert.equal(plantSoundState(sim).fault, true);
    sim.components.items.condenser.disturbance.value = 0;
    sim.components.items.turbine.actual.shaftSpeed = 0;
    assert.deepEqual(plantSoundState(sim), {activity:0, fault:false});
});

test('two gapless loops crossfade without stacking and command/UI cues preserve them', async () => {
    const context = new Context(), audio = new PlantAudio(context);
    await loadPlant(audio);
    audio.updatePlant({activity:0, fault:false});
    assert.equal(audio.nodes.size, 0);
    audio.updatePlant({activity:1, fault:false});
    const voices = [...audio.nodes];
    assert.equal(voices.length, 2);
    assert.ok(voices.every(v => v.oscillator.loop && v.oscillator.startTime === 10));
    const normal = voices.find(v => v.plant === 'normal'), fault = voices.find(v => v.plant === 'fault');
    assert.equal(normal.target, .32); assert.equal(fault.target, 0);
    audio.updatePlant({activity:1, fault:true});
    assert.equal(audio.nodes.size, 2);
    assert.equal(normal.target, 0); assert.equal(fault.target, .32);
    assert.deepEqual(fault.gain.gain.events.at(-1), ['target', .32, 10, .25]);
    audio.updatePlant({activity:100, fault:true});
    assert.equal(fault.target, .32);
    audio.cue('stop');
    await audio.preloadUi(async()=>({ok:true,arrayBuffer:async()=>new ArrayBuffer(4)}));
    audio.ui('on');
    assert.ok(voices.every(v => audio.nodes.has(v) && !v.oscillator.disconnected));
    audio.updatePlant({activity:.25, fault:false});
    assert.equal(normal.target, .16); assert.equal(fault.target, 0);
    audio.stop(); audio.stop();
    assert.equal(audio.nodes.size, 0);
    assert.ok(voices.every(v => v.oscillator.disconnected && v.gain.disconnected));
});

test('silence and suspension release loops; resuming creates only one fresh pair', async () => {
    const context = new Context(), audio = new PlantAudio(context);
    await loadPlant(audio);
    for (const state of ['suspended', 'running', 'closed']) {
        context.state = state;
        audio.updatePlant({activity:.5, fault:false});
        assert.equal(audio.nodes.size, state === 'running' ? 2 : 0);
    }
    context.state = 'running';
    audio.updatePlant({activity:.5, fault:false});
    const voices = [...audio.nodes];
    audio.updatePlant({activity:0, fault:true});
    assert.equal(audio.nodes.size, 0);
    assert.ok(voices.every(v => v.oscillator.disconnected && v.gain.disconnected));
});

test('loading completes without playing late audio and failures never substitute a healthy sound', async () => {
    const context = new Context(), audio = new PlantAudio(context);
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const urls = [];
    const loading = audio.preloadPlant(async url => {
        urls.push(url); await gate;
        return {ok:true,arrayBuffer:async()=>new ArrayBuffer(4)};
    });
    audio.updatePlant({activity:1, fault:false});
    audio.stop(); release(); await loading;
    await loadPlant(audio);
    assert.equal(urls.length, 2);
    assert.equal(audio.nodes.size, 0);
    assert.equal(context.created.length, 0);
    const failed = new PlantAudio(context);
    await failed.preloadPlant(async url => ({ok:url.includes('normal'),arrayBuffer:async()=>new ArrayBuffer(4)}));
    assert.equal(failed.plantState, 'unavailable');
    failed.updatePlant({activity:1, fault:true});
    assert.equal(context.created.length, 0);
});


test('Kenney samples preload once, play quietly without looping, and replace earlier UI voices', async () => {
    const context=new Context(), audio=new PlantAudio(context);
    const urls=[];
    const fetcher=async url=>{urls.push(url);return {ok:true,arrayBuffer:async()=>new ArrayBuffer(4)};};
    assert.equal(audio.ui('on'),false); // Loading never queues a click for later.
    await audio.preloadUi(fetcher); await audio.preloadUi(fetcher);
    assert.equal(urls.length,3); assert.equal(audio.uiBuffers.size,3);
    assert.ok(urls.every(url=>url.startsWith('/audio/nuclear-power/kenney-ui/')));
    assert.equal(audio.ui('on'),true);
    const [first]=audio.nodes;
    assert.equal(first.oscillator.loop,false);
    assert.equal(first.gain.gain.value,0.26);
    assert.equal(audio.ui('off'),false); // Fast repeated input cannot stack clicks.
    context.currentTime+=.1;
    assert.equal(audio.ui('off'),true);
    assert.equal(audio.nodes.size,1); assert.equal(first.oscillator.disconnected,true);
    audio.stop(); assert.equal(audio.nodes.size,0);
});

test('missing sample assets fail silently and never synthesize a replacement or replay', async () => {
    const context=new Context(), audio=new PlantAudio(context);
    await audio.preloadUi(async()=>{throw new Error('unavailable');});
    assert.equal(audio.ui('adjust'),false);
    assert.equal(context.created.length,0);
});

test('finished samples disconnect and suspended contexts never play a UI click', async () => {
    const context=new Context(), audio=new PlantAudio(context);
    await audio.preloadUi(async()=>({ok:true,arrayBuffer:async()=>new ArrayBuffer(4)}));
    context.state='suspended'; assert.equal(audio.ui('adjust'),false);
    context.state='running'; assert.equal(audio.ui('adjust'),true);
    const [voice]=audio.nodes;voice.oscillator.onended();
    assert.equal(audio.nodes.size,0);
    assert.equal(voice.gain.disconnected,true);
    assert.equal(voice.oscillator.disconnected,true);
});
