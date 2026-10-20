// API-call topology only: every observed caller connects to the service hub.
// No device-to-device or Zigbee radio links are inferred from HTTP calls.
class DeviceConnectionMap extends HTMLElement {
    connectedCallback() {
        if (this.resizeObserver) this.resizeObserver.observe(this.viewport);
    }

    disconnectedCallback() {
        this.resizeObserver?.disconnect();
    }

    update(rows, labels) {
        const previous = new Map((this.calls || []).map(call => [call.key, call.calledAt]));
        this.changed = new Set();
        this.labels = labels;
        this.calls = rows.map(([type, id, ip, calledAt, endpoint]) => {
            const key = JSON.stringify([type, id]);
            if (this.calls && previous.get(key) !== calledAt) this.changed.add(key);
            return { key, type, id, ip, calledAt, endpoint };
        }).sort((a, b) => a.key.localeCompare(b.key));
        if (!this.viewport) this.initialize();
        this.draw();
    }

    element(tag, className, text) {
        const element = document.createElement(tag);
        if (className) element.className = className;
        if (text !== undefined) element.textContent = text;
        return element;
    }

    initialize() {
        this.fitting = true;
        this.scale = 1;
        const toolbar = this.element('div', 'connection-map-toolbar');
        const searchLabel = this.element('label', 'connection-map-search', this.labels.search);
        this.search = this.element('input');
        this.search.type = 'search';
        this.search.addEventListener('input', () => this.draw());
        searchLabel.append(this.search);
        toolbar.append(searchLabel);
        const button = (text, title, callback) => {
            const control = this.element('button', 'connection-map-tool', text);
            control.type = 'button';
            control.title = title;
            control.setAttribute('aria-label', title);
            control.addEventListener('click', callback);
            toolbar.append(control);
        };
        button('−', this.labels.zoomOut, () => this.zoom(1 / 1.25));
        button('+', this.labels.zoomIn, () => this.zoom(1.25));
        button(this.labels.fit, this.labels.fit, () => {
            this.fitting = true;
            this.applyScale(true);
        });
        this.zoomLabel = this.element('span', 'connection-map-zoom');
        toolbar.append(this.zoomLabel);
        this.summary = this.element('div', 'connection-map-summary');
        this.viewport = this.element('div', 'connection-map-viewport');
        this.viewport.tabIndex = 0;
        this.viewport.setAttribute('role', 'region');
        this.viewport.setAttribute('aria-label', this.labels.viewport);
        this.stage = this.element('div', 'connection-map-stage');
        this.board = this.element('div', 'connection-map-board');
        this.stage.append(this.board);
        this.viewport.append(this.stage);
        const legend = this.element('div', 'connection-map-legend');
        for (const [className, text] of [
            ['control', this.labels.controlDevice], ['gateway', this.labels.zigbeeGateway],
            ['recent', this.labels.recent], ['older', this.labels.older]
        ]) {
            legend.append(this.element('span', className, text));
        }
        this.details = this.element('div', 'connection-map-details');
        this.append(toolbar, this.summary, this.viewport, legend, this.details);
        this.resizeObserver = new ResizeObserver(() => this.applyScale(this.fitting));
        this.resizeObserver.observe(this.viewport);

        // Touch devices use native scrolling; a mouse can also drag the canvas.
        let drag;
        this.viewport.addEventListener('pointerdown', event => {
            if (event.pointerType !== 'mouse' || event.button !== 0 || event.target.closest('button')) return;
            drag = { x: event.clientX, y: event.clientY, left: this.viewport.scrollLeft, top: this.viewport.scrollTop };
            this.viewport.setPointerCapture(event.pointerId);
            event.preventDefault();
        });
        this.viewport.addEventListener('pointermove', event => {
            if (!drag) return;
            this.viewport.scrollLeft = drag.left - event.clientX + drag.x;
            this.viewport.scrollTop = drag.top - event.clientY + drag.y;
        });
        this.viewport.addEventListener('pointerup', () => { drag = undefined; });
        this.viewport.addEventListener('pointercancel', () => { drag = undefined; });
    }

    typeLabel(call) {
        return call.type === 'ZIGBEE_GATEWAY' ? this.labels.zigbeeGateway : this.labels.controlDevice;
    }

    timeLabel(call) {
        return new Intl.DateTimeFormat(document.documentElement.lang || 'en', {
            timeZone: 'Europe/Helsinki', year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
        }).format(new Date(call.calledAt));
    }

    isRecent(call) {
        return Date.parse(call.calledAt) > Date.now() - 120000;
    }

    // Concentric grid rings guarantee that labels never overlap, even at the cap.
    positions(count) {
        const positions = [];
        for (let ring = 1; positions.length < count; ring++) {
            const slots = [];
            for (let offset = -ring; offset < ring; offset++) slots.push([ring, offset]);
            for (let offset = ring; offset > -ring; offset--) slots.push([offset, ring]);
            for (let offset = ring; offset > -ring; offset--) slots.push([-ring, offset]);
            for (let offset = -ring; offset < ring; offset++) slots.push([offset, -ring]);
            const size = Math.min(count - positions.length, slots.length);
            for (let index = 0; index < size; index++) {
                // Spread partially filled rings around the center, starting on the right.
                const slot = (Math.floor(index * slots.length / size) + ring) % slots.length;
                positions.push(slots[slot]);
            }
        }
        return positions;
    }

    draw() {
        const query = this.search.value.trim().toLocaleLowerCase();
        const calls = this.calls.filter(call =>
            [call.id, call.ip, call.endpoint, this.typeLabel(call)].some(value => value.toLocaleLowerCase().includes(query)));
        this.summary.textContent = `${calls.length} / ${this.calls.length} ${this.labels.clientCount}`;
        const focused = document.activeElement?.dataset?.clientKey;
        const positions = this.positions(calls.length);
        const ring = Math.max(0, ...positions.map(([x, y]) => Math.max(Math.abs(x), Math.abs(y))));
        const width = (ring * 2 + 1) * 280;
        const height = (ring * 2 + 1) * 148;
        const moved = this.width !== width || this.height !== height;
        this.width = width;
        this.height = height;
        this.board.replaceChildren();
        this.board.style.width = `${width}px`;
        this.board.style.height = `${height}px`;
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('width', width);
        svg.setAttribute('height', height);
        svg.setAttribute('aria-hidden', 'true');
        svg.classList.add('connection-map-links');
        this.board.append(svg);

        calls.forEach((call, index) => {
            const [column, row] = positions[index];
            const x = width / 2 + column * 280;
            const y = height / 2 + row * 148;
            const recent = this.isRecent(call);
            const changed = this.changed.has(call.key);
            const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            for (const [key, value] of Object.entries({ x1: x, y1: y, x2: width / 2, y2: height / 2 })) {
                line.setAttribute(key, value);
            }
            line.setAttribute('class', `connection-map-link ${recent ? 'recent' : ''} ${changed ? 'new-call' : ''}`);
            svg.append(line);
            const node = this.element('button', `connection-map-node ${call.type === 'ZIGBEE_GATEWAY' ? 'gateway' : 'control'} ${recent ? 'recent' : ''} ${changed ? 'new-call' : ''}`);
            node.type = 'button';
            node.dataset.clientKey = call.key;
            node.style.left = `${x}px`;
            node.style.top = `${y}px`;
            node.setAttribute('aria-pressed', String(this.selected === call.key));
            const state = recent ? this.labels.recent : this.labels.older;
            node.title = `${this.typeLabel(call)} · ${call.id}\n${this.labels.clientIp}: ${call.ip}\n${this.labels.lastCall}: ${this.timeLabel(call)} (Europe/Helsinki)\n${state}\n${call.endpoint}`;
            node.setAttribute('aria-label', node.title);
            node.append(this.element('span', 'connection-map-type', this.typeLabel(call)),
                this.element('strong', 'connection-map-id', call.id),
                this.element('span', 'connection-map-ip', `${this.labels.clientIp}: ${call.ip}`),
                this.element('span', 'connection-map-time', this.timeLabel(call)));
            node.addEventListener('click', () => {
                this.selected = call.key;
                this.showDetails();
            });
            this.board.append(node);
            if (focused === call.key) node.focus({ preventScroll: true });
        });
        const hub = this.element('div', 'connection-map-node connection-map-hub');
        hub.style.left = `${width / 2}px`;
        hub.style.top = `${height / 2}px`;
        hub.append(this.element('span', 'connection-map-type', this.labels.service),
            this.element('strong', '', 'Pörssiohjain'),
            this.element('span', '', `${this.calls.length} ${this.labels.clientCount}`));
        this.board.append(hub);
        if (!calls.length) {
            const empty = this.element('div', 'connection-map-empty', this.calls.length ? this.labels.noMatches : this.labels.empty);
            this.board.append(empty);
        }
        this.showDetails();
        this.applyScale(moved);
        this.changed.clear();
    }

    showDetails() {
        this.board.querySelectorAll('button[data-client-key]').forEach(node =>
            node.setAttribute('aria-pressed', String(node.dataset.clientKey === this.selected)));
        const call = this.calls.find(call => call.key === this.selected);
        this.details.replaceChildren();
        if (!call) {
            this.details.textContent = this.labels.select;
            return;
        }
        const list = this.element('dl');
        for (const [label, value] of [
            [this.typeLabel(call), this.isRecent(call) ? this.labels.recent : this.labels.older],
            [this.labels.clientId, call.id], [this.labels.clientIp, call.ip],
            [this.labels.lastCall, `${this.timeLabel(call)} (Europe/Helsinki)`],
            [this.labels.endpoint, call.endpoint]
        ]) {
            list.append(this.element('dt', '', label), this.element('dd', '', value));
        }
        this.details.append(list);
    }

    zoom(factor) {
        this.fitting = false;
        this.scale = Math.max(0.1, Math.min(2, this.scale * factor));
        this.applyScale(true);
    }

    applyScale(center) {
        if (!this.width || !this.viewport.clientWidth) return;
        if (this.fitting) {
            this.scale = Math.min(1, (this.viewport.clientWidth - 24) / this.width,
                (this.viewport.clientHeight - 24) / this.height);
        }
        const width = Math.max(this.viewport.clientWidth, this.width * this.scale);
        const height = Math.max(this.viewport.clientHeight, this.height * this.scale);
        this.stage.style.width = `${width}px`;
        this.stage.style.height = `${height}px`;
        this.board.style.left = `${(width - this.width * this.scale) / 2}px`;
        this.board.style.top = `${(height - this.height * this.scale) / 2}px`;
        this.board.style.transform = `scale(${this.scale})`;
        this.zoomLabel.textContent = `${Math.round(this.scale * 100)}%`;
        if (center) {
            this.viewport.scrollLeft = (width - this.viewport.clientWidth) / 2;
            this.viewport.scrollTop = (height - this.viewport.clientHeight) / 2;
        }
    }
}

customElements.define('device-connection-map', DeviceConnectionMap);
