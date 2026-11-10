import * as THREE from 'three';
import { PlantOverviewScene } from './nuclear-power-scene.js';

const TAU = Math.PI * 2;

// Reuses the plant camera, picking, resource disposal and resize behavior. Engine
// motion is illustrative and comes exclusively from reported generator state.
export class AuxiliaryPowerScene extends PlantOverviewScene {
    build() {
        this.frameHeight = 6.3;
        this.frameWidth = 13.5;
        this.defaultZoom = this.zoom = 1.25;
        this.target.set(0, 1.8, .1);
        this.engines = new Map();
        this.sourceLamps = new Map();
        this.steel = this.material('#93acb9');
        this.steelLight = this.material('#cfdee0');
        this.darkSteel = this.material('#344e5b');
        this.base = this.material('#253e4b');
        this.trim = this.material('#6a8b96');
        this.teal = this.material('#5cb6a1');
        this.rubber = this.material('#192b33');
        this.brass = this.material('#d9a353');
        this.jointGeometry = new THREE.IcosahedronGeometry(1, 1);
        this.scene.add(new THREE.HemisphereLight('#d7edf6', '#354841', 2.4));
        const sun = new THREE.DirectionalLight('#ffebcf', 3);
        sun.position.set(-8, 14, 14);
        this.scene.add(sun);
        const rim = new THREE.DirectionalLight('#9cdddf', 1.5);
        rim.position.set(10, 10, -8);
        this.scene.add(rim);
        this.floorMaterial = this.material('#1a2b37');
        this.box(this.scene, [23, .3, 12], this.floorMaterial, [0, -.25, 0]);
        this.grid = new THREE.GridHelper(23, 23, '#54717e', '#334d5c');
        this.grid.scale.z = .52;
        this.grid.position.y = -.08;
        this.scene.add(this.grid);
        this.geometries.add(this.grid.geometry);
        this.materials.add(this.grid.material);

        for (const [id, x] of [['A', -5.4], ['B', 5.4]]) this.buildEngine(id, x);
        const bus = this.component('bus', [0, 0, -3.7]);
        this.pad(bus, 3, 1.5);
        this.box(bus, [2.6, 2.4, 1], this.steel, [0, 1.5, 0]);
        this.box(bus, [2.2, 1.8, .06], this.darkSteel, [0, 1.6, .55]);
        this.busLamp = this.material('#455e59', { emissive: '#71f4bc', emissiveIntensity: 0 });
        for (let i = 0; i < 3; i++) this.box(bus, [.42, .2, .08], this.busLamp, [-.7 + i * .7, 2.1, .62]);
        this.box(bus, [.15, .6, .1], this.steelLight, [.8, 1.3, .65]);
        for (const [id, x] of [['grid', -8], ['turbine', 8]]) {
            const source = this.component(id, [x, 0, -4]);
            this.box(source, [1.8, 1.8, 1], this.darkSteel, [0, 1, 0]);
            const lamp = this.material('#4b6867', { emissive: '#85efc1', emissiveIntensity: 0 });
            this.box(source, [1.3, .18, .1], lamp, [0, 1.6, .55]);
            this.sourceLamps.set(id, lamp);
            this.line(this.scene, [[x, .1, -3.5], [x, .1, -2.8], [0, .1, -2.8], [0, .1, -3.2]], this.trim, .045);
        }
    }

    buildEngine(id, x) {
        const group = this.component(`diesel${id}`, [x, 0, .5]);
        this.pad(group, 9, 5.7);
        for (const mount of [-2.4, 2.4]) this.box(group, [1, .4, 2], this.rubber, [mount, .35, 0]);
        this.box(group, [5.4, .5, 1.9], this.teal, [-.7, .75, 0]);
        this.box(group, [4.2, 1.5, .35], this.teal, [-.8, 1.7, -.7]);
        this.box(group, [4.4, .35, 1.8], this.teal, [-.8, 2.85, 0]);
        const pistons = [];
        const crank = new THREE.Group();
        crank.position.set(-.7, 1.12, .45);
        group.add(crank);
        this.dynamicGroups.push(crank);
        this.cylinder(crank, .12, 5.8, this.steelLight, [.5, 0, 0], .12, 8).rotation.z = Math.PI / 2;
        for (let i = 0; i < 4; i++) {
            const px = -2.3 + i * 1.05;
            const piston = new THREE.Group();
            piston.position.set(px, 2, .45);
            group.add(piston);
            this.dynamicGroups.push(piston);
            this.cylinder(piston, .34, .38, this.steelLight, [0, 0, 0], .34, 8);
            this.cylinder(piston, .35, .05, this.darkSteel, [0, .12, 0], .35, 8);
            this.box(piston, [.1, .8, .12], this.brass, [0, -.55, 0]);
            pistons.push(piston);
            this.cylinder(crank, .24, .18, this.brass, [px + .7, 0, .2], .24, 8).rotation.z = Math.PI / 2;
            this.box(group, [.12, .9, 1.35], this.steel, [px + .48, 2.1, -.03]);
        }
        this.line(group, [[-2.4, 3.05, -.4], [-2.4, 3.5, -.4], [.8, 3.5, -.4], [.8, 3.05, -.4]], this.darkSteel, .12);
        this.cylinder(group, .45, .4, this.rubber, [-.9, 3.8, -.4], .45, 8);
        this.line(group, [[.8, 2.8, -.6], [1.2, 3.1, -.6], [1.2, 4.5, -.6], [1.8, 4.5, -.6]], this.darkSteel, .16);
        this.box(group, [1.1, 2.6, 2], this.steel, [-3.7, 1.8, -.1]);
        for (let fin = 0; fin < 7; fin++) this.box(group, [.8, .08, .08], this.darkSteel, [-3.7, .85 + fin * .3, 1]);
        const fan = new THREE.Group();
        fan.position.set(-3.7, 1.8, 1.12);
        group.add(fan);
        this.dynamicGroups.push(fan);
        this.mesh(group, new THREE.TorusGeometry(.62, .07, 4, 12), this.darkSteel, [-3.7, 1.8, 1.1]);
        this.cylinder(fan, .13, .15, this.teal, [0, 0, 0]).rotation.x = Math.PI / 2;
        for (let i = 0; i < 5; i++) {
            const angle = i / 5 * TAU;
            const blade = this.box(fan, [.19, .45, .08], this.teal, [Math.sin(angle) * .3, Math.cos(angle) * .3, 0]);
            blade.rotation.z = -angle + .35;
        }
        const alternator = this.cylinder(group, .85, 2.3, this.teal, [2.65, 1.5, 0]);
        alternator.rotation.z = Math.PI / 2;
        for (const ax of [1.6, 3.7]) this.cylinder(group, .9, .12, this.steel, [ax, 1.5, 0]).rotation.z = Math.PI / 2;
        const rotor = new THREE.Group();
        rotor.position.set(3.82, 1.5, 0);
        group.add(rotor);
        this.dynamicGroups.push(rotor);
        for (let i = 0; i < 6; i++) {
            const angle = i / 6 * TAU;
            const blade = this.box(rotor, [.08, .55, .16], this.steelLight, [0, Math.cos(angle) * .42, Math.sin(angle) * .42]);
            blade.rotation.x = angle;
        }
        const terminalMaterial = this.material('#4a6d61', { emissive: '#78ffc3', emissiveIntensity: 0 });
        this.box(group, [.7, .45, .8], this.darkSteel, [2.6, 2.5, 0]);
        this.box(group, [.45, .1, .4], terminalMaterial, [2.6, 2.78, .1]);
        const fill = this.box(group, [3.3, 1.1, .85], this.brass, [-.8, .9, 2]);
        this.dynamicMeshes.push(fill);
        this.box(group, [3.7, .15, 1.15], this.darkSteel, [-.8, .3, 2]);
        this.box(group, [3.7, .15, 1.15], this.steel, [-.8, 1.55, 2]);
        for (const side of [-2.6, 1]) this.box(group, [.1, 1.1, 1], this.steel, [side, .9, 2]);
        this.line(group, [[1, 1.3, 2], [1.3, 1.3, 2], [1.3, 1.3, .3]], this.brass, .055);
        const wireMaterial = this.material('#577a70', { emissive: '#77eec0', emissiveIntensity: 0 });
        const vectors = this.line(this.scene, [[x + 2.6, .22, .5], [x + 2.6, .22, -1.9], [0, .22, -1.9], [0, .22, -3.2]], wireMaterial, .055);
        const path = new THREE.CurvePath();
        for (let i = 1; i < vectors.length; i++) path.add(new THREE.LineCurve3(vectors[i - 1], vectors[i]));
        const markers = Array.from({ length: 7 }, () => this.mesh(this.scene, new THREE.OctahedronGeometry(.1), terminalMaterial));
        this.dynamicMeshes.push(...markers);
        this.engines.set(id, { crank, pistons, fan, rotor, fill, terminalMaterial, wireMaterial, path, markers });
    }

    buildLabels() {
        this.renderer.domElement.setAttribute('aria-label', 'Backup diesel generators in 3D. Drag or use arrow keys to orbit; scroll or use plus and minus to zoom. Select a generator to focus its controls.');
        const layer = document.createElement('div');
        layer.className = 'plant-scene-labels';
        for (const [id, name, anchor] of [
            ['dieselA', 'Diesel A', [-5.4, 4.7, .5]], ['dieselB', 'Diesel B', [5.4, 4.7, .5]],
            ['grid', 'External grid', [-8, 2.6, -4]], ['turbine', 'Turbine feed', [8, 2.6, -4]], ['bus', 'Auxiliary AC bus', [0, 3.2, -3.7]],
        ]) {
            const button = document.createElement('button');
            button.className = 'plant-scene-label';
            button.dataset.auxiliaryEquipment = id;
            button.setAttribute('aria-label', `Inspect ${name.toLowerCase()}`);
            const title = document.createElement('strong');
            title.textContent = name;
            const reading = document.createElement('small');
            button.append(title, reading);
            button.addEventListener('click', () => this.inspect(id), { signal: this.abort.signal });
            layer.append(button);
            this.labels.set(id, { button, reading, anchor: new THREE.Vector3(...anchor) });
        }
        const hint = document.createElement('span');
        hint.className = 'plant-scene-hint';
        hint.textContent = 'Drag: orbit · wheel-drag: pan · scroll: zoom';
        this.host.append(layer, hint);
    }

    update(supply, phases, busPhase, theme) {
        if (this.disposed) return;
        if (theme !== this.theme) {
            this.theme = theme;
            this.floorMaterial.color.set(theme === 'light' ? '#d2e0e3' : '#1a2b37');
            this.grid.material.color.set(theme === 'light' ? '#9cb5bd' : '#405a69');
        }
        for (const generator of supply.generators) {
            const engine = this.engines.get(generator.id);
            const phase = phases[generator.id] / 360;
            engine.crank.rotation.x = engine.rotor.rotation.x = phase * TAU;
            engine.fan.rotation.z = phase === 0 ? 0 : -phase * TAU;
            for (const [index, piston] of engine.pistons.entries()) piston.position.y = 2 + Math.sin((phase + index / 4) * TAU) * .26;
            engine.fill.scale.y = Math.max(.001, generator.fuel / 100);
            engine.fill.position.y = .35 + .55 * generator.fuel / 100;
            const loaded = generator.state === 'RUNNING' && generator.load > 0;
            engine.terminalMaterial.emissiveIntensity = loaded ? .9 : 0;
            engine.wireMaterial.emissiveIntensity = loaded ? .7 : 0;
            for (const [index, marker] of engine.markers.entries()) {
                marker.visible = loaded;
                marker.position.copy(engine.path.getPoint((busPhase + index / engine.markers.length) % 1));
            }
            const label = this.labels.get(`diesel${generator.id}`);
            const state = generator.state === 'RUNNING' ? loaded ? 'SUPPLYING' : 'RUNNING / UNLOADED' : generator.state;
            label.reading.textContent = `${state} · ${generator.fuel.toFixed(0)}% fuel`;
            label.button.classList.toggle('selected', loaded);
        }
        this.busLamp.emissiveIntensity = supply.factor > 0 ? .7 : 0;
        for (const id of ['grid', 'turbine']) {
            const energized = supply.source.includes(id === 'grid' ? 'GRID' : 'TURBINE');
            this.sourceLamps.get(id).emissiveIntensity = energized ? .9 : 0;
            const label = this.labels.get(id);
            label.reading.textContent = energized ? 'SUPPLYING' : (id === 'grid' ? supply.gridAvailable : supply.turbineConnected) ? 'CONNECTED' : 'ISOLATED';
            label.button.classList.toggle('selected', energized);
        }
        this.labels.get('bus').reading.textContent = supply.factor > 0 ? `${supply.demand.toFixed(2)} MW requested` : 'NO AC SUPPLY';
        this.render();
    }
}
