import * as THREE from 'three';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
import { loopTemperatures, temperatureColor } from './nuclear-power-temperature.js';
import { flowMarkerDistance, valveHandleAngle } from './nuclear-power-motion.js';
import { CORE_CHANNELS } from './nuclear-power-model.js';
import { buildFuelHandlingHall, updateFuelHandlingHall, CHANNEL_PITCH, ROD_INSERTED_Y, ROD_TRAVEL } from './nuclear-power-refuelling-scene.js';
import { buildElectricalScene, updateElectricalScene, ELECTRICAL_LABELS } from './nuclear-power-electrical-scene.js';

const TAU = Math.PI * 2;
const DEFAULT_ZOOM = 1.2;
const MAX_PAN_DISTANCE = 8;
const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
const equipment = [
    ['reactor', 'R-01', 'Reactor vessel', [-10.1, 5.3, 1.1]],
    ['exchanger', 'HX-01', 'Heat exchanger', [-1.3, 6.5, -1.5]],
    ['turbine', 'TG-01', 'Turbine / generator', [7.2, 4.8, -1.5]],
    ['condenser', 'C-01', 'Condenser', [7, .3, 3.1]],
    ['pumpA', 'P-01', 'Primary pump', [-4.8, .3, 4.7]],
    ['pumpB', 'P-02', 'Secondary pump', [2.2, .3, 5.2]],
];

// A presentation adapter only. All commands, interlocks and plant state stay in
// NuclearPowerSimulation; the console owns the single visibility-aware frame loop.
export class PlantOverviewScene {
    constructor(host, { inspect, unavailable }) {
        this.host = host;
        this.inspect = inspect;
        this.disposed = false;
        this.geometries = new Set();
        this.materials = new Set();
        this.groups = new Map();
        this.labels = new Map();
        this.featureLabels = [];
        this.paths = [];
        this.fuel = [];
        this.rods = [];
        this.valves = [];
        this.pumps = {};
        this.dynamicGroups = [];
        this.dynamicMeshes = [];
        this.instancedMeshes = [];
        this.abort = new AbortController();
        this.yaw = .24;
        this.pitch = .7;
        this.zoom = DEFAULT_ZOOM;
        this.scene = new THREE.Scene();
        this.camera = new THREE.OrthographicCamera(-14, 14, 9, -9, .1, 120);
        this.target = new THREE.Vector3(0, 2.6, .5);
        this.raycaster = new THREE.Raycaster();
        this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' });
        this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
        this.renderer.setClearColor(0, 0);
        this.renderer.outputColorSpace = THREE.SRGBColorSpace;
        const canvas = this.renderer.domElement;
        canvas.className = 'plant-scene-canvas';
        canvas.tabIndex = 0;
        canvas.setAttribute('role', 'group');
        canvas.setAttribute('aria-label', 'Interactive 3D plant. Drag or use arrow keys to rotate. Hold the mouse wheel and drag to pan; Shift+drag or Shift+arrow keys also pan. Scroll or use plus and minus to zoom; Home resets the view. Equipment buttons open component consoles.');
        canvas.title = 'Drag to orbit · hold mouse wheel and drag to pan · Shift+drag or Shift+arrows to pan · scroll to zoom · Home to reset';
        host.replaceChildren(canvas);
        try {
            this.build();
            this.defaultTarget = this.target.clone();
            this.batchStaticMeshes();
            this.buildLabels();
            this.bindCamera();
            canvas.addEventListener('webglcontextlost', event => {
                event.preventDefault();
                if (!this.disposed) unavailable();
            }, { signal: this.abort.signal });
            this.resizeObserver = new ResizeObserver(() => this.resize());
            this.resizeObserver.observe(host);
            this.resize();
        } catch (error) {
            this.dispose();
            throw error;
        }
    }

    material(color, options = {}) {
        const material = new THREE.MeshStandardMaterial({ color, roughness: .78, metalness: .15, flatShading: true, ...options });
        this.materials.add(material);
        return material;
    }

    mesh(parent, geometry, material, position = [0, 0, 0]) {
        this.geometries.add(geometry);
        const mesh = new THREE.Mesh(geometry, material);
        mesh.position.set(...position);
        parent.add(mesh);
        return mesh;
    }

    box(parent, size, material, position) {
        return this.mesh(parent, new THREE.BoxGeometry(...size), material, position);
    }

    cylinder(parent, radius, height, material, position, top = radius, segments = 12) {
        return this.mesh(parent, new THREE.CylinderGeometry(top, radius, height, segments), material, position);
    }

    line(parent, points, material, radius = .13) {
        const vectors = points.map(point => new THREE.Vector3(...point));
        for (let i = 1; i < vectors.length; i++) {
            const delta = vectors[i].clone().sub(vectors[i - 1]);
            const tube = this.cylinder(parent, radius, delta.length(), material, vectors[i].clone().add(vectors[i - 1]).multiplyScalar(.5).toArray(), radius, 8);
            tube.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), delta.normalize());
        }
        for (const point of vectors.slice(1, -1)) this.mesh(parent, this.jointGeometry, material, point.toArray()).scale.setScalar(radius);
        return vectors;
    }

    flow(points, circuit, kind, material, temperatureKey) {
        const vectors = this.line(this.scene, points, material);
        const curve = new THREE.CurvePath();
        for (let i = 1; i < vectors.length; i++) curve.add(new THREE.LineCurve3(vectors[i - 1], vectors[i]));
        const markerMaterial = this.material('#b5ffec', { emissive: '#58b9ad', emissiveIntensity: .35, transparent: true });
        const length = curve.getLength();
        const dots = Array.from({ length: Math.max(2, Math.round(length / 1.2)) }, () => this.mesh(this.scene, this.dotGeometry, markerMaterial));
        this.paths.push({ curve, length, dots, circuit, kind, material, markerMaterial, temperatureKey });
    }

    component(id, position) {
        const group = new THREE.Group();
        group.position.set(...position);
        group.userData.equipment = id;
        this.scene.add(group);
        this.groups.set(id, group);
        return group;
    }

    pad(parent, width, depth) {
        this.box(parent, [width, .18, depth], this.base, [0, .05, 0]);
        for (const x of [-1, 1]) this.box(parent, [.12, .05, depth * .7], this.trim, [x * (width / 2 - .12), .17, 0]);
    }

    valve(id, key, point, pipeDirection = [1, 0, 0]) {
        const group = new THREE.Group();
        group.position.set(...point);
        const axis = new THREE.Vector3(...pipeDirection).normalize();
        group.quaternion.setFromUnitVectors(new THREE.Vector3(1, 0, 0), axis);
        this.groups.get(id).add(group);
        this.cylinder(group, .24, .34, this.steel, [0, 0, 0]);
        this.cylinder(group, .045, .5, this.steelLight, [0, .35, 0]);
        const handle = this.box(group, [.64, .07, .1], this.teal, [0, .61, 0]);
        this.valves.push({ handle, group, pipeDirection: axis, id, key });
    }

    build() {
        this.frameHeight = 12.2;
        this.target.set(0, 4.15, -1.4);
        this.steel = this.material('#708d9e');
        this.steelLight = this.material('#b6cbd4');
        this.darkSteel = this.material('#344c5c');
        this.base = this.material('#243947');
        this.trim = this.material('#627f8c');
        this.teal = this.material('#64c9b5');
        this.hotPipe = this.material('#d99155');
        this.coolPipe = this.material('#4c9eac');
        this.hotMarker = this.material('#fff0b3', { emissive: '#eab66d', emissiveIntensity: .4 });
        this.coolMarker = this.material('#b5ffec', { emissive: '#58b9ad', emissiveIntensity: .35 });
        this.jointGeometry = new THREE.IcosahedronGeometry(1, 1);
        this.dotGeometry = new THREE.OctahedronGeometry(.18);
        this.scene.add(new THREE.HemisphereLight('#d4edf4', '#304450', 2.4));
        const sun = new THREE.DirectionalLight('#ffe4c3', 3.2);
        sun.position.set(-8, 16, 12);
        this.scene.add(sun);
        const rim = new THREE.DirectionalLight('#70bfcf', 1.5);
        rim.position.set(10, 8, -8);
        this.scene.add(rim);
        this.floorMaterial = this.material('#1a2b37');
        this.box(this.scene, [24, .35, 16.5], this.floorMaterial, [0, -.35, -1.25]);
        const grid = new THREE.GridHelper(24, 24, '#54717e', '#334d5c');
        grid.scale.z = 16.5 / 24;
        grid.position.set(0, -.16, -1.25);
        this.grid = grid;
        this.scene.add(grid);
        this.geometries.add(grid.geometry);
        for (const material of [grid.material].flat()) this.materials.add(material);

        const reactor = this.component('reactor', [-7.4, 0, 0]);
        this.pad(reactor, 4.6, 3.8);
        for (const x of [-1.25, 1.25]) this.box(reactor, [.3, 1.4, 1.8], this.darkSteel, [x, .8, 0]);
        this.cylinder(reactor, 1.65, .5, this.steel, [0, 1.4, 0]);
        // Open front shell keeps fuel and rods legible without transparency sorting.
        this.mesh(reactor, new THREE.CylinderGeometry(1.65, 1.65, 4.2, 12, 1, true, Math.PI / 2, Math.PI),
            this.material('#7d9aaa', { side: THREE.DoubleSide }), [0, 3.7, 0]);
        for (const y of [1.7, 5.8]) this.mesh(reactor, new THREE.TorusGeometry(1.65, .13, 4, 12), this.steelLight, [0, y, 0]).rotation.x = Math.PI / 2;
        this.cylinder(reactor, 1.72, .32, this.steelLight, [0, 6, 0]);
        const rodChannels = CORE_CHANNELS.filter(channel => channel.rodId)
            .sort((a, b) => a.rodId.localeCompare(b.rodId));
        for (const channel of CORE_CHANNELS.filter(channel => channel.fuelId)) {
            const x = channel.x * CHANNEL_PITCH;
            const z = channel.y * CHANNEL_PITCH;
            const material = this.material('#997c58', { emissive: '#000000' });
            const fuel = this.box(reactor, [.2, 2.45, .2], material, [x, 3, z]);
            fuel.userData.fuelId = channel.fuelId;
            fuel.userData.zoneIndex = rodChannels.reduce((nearest, rod, index) => {
                const distance = candidate => (candidate.x - channel.x) ** 2 + (candidate.y - channel.y) ** 2;
                return distance(rod) < distance(rodChannels[nearest]) ? index : nearest;
            }, 0);
            this.fuel.push(fuel);
        }
        const absorberMaterial = this.material('#7bbebc', { emissive: '#266d71', emissiveIntensity: .12, metalness: .45 });
        for (const channel of rodChannels) {
            const rod = this.cylinder(reactor, .075, 2.25, absorberMaterial,
                [channel.x * CHANNEL_PITCH, ROD_INSERTED_Y, channel.y * CHANNEL_PITCH], .075, 6);
            rod.userData.rodId = channel.rodId;
            this.rods.push(rod);
        }
        buildFuelHandlingHall(this, reactor);
        this.reactionMaterial = this.material('#ffe6a1', {
            emissive: '#ffc14e', emissiveIntensity: .9, transparent: true,
            opacity: 0, depthWrite: false, metalness: 0,
        });
        const reactionGeometry = new THREE.OctahedronGeometry(.065);
        this.reactorReactions = Array.from({ length: 24 }, () => {
            const particle = this.mesh(reactor, reactionGeometry, this.reactionMaterial);
            particle.visible = false;
            particle.raycast = () => {};
            this.dynamicMeshes.push(particle);
            return particle;
        });
        this.pool = this.cylinder(reactor, 1.25, .12, this.material('#ff602d', { emissive: '#ff3b16', emissiveIntensity: 1 }), [0, 1.76, 0]);
        this.pool.visible = false;
        this.drips = Array.from({ length: 5 }, (_, i) => this.mesh(reactor, this.dotGeometry, this.pool.material, [(i - 2) * .42, 2, .5]));

        const exchanger = this.component('exchanger', [-1.3, 0, -1.5]);
        this.pad(exchanger, 3, 3.2);
        for (const x of [-.8, .8]) this.box(exchanger, [.24, 1.2, 1.2], this.darkSteel, [x, .65, 0]);
        this.mesh(exchanger, new THREE.CylinderGeometry(1, 1, 3.8, 10, 1, true, Math.PI / 2, Math.PI),
            this.material('#7a9caa', { side: THREE.DoubleSide }), [0, 3.3, 0]);
        for (const y of [1.4, 5.2]) this.cylinder(exchanger, 1.07, .22, this.steelLight, [0, y, 0]);
        this.cylinder(exchanger, .94, .6, this.steel, [0, 5.6, 0], .45);
        for (let i = 0; i < 5; i++) {
            const x = -.65 + i * .32;
            this.line(exchanger, [[x, 1.6, .3], [x, 4.65, .3], [x, 4.8, -.3], [x, 1.6, -.3]], this.hotPipe, .065);
        }
        this.exchangerGlow = this.material('#f4b166', { emissive: '#e7a256', emissiveIntensity: .2 });
        for (const y of [2.3, 3.4, 4.5]) this.box(exchanger, [1.5, .07, .12], this.exchangerGlow, [0, y, .5]);
        // Steam stays inside the secondary-side cutaway, then turns into its
        // outlet. It shares the existing steam phase, not a separate animation.
        const steamGeometry = new THREE.IcosahedronGeometry(1, 1);
        this.exchangerSteam = Array.from({ length: 15 }, () => {
            const material = this.material('#def6f2', {
                transparent: true, opacity: 0, depthWrite: false, metalness: 0,
                emissive: '#b8e1db', emissiveIntensity: .25,
            });
            const puff = this.mesh(exchanger, steamGeometry, material);
            puff.visible = false;
            puff.renderOrder = 1;
            puff.raycast = () => {}; // Selecting steam still picks the equipment behind it.
            this.dynamicMeshes.push(puff);
            return puff;
        });
        this.valve('exchanger', 'secondaryValve', [1.25, 4.3, 0], [0, -1, 0]);
        this.valve('exchanger', 'bypass', [-1.4, 2.5, .6], [0, 1, 0]);
        this.bypassMaterial = this.material('#698c8c');
        this.flow([[-2.3, 4.7, -1.5], [-3, 4.7, -1.5], [-3, 1.3, -1.5], [-2.3, 1.3, -1.5]], 'A', 'water', this.bypassMaterial, 'primaryBypass');

        for (const [id, x, z] of [['pumpA', -4.8, 3.7], ['pumpB', 2.2, 4.4]]) {
            const pump = this.component(id, [x, 0, z]);
            this.pad(pump, 2.25, 2);
            this.box(pump, [1.5, .65, 1.2], this.darkSteel, [0, .5, 0]);
            this.cylinder(pump, .66, .9, this.steel, [0, 1.2, -.1]).rotation.x = Math.PI / 2;
            this.mesh(pump, new THREE.TorusGeometry(.66, .09, 4, 12), this.steelLight, [0, 1.2, .4]);
            const rotor = new THREE.Group();
            rotor.position.set(0, 1.2, .48);
            pump.add(rotor);
            this.cylinder(rotor, .14, .16, this.teal, [0, 0, 0]).rotation.x = Math.PI / 2;
            for (let i = 0; i < 4; i++) {
                const blade = this.box(rotor, [.23, .43, .08], this.teal, [Math.sin(i / 4 * TAU) * .29, Math.cos(i / 4 * TAU) * .29, 0]);
                blade.rotation.z = -i / 4 * TAU + .4;
            }
            this.pumps[id] = rotor;
            this.valve(id, 'dischargeValve', [1, 1.1, 0]);
        }

        const turbine = this.component('turbine', [6, 0, -1.5]);
        this.pad(turbine, 6, 3);
        for (const x of [-1, 1, 3]) this.box(turbine, [.38, 1.9, 1.3], this.darkSteel, [x, 1.1, 0]);
        const shell = this.mesh(turbine, new THREE.CylinderGeometry(.65, 1.1, 3.1, 12, 1, true, Math.PI, Math.PI),
            this.material('#799baa', { side: THREE.DoubleSide }), [0, 2.8, 0]);
        shell.rotation.z = Math.PI / 2;
        this.turbineRotor = new THREE.Group();
        this.turbineRotor.position.set(0, 2.8, 0);
        turbine.add(this.turbineRotor);
        this.cylinder(this.turbineRotor, .1, 6.3, this.steelLight, [1, 0, 0]).rotation.z = Math.PI / 2;
        for (let i = 0; i < 5; i++) {
            const x = -1.15 + i * .56;
            for (let blade = 0; blade < 8; blade++) {
                const angle = blade / 8 * TAU;
                const radius = .43 + i * .07;
                const mesh = this.box(this.turbineRotor, [.09, radius * .75, .16], this.steelLight, [x, Math.cos(angle) * radius * .65, Math.sin(angle) * radius * .65]);
                mesh.rotation.x = angle;
            }
        }
        const generator = this.cylinder(turbine, .84, 1.8, this.teal, [3.15, 2.8, 0]);
        generator.rotation.z = Math.PI / 2;
        for (const x of [2.4, 3.9]) this.cylinder(turbine, .88, .12, this.steelLight, [x, 2.8, 0]).rotation.z = Math.PI / 2;
        this.generatorLamp = this.box(turbine, [.65, .12, .5], this.material('#4d7d6d', { emissive: '#57e3a6', emissiveIntensity: 0 }), [3.15, 3.68, 0]);
        this.valve('turbine', 'admission', [-1.7, 3.4, 0]);

        const condenser = this.component('condenser', [7, 0, 3.1]);
        this.pad(condenser, 4.2, 2.6);
        this.box(condenser, [3.7, 1.45, 1.8], this.steel, [0, 1.05, 0]);
        this.box(condenser, [3.2, 1.1, .05], this.darkSteel, [0, 1.08, .93]);
        for (let i = 0; i < 6; i++) this.box(condenser, [.1, 1, .1], this.coolPipe, [-1.3 + i * .52, 1.08, .98]);
        this.drops = Array.from({ length: 7 }, (_, i) => this.mesh(condenser, this.dotGeometry, this.coolMarker, [-1.3 + i * .43, 1.4, 1.1]));
        this.valve('condenser', 'coolingValve', [1.8, 1.3, 0]);

        this.flow([[-5.8, 4.7, 0], [-4, 4.7, 0], [-4, 4.7, -1.5], [-2.3, 4.7, -1.5]], 'A', 'hot', this.hotPipe, 'primaryHot');
        this.flow([[-2.3, 1.3, -1.5], [-3.3, 1.3, -1.5], [-3.3, 1.2, 3.7], [-4.8, 1.2, 3.7], [-7.4, 1.2, 3.7], [-7.4, 1.8, 1]], 'A', 'water', this.material('#4c9eac'), 'primaryReturn');
        this.flow([[-.3, 4.7, -1.5], [4.3, 4.7, -1.5], [4.3, 3.3, -1.5], [4.6, 3.3, -1.5]], 'B', 'steam', this.material('#4c9eac'), 'secondaryHot');
        this.flow([[6, 2.8, -.6], [6, 2.8, 2.3], [6, 1.7, 2.3]], 'B', 'steam', this.material('#4c9eac'), 'secondaryExhaust');
        this.flow([[7, .9, 4], [7, .9, 4.4], [2.2, 1.2, 4.4], [.7, 1.2, 4.4], [.7, 1.3, -1.5], [-.3, 1.3, -1.5]], 'B', 'water', this.material('#4c9eac'), 'secondaryReturn');
        this.markerHighlight = new THREE.Color('#fff6e6');
        buildElectricalScene(this);

    }

    buildLabels() {
        const layer = document.createElement('div');
        layer.className = 'plant-scene-labels';
        for (const [id, tag, name, position] of [...equipment, ...ELECTRICAL_LABELS]) {
            const button = document.createElement('button');
            button.className = 'plant-scene-label';
            button.dataset.sceneEquipment = id;
            button.setAttribute('aria-label', `Open ${name.toLowerCase()} controls`);
            button.setAttribute('aria-haspopup', 'dialog');
            button.setAttribute('aria-controls', id === 'reactor' ? 'core-map-window' : id.startsWith('diesel') ? `${id}-window` : ['grid', 'bus'].includes(id) ? 'electrical-supply-window' : `equipment-${id}-window`);
            button.setAttribute('aria-expanded', 'false');
            const title = document.createElement('strong');
            title.textContent = tag;
            const text = document.createElement('span');
            text.textContent = name;
            const reading = document.createElement('small');
            button.append(title, text, reading);
            button.addEventListener('click', () => this.inspect(id), { signal: this.abort.signal });
            layer.append(button);
            this.labels.set(id, { button, reading, anchor: new THREE.Vector3(...position) });
        }
        for (const [id, title, detail, position] of [
            ['spent-fuel-pool', 'SF-01 / Spent-fuel pool', 'Empty storage racks', [-10.6, 6.4, -4.75]],
            ['fuel-crane', 'FH-01 / Fuel-handling crane', 'Parked', [-7.4, 11.9, -4.75]],
        ]) {
            const label = document.createElement(id === 'spent-fuel-pool' ? 'button' : 'span');
            label.className = 'plant-scene-label plant-scene-feature';
            label.dataset.sceneFeature = id;
            if (id === 'spent-fuel-pool') {
                label.dataset.sceneEquipment = 'spentFuel';
                label.setAttribute('aria-label', 'Open spent-fuel pool and dispatch controls');
                label.setAttribute('aria-haspopup', 'dialog');
                label.setAttribute('aria-controls', 'spent-fuel-window');
                label.setAttribute('aria-expanded', 'false');
                label.addEventListener('click', () => this.inspect('spentFuel'), { signal: this.abort.signal });
            }
            const name = document.createElement('strong');
            name.textContent = title;
            const description = document.createElement('small');
            description.textContent = detail;
            label.append(name, description);
            layer.append(label);
            this.featureLabels.push({ id, button: label, reading: description, anchor: new THREE.Vector3(...position) });
        }
        this.renderer.domElement.setAttribute('aria-description', 'The tiled reactor deck stays closed during regulation. Control rods and aqua collars move inside the open-front guide chamber; small sliders on green caps indicate actual rod insertion. Behind the reactor are a spent-fuel pool and a fuel-handling crane. Each manual replacement can illustrate one assembly moving into the pool.');
        const caption = document.createElement('span');
        caption.className = 'plant-scene-caption';
        caption.textContent = 'UNIT 01 / CUTAWAY';
        const hint = document.createElement('span');
        hint.className = 'plant-scene-hint';
        hint.textContent = 'Drag: orbit · wheel-drag: pan · scroll: zoom';
        this.host.append(layer, caption, hint);
    }

    batchStaticMeshes() {
        // Keep picking by component while batching stationary surfaces. Rotor
        // blades are batched in their own moving coordinate system as well.
        const moving = new Set([...this.fuel, ...this.rods, this.pool, ...(this.drips || []),
            ...(this.drops || []), ...this.paths.flatMap(path => path.dots),
            ...this.valves.map(valve => valve.handle), ...Object.values(this.pumps), this.turbineRotor,
            ...this.dynamicGroups, ...this.dynamicMeshes]);
        this.scene.updateMatrixWorld(true);
        for (const root of [this.scene, ...this.groups.values(), ...Object.values(this.pumps), this.turbineRotor, ...this.dynamicGroups].filter(Boolean)) {
            const batches = new Map();
            const inverse = root.matrixWorld.clone().invert();
            const visit = object => {
                if (object !== root && (moving.has(object) || object.userData.equipment)) return;
                if (object.isMesh) {
                    if (!batches.has(object.material)) batches.set(object.material, []);
                    batches.get(object.material).push(object);
                }
                for (const child of object.children) visit(child);
            };
            visit(root);
            for (const [material, meshes] of batches) {
                if (meshes.length < 2) continue;
                const parts = meshes.map(mesh => {
                    const geometry = mesh.geometry.index ? mesh.geometry.toNonIndexed() : mesh.geometry.clone();
                    return geometry.applyMatrix4(inverse.clone().multiply(mesh.matrixWorld));
                });
                const geometry = mergeGeometries(parts);
                for (const part of parts) part.dispose();
                if (!geometry) continue;
                this.mesh(root, geometry, material);
                for (const mesh of meshes) mesh.removeFromParent();
            }
        }
    }

    bindCamera() {
        const canvas = this.renderer.domElement;
        const options = { signal: this.abort.signal };
        canvas.addEventListener('wheel', event => {
            event.preventDefault();
            const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? this.height : 1);
            this.setZoom(this.zoom * Math.exp(-clamp(delta, -300, 300) * .0015));
        }, { ...options, passive: false });
        canvas.addEventListener('pointerdown', event => {
            if (![0, 1].includes(event.button) || this.drag) return;
            const mode = event.button === 1 || event.shiftKey ? 'pan' : 'orbit';
            if (mode === 'pan') {
                event.preventDefault(); // Suppress middle-button browser autoscroll.
                canvas.focus({ preventScroll: true });
            }
            this.drag = { id: event.pointerId, button: event.button, mode, target: this.target.clone(),
                x: event.clientX, y: event.clientY, yaw: this.yaw, pitch: this.pitch, moved: false };
            canvas.dataset.dragging = mode;
            canvas.setPointerCapture(event.pointerId);
        }, options);
        canvas.addEventListener('pointermove', event => {
            if (!this.drag || this.drag.id !== event.pointerId) return;
            const dx = event.clientX - this.drag.x;
            const dy = event.clientY - this.drag.y;
            this.drag.moved ||= Math.hypot(dx, dy) > 5;
            if (!this.drag.moved) return;
            if (this.drag.mode === 'pan') {
                this.pan(dx, dy, this.drag.target);
                return;
            }
            this.yaw = clamp(this.drag.yaw - dx * .004, -.48, .65);
            this.pitch = clamp(this.drag.pitch + dy * .003, .4, .95);
            this.render();
        }, options);
        canvas.addEventListener('pointerup', event => {
            if (!this.drag || this.drag.id !== event.pointerId || this.drag.button !== event.button) return;
            const { moved, mode } = this.drag;
            this.drag = null;
            delete canvas.dataset.dragging;
            if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
            if (moved || mode === 'pan') return;
            const rect = canvas.getBoundingClientRect();
            this.raycaster.setFromCamera(new THREE.Vector2((event.clientX - rect.left) / rect.width * 2 - 1, -(event.clientY - rect.top) / rect.height * 2 + 1), this.camera);
            for (const hit of this.raycaster.intersectObjects([...this.groups.values()], true)) {
                let object = hit.object;
                while (object && !object.userData.equipment) object = object.parent;
                if (object) { this.inspect(object.userData.equipment); break; }
            }
        }, options);
        for (const type of ['pointercancel', 'lostpointercapture']) canvas.addEventListener(type, () => {
            this.drag = null;
            delete canvas.dataset.dragging;
        }, options);
        canvas.addEventListener('keydown', event => {
            const directions = { ArrowLeft: [-.08, 0], ArrowRight: [.08, 0], ArrowUp: [0, .06], ArrowDown: [0, -.06] };
            if (directions[event.key]) {
                if (event.shiftKey) {
                    const [dx, dy] = { ArrowLeft: [36, 0], ArrowRight: [-36, 0], ArrowUp: [0, 36], ArrowDown: [0, -36] }[event.key];
                    this.pan(dx, dy);
                } else {
                    this.yaw = clamp(this.yaw + directions[event.key][0], -.48, .65);
                    this.pitch = clamp(this.pitch + directions[event.key][1], .4, .95);
                    this.render();
                }
            } else if (['+', '=', '-', '_'].includes(event.key)) this.setZoom(this.zoom + (['+', '='].includes(event.key) ? .15 : -.15));
            else if (event.key === 'Home') this.resetCamera();
            else return;
            event.preventDefault();
        }, options);
    }

    resize() {
        if (this.disposed || !this.host.clientWidth || !this.host.clientHeight) return;
        const width = this.host.clientWidth, height = this.host.clientHeight;
        if (width !== this.width || height !== this.height) {
            this.width = width;
            this.height = height;
            this.renderer.setSize(width, height, false);
        }
        this.labelSignature = null;
        this.render();
    }

    setZoom(zoom) {
        this.zoom = clamp(zoom, .65, 3);
        this.render();
    }

    pan(dx, dy, origin = this.target) {
        if (!this.height) return;
        // Camera-plane motion keeps dragging aligned with the screen at every
        // orbit angle. Orthographic scale makes it follow the pointer at any zoom.
        const unitsPerPixel = (this.camera.top - this.camera.bottom) / this.height;
        const offset = new THREE.Vector3().copy(origin).sub(this.defaultTarget)
            .addScaledVector(new THREE.Vector3().setFromMatrixColumn(this.camera.matrixWorld, 0), -dx * unitsPerPixel)
            .addScaledVector(new THREE.Vector3().setFromMatrixColumn(this.camera.matrixWorld, 1), dy * unitsPerPixel)
            .clampLength(0, MAX_PAN_DISTANCE);
        this.target.copy(this.defaultTarget).add(offset);
        this.render();
    }

    resetCamera() {
        this.yaw = .24;
        this.pitch = .7;
        this.zoom = this.defaultZoom ?? DEFAULT_ZOOM;
        this.target.copy(this.defaultTarget);
        this.render();
    }

    update(sim, motion, pumps, selected, theme, fuelTransfer, dieselMotion) {
        if (this.disposed) return;
        if (theme !== this.theme) {
            this.theme = theme;
            this.floorMaterial.color.set(theme === 'light' ? '#d2e0e3' : '#1a2b37');
            this.grid.material.color.set(theme === 'light' ? '#9cb5bd' : '#405a69');
        }
        for (const [index, rod] of this.rods.entries()) rod.position.y = ROD_INSERTED_Y + (1 - sim.controlRods[index].position / 100) * ROD_TRAVEL;
        for (const { rodIndex, x, marker } of this.refuelling.rodIndicators) {
            marker.position.x = x + (1 - sim.controlRods[rodIndex].position / 50) * .085;
        }
        for (const [index, fuel] of this.fuel.entries()) {
            const zone = sim.condition.zones[fuel.userData.zoneIndex];
            const heat = clamp((zone.temperature - 100) / 550, 0, 1);
            fuel.material.color.setHSL(.1 - heat * .09, .28 + heat * .6, .36 + heat * .14);
            fuel.material.emissive.setHSL(.05 - heat * .04, 1, .5);
            const channel = motion.reactionChannels?.[fuel.userData.zoneIndex];
            const reaction = sim.running && !sim.tripped && !sim.condition.meltdown ? channel?.activity || 0 : 0;
            const pulse = .5 + .5 * Math.sin(((channel?.phase ?? motion.reactionPhase) + index / this.fuel.length) * TAU);
            fuel.material.emissiveIntensity = heat * (.6 + .1 * Math.sin(motion.thermalPhase * TAU)) + reaction * (.25 + pulse * .55);
            fuel.scale.y = 1 - sim.condition.meltProgress * .55;
            fuel.position.y = 1.78 + 1.225 * fuel.scale.y;
        }
        const reactionActivity = sim.running && !sim.tripped && !sim.condition.meltdown ? motion.reactionActivity || 0 : 0;
        this.reactionMaterial.opacity = reactionActivity * .9;
        for (const [index, particle] of this.reactorReactions.entries()) {
            const channel = motion.reactionChannels?.[this.fuel[index % this.fuel.length].userData.zoneIndex];
            const phase = ((channel?.phase ?? motion.reactionPhase) + index / this.reactorReactions.length) % 1;
            const travel = phase < .5 ? phase * 2 : (1 - phase) * 2;
            const source = this.fuel[index % this.fuel.length];
            const target = this.fuel[(index * 7 + 5) % this.fuel.length];
            particle.position.set(
                source.position.x + (target.position.x - source.position.x) * travel,
                2.25 + index % 5 * .4 + Math.sin(phase * TAU) * .08,
                .84 + Math.sin(index * 2.4) * .08,
            );
            particle.scale.setScalar(.7 + (channel?.activity || 0) * .7);
            particle.visible = reactionActivity > .005 && (channel?.activity || 0) > .005;
        }
        this.pool.visible = sim.condition.meltProgress > 0;
        this.pool.scale.setScalar(.2 + sim.condition.meltProgress * .8);
        for (const [index, drip] of this.drips.entries()) {
            drip.visible = sim.condition.meltProgress > 0;
            drip.position.y = 3.6 - ((motion.thermalPhase + index / 5) % 1) * 1.8;
            drip.scale.set(.5, 1, .5);
        }
        for (const [id, pump] of Object.entries(this.pumps)) pump.rotation.z = pumps[id === 'pumpA' ? 'A' : 'B'].angle * Math.PI / 180;
        this.turbineRotor.rotation.x = motion.angle * Math.PI / 180;
        this.generatorLamp.material.emissiveIntensity = sim.electricalOutput > .1 ? .7 : 0;
        this.exchangerGlow.emissiveIntensity = motion.heat * (.4 + .4 * Math.sin(motion.heatPhase * TAU) ** 2);
        for (const [index, puff] of this.exchangerSteam.entries()) {
            const phase = (motion.steamPhase + index / this.exchangerSteam.length) % 1;
            const turn = clamp((phase - .72) / .28, 0, 1);
            const lane = index % 3 - 1;
            puff.position.set(
                (lane * .48 + Math.sin(phase * TAU + index) * .06) * (1 - turn) + turn * 1.05,
                2.1 + Math.min(1, phase / .8) * 2.6,
                .64 * (1 - turn),
            );
            const size = .1 + Math.sin(phase * Math.PI) * .14;
            puff.scale.set(size * 1.2, size * 1.45, size);
            puff.rotation.y = phase * TAU;
            puff.material.opacity = motion.steam * .48 * Math.sin(phase * Math.PI);
            puff.material.color.set(theme === 'light' ? '#82b5b3' : '#def6f2');
            puff.visible = puff.material.opacity > .012;
        }
        const temperatures = loopTemperatures(sim);
        this.coolPipe.color.set(temperatureColor(temperatures.condenserCooling));
        this.coolMarker.color.set(temperatureColor(temperatures.secondaryReturn)).lerp(this.markerHighlight, .5);
        for (const { handle, id, key } of this.valves) handle.rotation.y = valveHandleAngle(sim.components.items[id].actual[key] ?? 100) * Math.PI / 180;
        for (const path of this.paths) {
            path.material.color.set(temperatureColor(temperatures[path.temperatureKey]));
            path.markerMaterial.color.copy(path.material.color).lerp(this.markerHighlight, .5);
            path.markerMaterial.emissive.copy(path.material.color);
            const flow = path.circuit === 'A' ? sim.primaryFlow : sim.secondaryFlow;
            const bypass = path.temperatureKey === 'primaryBypass' ? sim.components.items.exchanger.actual.bypass / 100 : 1;
            path.markerMaterial.opacity = bypass;
            const travel = path.circuit === 'A' ? motion.primaryTravel : motion.secondaryTravel;
            for (const [index, dot] of path.dots.entries()) {
                dot.visible = flow > .1 && bypass > .001 && (path.kind !== 'steam' || motion.steam > .02);
                dot.position.copy(path.curve.getPoint(flowMarkerDistance(travel, index, path.dots.length, path.length) / path.length));
            }
        }
        for (const [index, drop] of this.drops.entries()) {
            drop.visible = motion.steam > .02;
            drop.position.y = 1.6 - ((motion.steamPhase * 2 + index / 7) % 1) * 1.1;
            drop.scale.set(.4, .65, .4);
        }
        const readings = {
            reactor: `In ${sim.returnTemperature.toFixed(0)} °C · Out ${sim.temperature.toFixed(0)} °C`,
            exchanger: `In ${sim.secondaryReturnTemperature.toFixed(0)} °C · Out ${sim.secondaryTemperature.toFixed(0)} °C`,
            turbine: `${sim.electricalOutput.toFixed(0)} MW electric`,
            condenser: `Return ${sim.secondaryReturnTemperature.toFixed(0)} °C`,
            pumpA: `${sim.returnTemperature.toFixed(0)} °C · ${sim.components.items.pumpA.actual.speed.toFixed(0)}% speed`,
            pumpB: `${sim.secondaryReturnTemperature.toFixed(0)} °C · ${sim.components.items.pumpB.actual.speed.toFixed(0)}% speed`,
        };
        for (const [id, label] of this.labels) {
            if (readings[id] !== undefined && label.reading.textContent !== readings[id]) label.reading.textContent = readings[id];
            label.button.classList.toggle('selected', id === selected);
            label.button.classList.toggle('hot', id === 'reactor' && sim.temperature > 335);
        }
        this.labels.get('reactor').button.setAttribute('aria-label', `Open reactor core map; simulated inlet ${sim.returnTemperature.toFixed(1)} degrees Celsius, outlet ${sim.temperature.toFixed(1)} degrees Celsius`);
        if (fuelTransfer) updateFuelHandlingHall(this, fuelTransfer);
        updateElectricalScene(this, sim.supply, dieselMotion, motion.supplyPhase);
        this.render();
    }

    render() {
        if (this.disposed || !this.width || this.host.hidden) return;
        const cameraSignature = [this.width, this.height, this.zoom, this.yaw, this.pitch, this.target.x, this.target.y, this.target.z].join(',');
        if (cameraSignature !== this.cameraSignature) {
            this.cameraSignature = cameraSignature;
            const aspect = this.width / this.height;
            const halfHeight = Math.max(this.frameHeight ?? 8.8, (this.frameWidth ?? 14) / aspect) / this.zoom;
            Object.assign(this.camera, { left: -halfHeight * aspect, right: halfHeight * aspect, top: halfHeight, bottom: -halfHeight });
            this.camera.position.set(Math.sin(this.yaw) * Math.cos(this.pitch) * 36, Math.sin(this.pitch) * 36, Math.cos(this.yaw) * Math.cos(this.pitch) * 36).add(this.target);
            this.camera.lookAt(this.target);
            this.camera.updateProjectionMatrix();
            this.camera.updateMatrixWorld();
        }
        const labels = [...this.labels.values(), ...this.featureLabels];
        const labelSignature = cameraSignature + labels.map(label => label.button.textContent).join('|');
        if (labelSignature !== this.labelSignature) {
            this.labelSignature = labelSignature;
            // Read all label sizes before writing positions; unchanged labels/camera
            // need no layout measurements on subsequent animation frames.
            const positions = labels.map(label => {
                const point = label.anchor.clone().project(this.camera);
                // Keep semantic buttons reachable even at the bounded camera extremes.
                const halfWidth = label.button.offsetWidth / 2 + 6;
                const halfHeight = label.button.offsetHeight / 2 + 6;
                return { button: label.button,
                    left: `${clamp((point.x + 1) * this.width / 2, halfWidth, this.width - halfWidth)}px`,
                    top: `${clamp((1 - point.y) * this.height / 2, halfHeight, this.height - halfHeight)}px` };
            });
            for (const { button, left, top } of positions) {
                if (button.style.left !== left) button.style.left = left;
                if (button.style.top !== top) button.style.top = top;
            }
        }
        this.renderer.render(this.scene, this.camera);
    }

    dispose() {
        if (this.disposed) return;
        this.disposed = true;
        this.abort.abort();
        this.resizeObserver?.disconnect();
        this.drag = null;
        for (const mesh of this.instancedMeshes) mesh.dispose();
        for (const geometry of this.geometries) geometry.dispose();
        for (const material of this.materials) material.dispose();
        this.scene.clear();
        this.renderer.dispose();
        this.renderer.forceContextLoss();
        this.host.replaceChildren();
    }
}
