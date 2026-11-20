// Account groups represent ownership. Links show service connectivity, not device-to-device or radio links.
class DeviceConnectionMap extends HTMLElement {
    connectedCallback() {
        if (this.resizeObserver) this.resizeObserver.observe(this.viewport);
    }

    disconnectedCallback() {
        this.resizeObserver?.disconnect();
    }

    update(rows, labels) {
        const previous = new Map((this.calls || []).map(call => [call.key, this.activity(call)]));
        this.changed = new Set();
        this.labels = labels;
        this.calls = rows.map(row => {
            const key = JSON.stringify([row.type === 'ZIGBEE_GATEWAY' ? 'gateway' : 'device', row.id]);
            const call = { ...row, key };
            if (previous.has(key) && previous.get(key) !== this.activity(call)) this.changed.add(key);
            return call;
        }).sort((a, b) => a.key.localeCompare(b.key));
        if (!this.viewport) this.initialize();
        this.draw();
    }

    activity(call) {
        return JSON.stringify([call.calledAt, call.lastCommunication, call.online]);
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
        this.summary.setAttribute('aria-live', 'polite');
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
            ['heat-pump', this.labels.heatPump], ['thermostat', this.labels.thermostat],
            ['sensor', this.labels.temperatureSensor], ['online', this.labels.online],
            ['offline', this.labels.offline], ['recent', this.labels.recent]
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

    typeInfo(call) {
        return {
            CONTROL_DEVICE: ['control', this.labels.controlDevice],
            ZIGBEE_GATEWAY: ['gateway', this.labels.zigbeeGateway],
            HEAT_PUMP: ['heat-pump', this.labels.heatPump],
            THERMOSTAT: ['thermostat', this.labels.thermostat],
            TEMPERATURE_SENSOR: ['sensor', this.labels.temperatureSensor]
        }[call.type] || ['control', call.type];
    }

    accountLabel(call) {
        if (!call.accountId) return this.labels.unassigned;
        return `${this.labels.account} #${call.accountId}${call.accountEmail ? ` · ${call.accountEmail}` : ''}`;
    }

    timeLabel(time) {
        if (!time) return '—';
        return new Intl.DateTimeFormat(document.documentElement.lang || 'en', {
            timeZone: 'Europe/Helsinki', year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
        }).format(new Date(time));
    }

    isRecent(call) {
        return Date.parse(call.calledAt) > Date.now() - 120000;
    }

    statusLabel(call) {
        return call.online === true ? this.labels.online : call.online === false ? this.labels.offline : this.labels.unknown;
    }

    // Stack account panels in two balanced banks, with a clear service corridor between them.
    layout(calls) {
        const byAccount = new Map();
        calls.forEach(call => {
            const key = call.accountId || '';
            if (!byAccount.has(key)) byAccount.set(key, { key, label: this.accountLabel(call), calls: [] });
            byAccount.get(key).calls.push(call);
        });
        const groups = [...byAccount.values()].sort((a, b) =>
            !a.key ? 1 : !b.key ? -1 : a.key.localeCompare(b.key, undefined, { numeric: true }));
        const heights = [0, 0];
        groups.forEach(group => {
            group.calls.sort((a, b) => Number(b.online === true) - Number(a.online === true)
                || (a.name === '—' ? a.id : a.name).localeCompare(b.name === '—' ? b.id : b.name));
            group.side = heights[0] <= heights[1] ? 0 : 1;
            group.width = 548;
            group.height = 66 + Math.ceil(group.calls.length / 2) * 126;
            group.y = heights[group.side];
            heights[group.side] += group.height + 32;
        });
        const height = Math.max(260, ...heights.map(value => value ? value - 32 : 0)) + 48;
        groups.forEach(group => {
            group.x = group.side === 0 ? 24 : 900;
            group.y += (height - (heights[group.side] - 32)) / 2;
        });
        return { groups, width: calls.length ? 1472 : 420, height };
    }

    draw() {
        const query = this.search.value.trim().toLocaleLowerCase();
        const calls = this.calls.filter(call =>
            [call.id, call.name, call.ip, call.endpoint, call.connection, this.typeInfo(call)[1], this.accountLabel(call)]
                .some(value => value.toLocaleLowerCase().includes(query)));
        const { groups, width, height } = this.layout(calls);
        const online = calls.filter(call => call.online === true).length;
        this.summary.textContent = `${calls.length} / ${this.calls.length} ${this.labels.clientCount} · ${online} ${this.labels.online} · ${groups.filter(group => group.key).length} ${this.labels.accountCount}`;
        const focused = document.activeElement?.dataset?.clientKey;
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

        groups.forEach(group => {
            const x = group.side === 0 ? group.x + group.width : group.x;
            const y = group.y + group.height / 2;
            const corridor = group.side === 0 ? width / 2 - 144 : width / 2 + 144;
            const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            line.setAttribute('d', `M ${width / 2} ${height / 2} H ${corridor} V ${y} H ${x}`);
            line.setAttribute('class', `connection-map-link ${group.calls.some(call => call.online === true) ? 'online' : ''} ${group.calls.some(call => this.changed.has(call.key)) ? 'new-call' : ''}`);
            svg.append(line);
            const panel = this.element('section', 'connection-map-group');
            panel.dataset.accountId = group.key;
            panel.setAttribute('aria-label', group.label);
            Object.assign(panel.style, { left: `${group.x}px`, top: `${group.y}px`, width: `${group.width}px`, height: `${group.height}px` });
            const header = this.element('div', 'connection-map-group-header');
            const title = this.element('strong', '', group.label);
            title.title = group.label;
            header.append(title, this.element('span', '', `${group.calls.length} ${this.labels.clientCount} · ${group.calls.filter(call => call.online === true).length} ${this.labels.online}`));
            panel.append(header);
            group.calls.forEach((call, index) => {
                const [typeClass, typeLabel] = this.typeInfo(call);
                const node = this.element('button', `connection-map-node ${typeClass} ${call.online === true ? 'online' : ''} ${this.changed.has(call.key) ? 'new-call' : ''}`);
                node.type = 'button';
                node.dataset.clientKey = call.key;
                node.style.left = `${142 + (index % 2) * 264}px`;
                node.style.top = `${120 + Math.floor(index / 2) * 126}px`;
                node.setAttribute('aria-pressed', String(this.selected === call.key));
                const status = `${this.statusLabel(call)}${call.connection !== '—' ? ` · ${call.connection}` : ''}`;
                node.title = `${group.label}\n${typeLabel} · ${call.id}\n${status}\n${this.labels.clientIp}: ${call.ip}\n${this.labels.lastCommunication}: ${this.timeLabel(call.lastCommunication)}\n${this.labels.lastCall}: ${this.timeLabel(call.calledAt)} (Europe/Helsinki)\n${this.isRecent(call) ? this.labels.recent : ''}\n${call.endpoint}`;
                node.setAttribute('aria-label', node.title);
                const type = this.element('span', 'connection-map-type', typeLabel);
                if (this.isRecent(call)) {
                    const recent = this.element('span', 'connection-map-recent-call', '◷');
                    recent.title = this.labels.recent;
                    recent.setAttribute('aria-label', this.labels.recent);
                    type.append(recent);
                }
                node.append(type,
                    this.element('strong', 'connection-map-id', call.name === '—' ? call.id : call.name),
                    this.element('span', 'connection-map-status', status),
                    this.element('span', 'connection-map-ip', `${this.labels.clientIp}: ${call.ip}`),
                    this.element('span', 'connection-map-time', this.timeLabel(call.lastCommunication || call.calledAt)));
                node.addEventListener('click', () => {
                    this.selected = call.key;
                    this.showDetails();
                });
                panel.append(node);
            });
            this.board.append(panel);
        });
        const hub = this.element('div', 'connection-map-node connection-map-hub');
        hub.style.left = `${width / 2}px`;
        hub.style.top = `${height / 2}px`;
        hub.append(this.element('span', 'connection-map-type', this.labels.service),
            this.element('strong', '', 'Pörssiohjain'),
            this.element('span', '', `${this.calls.length} ${this.labels.clientCount}`));
        this.board.append(hub);
        if (!calls.length) {
            this.board.append(this.element('div', 'connection-map-empty', this.calls.length ? this.labels.noMatches : this.labels.empty));
        }
        if (focused) {
            [...this.board.querySelectorAll('button[data-client-key]')]
                .find(node => node.dataset.clientKey === focused)?.focus({ preventScroll: true });
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
            [this.labels.account, this.accountLabel(call)],
            [this.typeInfo(call)[1], this.statusLabel(call)],
            [this.labels.deviceName, call.name], [this.labels.clientId, call.id],
            [this.labels.connection, call.connection], [this.labels.clientIp, call.ip],
            [this.labels.lastCommunication, `${this.timeLabel(call.lastCommunication)} (Europe/Helsinki)`],
            [this.labels.lastCall, call.calledAt ? `${this.timeLabel(call.calledAt)} (Europe/Helsinki) · ${this.isRecent(call) ? this.labels.recent : this.labels.older}` : this.labels.noCall],
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
            this.scale = Math.max(0.01, Math.min(1, (this.viewport.clientWidth - 24) / this.width,
                (this.viewport.clientHeight - 24) / this.height));
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
