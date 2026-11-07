import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PlantAudio } from '../src/simulation/nuclear-power-audio.js';

class Param {
    value = 0;
    events = [];
    setValueAtTime(value, time) { this.value = value; this.events.push(['set', value, time]); }
    linearRampToValueAtTime(value, time) { this.value = value; this.events.push(['ramp', value, time]); }
    cancelScheduledValues(time) { this.events.push(['cancel', time]); }
    cancelAndHoldAtTime(time) { this.events.push(['hold', time]); }
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

test('repeated cues and mute stop all voices without any continuous running sound', () => {
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
    assert.equal(first.gain.gain.value,0.13);
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
