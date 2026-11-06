import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFileSync, writeFileSync, mkdtempSync, readdirSync } from 'node:fs';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

test('core map, centered variable-speed pumps, automatic control, trip recovery and logs work in browser', { timeout: 90000 }, async () => {
    const artifacts = mkdtempSync(join(tmpdir(), 'nuclear-power-check-'));
    const appUrl = process.env.NUCLEAR_POWER_APP_URL;
    if (appUrl) {
        let ready = false;
        const startupDeadline = Date.now() + 60000;
        while (Date.now() < startupDeadline) {
            try {
                const response = await fetch(`${appUrl}/login`, { signal: AbortSignal.timeout(1000) });
                if (response.ok) { ready = true; break; }
            } catch { /* The disposable Spring instance may still be starting. */ }
            await wait(100);
        }
        assert.ok(ready, 'Disposable local application did not become ready');
    }
    const server = createServer((request, response) => {
        const path = request.url.split('?')[0];
        if (path === '/') {
            response.setHeader('Content-Type', 'text/html');
            response.end(`<!doctype html><html lang="fi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Nuclear Power check</title><style>body{margin:0;background:#101923}main{width:100%}</style><main><nuclear-power-console></nuclear-power-console></main><script type="module" src="/nuclear-power.js"></script></html>`);
            return;
        }
        if (path === '/nuclear-power.css' || path === '/nuclear-power-light.css') {
            response.setHeader('Content-Type', 'text/javascript');
            response.end('export default ' + JSON.stringify(readFileSync(join(root, 'src/main/frontend', path.slice(1)), 'utf8')));
            return;
        }
        const allowed = ['/nuclear-power.js', '/nuclear-power-model.js', '/nuclear-power-motion.js', '/nuclear-power-condition.js'];
        if (!allowed.includes(path)) { response.writeHead(404); response.end(); return; }
        response.setHeader('Content-Type', 'text/javascript');
        response.end(readFileSync(join(root, 'src/main/frontend', path.slice(1))));
    });
    if (!appUrl) await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const browser = spawn(process.env.CHROME_BIN || '/opt/google/chrome/chrome', ['--headless', '--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu', '--remote-debugging-port=0', `--user-data-dir=${join(artifacts, 'profile')}`, 'about:blank']);
    let socket;
    try {
        const endpoint = await new Promise((resolve, reject) => {
            let output = '';
            const timer = setTimeout(() => reject(new Error('Chrome startup timeout: ' + output.slice(-1000))), 10000);
            browser.stderr.on('data', chunk => {
                output += chunk;
                const match = output.match(/DevTools listening on (ws:\/\/\S+)/);
                if (match) { clearTimeout(timer); resolve(match[1]); }
            });
            browser.on('error', error => { clearTimeout(timer); reject(error); });
        });
        socket = new WebSocket(endpoint);
        await new Promise(resolve => socket.addEventListener('open', resolve, { once: true }));
        const pending = new Map();
        const exceptions = [];
        let nextId = 0;
        socket.addEventListener('message', event => {
            const message = JSON.parse(event.data);
            if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails);
            if (!pending.has(message.id)) return;
            const callback = pending.get(message.id);
            pending.delete(message.id);
            message.error ? callback.reject(message.error) : callback.resolve(message.result);
        });
        const send = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
            const id = ++nextId;
            pending.set(id, { resolve, reject });
            socket.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
        });
        const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
        const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
        const page = (method, params = {}) => send(method, params, sessionId);
        const evaluate = async expression => {
            const result = await page('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
            if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
            return result.result.value;
        };
        const until = async (expression, message) => {
            for (let attempt = 0; attempt < 200; attempt++) {
                if (await evaluate(expression)) return;
                await wait(100);
            }
            await screenshot('failure');
            console.log('Failure context:', await evaluate(`({path:location.pathname,text:document.body.innerText.slice(0,1800),buttons:[...document.querySelectorAll('vaadin-button')].map(b=>({text:b.textContent,disabled:b.disabled}))})`));
            throw new Error(message);
        };
        const consoleCode = code => evaluate(`(()=>{const c=document.querySelector('nuclear-power-console');const s=c.shadowRoot;${code}})()`);
        const action = action => consoleCode(`s.querySelector('[data-action="${action}"]').click();`);
        const value = name => consoleCode(`return s.querySelector('[data-value="${name}"]').textContent;`);
        const input = (selector, value) => consoleCode(`const input=s.querySelector(${JSON.stringify(selector)});input.value=${JSON.stringify(String(value))};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));`);
        const advance = seconds => consoleCode(`for(let i=0;i<${seconds * 4};i++)c.simulation.tick(.25);c.update();`);
        const screenshot = async name => {
            await wait(200); // Let palette/hover transitions settle before capturing.
            const result = await page('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
            writeFileSync(join(artifacts, `${name}.png`), Buffer.from(result.data, 'base64'));
        };
        await page('Runtime.enable');
        await page('Page.enable');
        await send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: artifacts });
        await page('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1120, deviceScaleFactor: 1, mobile: false });
        await page('Page.navigate', { url: appUrl ? `${appUrl}/login` : `http://127.0.0.1:${server.address().port}/` });
        if (appUrl) {
            await until(`!!document.querySelector('vaadin-password-field')`, 'Local login did not render');
            await evaluate(`for(const field of [document.querySelector('vaadin-text-field'),document.querySelector('vaadin-password-field')]){field.value='admin';field.dispatchEvent(new Event('input',{bubbles:true,composed:true}));field.dispatchEvent(new Event('change',{bubbles:true,composed:true}));}`);
            await until(`document.querySelector('vaadin-button[theme~="primary"]')?.disabled===false`, 'Login button did not enable');
            await evaluate(`document.querySelector('vaadin-button[theme~="primary"]').click()`);
            await until(`!!document.querySelector('.retro-start-button')`, 'Desktop did not open');
            await evaluate(`document.querySelector('.retro-start-button').click()`);
            await until(`[...document.querySelectorAll('.retro-menu-item')].some(b=>b.textContent.trim()==='Nuclear Power')`, 'Nuclear Power menu item did not open');
            await evaluate(`[...document.querySelectorAll('.retro-menu-item')].find(b=>b.textContent.trim()==='Nuclear Power').click()`);
        }
        await until(`!!document.querySelector('nuclear-power-console')?.shadowRoot?.querySelector('.console')`, 'Control room did not render');
        assert.equal(await value('status'), 'RUNNING');
        assert.equal(await consoleCode('return c.lang;'), 'en');
        assert.equal(await consoleCode('return c.dataset.theme;'), 'dark');
        await consoleCode(`const toggle=s.querySelector('#light-theme');toggle.checked=true;toggle.dispatchEvent(new Event('change',{bubbles:true}));`);
        assert.equal(await consoleCode('return c.dataset.theme;'), 'light');
        assert.equal(await consoleCode('return getComputedStyle(c).colorScheme;'), 'light');
        await wait(200);
        const disabledContrast = await consoleCode(`const style=getComputedStyle(s.querySelector('[data-action="start"]'));const luminance=color=>{const rgb=color.match(/[\\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;});return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;};const a=luminance(style.color),b=luminance(style.backgroundColor);return {opacity:Number(style.opacity),ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)};`);
        assert.equal(disabledContrast.opacity, 1);
        assert.ok(disabledContrast.ratio >= 4.5, 'Light-theme disabled controls should remain readable');
        assert.equal(await consoleCode(`const other=document.createElement('nuclear-power-console');other.style.display='none';c.parentElement.append(other);const theme=other.dataset.theme;other.remove();return theme;`), 'light');
        await consoleCode(`const toggle=s.querySelector('#light-theme');toggle.checked=false;toggle.dispatchEvent(new Event('change',{bubbles:true}));`);
        for (const [width, height] of [[1920, 1080], [2560, 1440]]) {
            await page('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
            await wait(100);
            const layout = await consoleCode(`const rect=selector=>{const r=s.querySelector(selector).getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width};};return {metrics:rect('.metrics'),controls:rect('.control-column'),analysis:rect('.analysis-column'),trends:rect('.trend-panel'),logs:rect('.events-panel'),footer:rect('.console-footer'),console:rect('.console')};`);
            assert.ok(layout.metrics.right <= layout.controls.x);
            assert.ok(layout.controls.right <= layout.analysis.x);
            assert.ok(layout.trends.bottom <= layout.logs.y);
            assert.ok(layout.console.width > width - 50, 'Wide layout should use the full feature width');
            assert.ok(layout.footer.bottom <= height - (appUrl ? 35 : 0), `Wide screen should fit the overview: ${JSON.stringify(layout)}`);
            await screenshot(`wide-${width}-dark`);
            await consoleCode(`const toggle=s.querySelector('#light-theme');toggle.checked=true;toggle.dispatchEvent(new Event('change',{bubbles:true}));`);
            await screenshot(`wide-${width}-light`);
            await consoleCode(`const toggle=s.querySelector('#light-theme');toggle.checked=false;toggle.dispatchEvent(new Event('change',{bubbles:true}));`);
        }
        await action('fluctuations');

        // A rotated local origin must coincide exactly with the stationary hub center.
        for (let frame = 0; frame < 5; frame++) {
            const centers = await consoleCode(`return [...s.querySelectorAll('.pump')].map(pump=>{const hub=new DOMPoint(0,0).matrixTransform(pump.querySelector('.pump-hub').getScreenCTM());const rotor=new DOMPoint(0,0).matrixTransform(pump.querySelector('.rotor').getScreenCTM());return Math.hypot(hub.x-rotor.x,hub.y-rotor.y);});`);
            assert.ok(centers.every(distance => distance < 0.01), `Off-center impellers: ${centers}`);
            await wait(100);
        }
        await input('#pump-A-speed', 45);
        await input('#pump-B-speed', 80);
        await advance(12);
        assert.ok(Number.parseFloat(await value('pumpAActual')) < 46);
        assert.ok(Number.parseFloat(await value('pumpBActual')) > 79);
        const angleBefore = await consoleCode('return {a:c.motion.A.angle,b:c.motion.B.angle};');
        await wait(300);
        const angleAfter = await consoleCode('return {a:c.motion.A.angle,b:c.motion.B.angle};');
        assert.ok((angleAfter.a - angleBefore.a + 360) % 360 < (angleAfter.b - angleBefore.b + 360) % 360);
        await action('reset');
        await consoleCode(`s.querySelector('[data-equipment="reactor"]').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));`);
        assert.equal(await consoleCode(`return s.querySelector('.core-dialog').matches(':popover-open');`), true);
        assert.equal(await consoleCode(`return s.querySelector('.diagram-viewport').hidden;`), false);
        const dialogBefore = await consoleCode(`const r=s.querySelector('.core-dialog').getBoundingClientRect();return {x:r.x,y:r.y};`);
        await consoleCode(`s.querySelector('.core-dialog-titlebar').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true}));`);
        assert.ok(await consoleCode(`return s.querySelector('.core-dialog').getBoundingClientRect().x;`) < dialogBefore.x);
        const header = await consoleCode(`const r=s.querySelector('.core-dialog-titlebar').getBoundingClientRect();return {x:r.x+100,y:r.y+15};`);
        const dragStart = await consoleCode(`return s.querySelector('.core-dialog').getBoundingClientRect().x;`);
        await page('Input.dispatchMouseEvent', { type: 'mousePressed', ...header, button: 'left', clickCount: 1 });
        await page('Input.dispatchMouseEvent', { type: 'mouseMoved', x: header.x - 70, y: header.y + 25, button: 'left', buttons: 1 });
        await page('Input.dispatchMouseEvent', { type: 'mouseReleased', x: header.x - 70, y: header.y + 25, button: 'left', clickCount: 1 });
        assert.ok(await consoleCode(`return s.querySelector('.core-dialog').getBoundingClientRect().x;`) < dragStart - 60);
        assert.equal(await consoleCode(`return s.querySelectorAll('[data-rod]').length;`), 12);
        await consoleCode(`s.querySelector('[data-rod="CR-05"]').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));`);
        await input('#individual-rod', 76);
        assert.equal(await consoleCode(`return c.simulation.controlRods.find(r=>r.id==='CR-05').target;`), 76);
        assert.equal(await consoleCode(`return c.simulation.controlRods.find(r=>r.id==='CR-06').target;`), 36);
        await action('insert');
        assert.equal(await consoleCode(`return c.simulation.controlRods.find(r=>r.id==='CR-05').target;`), 81);
        assert.equal(await consoleCode(`return c.simulation.controlRods.find(r=>r.id==='CR-06').target;`), 41);
        await advance(20);
        assert.ok(Number.parseFloat(await value('selectedRodActual')) > 80);
        await screenshot('desktop-core-map');

        await action('auto');
        assert.equal(await consoleCode(`return s.querySelector('#individual-rod').disabled;`), true);
        assert.equal(await consoleCode(`return s.querySelector('#pump-A-speed').disabled;`), true);
        await input('#auto-temperature', 275);
        await input('#auto-flow', 80);
        await advance(180);
        assert.ok(Math.abs(await consoleCode('return c.simulation.temperature;') - 275) < 3);
        assert.equal(await value('mode'), 'AUTO');
        await input('#auto-objective', 'power');
        await input('#auto-electrical', 150);
        await advance(240);
        assert.ok(Math.abs(await consoleCode('return c.simulation.snapshot().signals.electricalOutput.value;') - 150) < 2);
        assert.equal(await value('temperatureLabel'), 'Core limit');
        assert.equal(await value('chartOutputUnit'), 'MW');
        assert.ok(await consoleCode(`return s.querySelector('.target-trace').getAttribute('d').length>0;`));
        await page('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
        await advance(1200); // Check the power view with a full event journal.
        assert.ok(await consoleCode(`return s.querySelector('.console-footer').getBoundingClientRect().bottom;`) <= 1080 - (appUrl ? 35 : 0), 'Electrical control and a full journal should fit a wide screen');
        await screenshot('electrical-control-dark');
        await consoleCode(`const toggle=s.querySelector('#light-theme');toggle.checked=true;toggle.dispatchEvent(new Event('change',{bubbles:true}));`);
        await screenshot('electrical-control-light');
        if (appUrl) assert.equal(await evaluate(`getComputedStyle(document.querySelector('.nuclear-power-view')).backgroundColor`), 'rgb(237, 242, 245)');
        await consoleCode(`const toggle=s.querySelector('#light-theme');toggle.checked=false;toggle.dispatchEvent(new Event('change',{bubbles:true}));`);
        await input('#auto-electrical', 400);
        await advance(180);
        assert.equal(await value('regulationDetail'), 'Core temperature limit');
        await input('#auto-objective', 'temperature');
        await advance(180);
        await action('fluctuations');
        await advance(60);
        assert.equal(await consoleCode(`return c.simulation.fluctuationsEnabled;`), false);
        await action('trip');
        assert.equal(await value('status'), 'TRIPPED');
        assert.equal(await consoleCode(`return s.querySelector('[data-action="reset-trip"]').disabled;`), true);
        await advance(8);
        assert.equal(await consoleCode(`return s.querySelector('[data-action="reset-trip"]').disabled;`), false);
        const beforeRecovery = await consoleCode('return c.simulation.elapsed;');
        await action('reset-trip');
        assert.equal(await consoleCode('return c.simulation.running;'), false);
        assert.ok(await consoleCode('return c.simulation.elapsed;') >= beforeRecovery);
        await action('start');
        assert.equal(await consoleCode('return c.simulation.running;'), true);
        await input('#log-filter', 'automation');
        assert.equal(await consoleCode(`return [...s.querySelectorAll('.event-category')].every(e=>e.textContent==='AUTOMATION');`), true);
        await action('export-log');
        for (let attempt = 0; attempt < 40 && !readdirSync(artifacts).some(name => name.endsWith('.csv')); attempt++) await wait(100);
        const csv = readFileSync(join(artifacts, 'nuclear-power-simulation-log.csv'), 'utf8');
        assert.ok(csv.includes('"SIMULATED"'));
        assert.ok(csv.includes('"automation"'));
        assert.ok(csv.includes('"alarm"'));

        await action('close-core');
        assert.equal(await consoleCode(`return s.querySelector('.core-dialog').matches(':popover-open');`), false);
        assert.equal(await consoleCode(`return s.querySelector('.diagram-viewport').hidden;`), false);
        await screenshot('desktop-process');
        await page('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
        await wait(150);
        await action('core-view');
        assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
        assert.equal(await consoleCode('return c.scrollWidth <= c.clientWidth;'), true);
        await consoleCode(`s.querySelector('.core-map').scrollIntoView();`);
        await screenshot('mobile-core-map');
        await action('manual');
        await input('#individual-rod', 45);
        assert.equal(await consoleCode(`return c.simulation.controlRods.find(r=>r.id==='CR-05').target;`), 45);
        await consoleCode(`c.simulation.temperature=350;c.simulation.updateAlarms();c.update();`);
        assert.equal(await consoleCode('return c.dataset.overheated;'), 'true');
        assert.ok(await value('temperatureState') === 'HIGH TEMPERATURE');
        await action('acknowledge');
        assert.equal(await consoleCode('return c.dataset.overheated;'), 'true');
        await consoleCode(`c.simulation.temperature=286;c.simulation.updateAlarms();c.update();`);
        assert.equal(await consoleCode('return c.dataset.overheated;'), 'false');
        await action('reset');
        await input('#fault-scenario', 'primary-restriction');
        await advance(10);
        assert.ok(await consoleCode('return c.simulation.primaryFlow;') < 95);
        assert.equal(await consoleCode('return c.simulation.condition.meltdown;'), false);
        await action('clear-fault');
        await advance(120);
        assert.equal(await consoleCode('return c.simulation.condition.damaged;'), false);
        await action('reset');
        await action('pumpA'); await action('pumpB');
        await advance(20);
        assert.equal(await consoleCode('return c.simulation.condition.coreDamage;'), 0);
        await screenshot('cooling-loss-warning');
        await advance(300);
        assert.equal(await value('status'), 'MELTDOWN');
        assert.equal(await value('conditionStage'), 'MELTDOWN');
        assert.ok(Number(await value('chartTempMax')) >= await consoleCode('return c.simulation.temperature;'));
        assert.equal(await consoleCode(`return s.querySelector('[data-action="reset-trip"]').disabled;`), true);
        assert.equal(await consoleCode(`return s.querySelector('[data-action="repair"]').disabled;`), true);
        assert.ok(await consoleCode(`return s.querySelectorAll('[data-rod][data-condition="damaged"]').length;`) > 0);
        await action('export-incident');
        for (let attempt = 0; attempt < 40 && !readdirSync(artifacts).includes('nuclear-power-incident.json'); attempt++) await wait(100);
        const incident = JSON.parse(readFileSync(join(artifacts, 'nuclear-power-incident.json'), 'utf8'));
        assert.equal(incident.source, 'SIMULATED');
        assert.match(incident.cause, /pump A/);
        assert.ok(incident.timeline.some(event => event.stage === 'MELTDOWN'));
        await screenshot('gradual-meltdown');
        await action('reset');
        assert.equal(await value('conditionStage'), 'NORMAL');
        await action('pause');
        assert.equal(await consoleCode('return c.timer;'), null);
        assert.equal(await consoleCode('return c.animationFrame;'), null);
        await action('pause');
        await page('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
        await wait(100);
        assert.equal(await consoleCode('return c.animationFrame;'), null);
        assert.ok(await consoleCode('return Boolean(c.timer);'));
        await page('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] });
        await wait(100);
        assert.ok(await consoleCode('return Boolean(c.animationFrame);'));
        await consoleCode(`c.style.display='none';`);
        await wait(150);
        assert.equal(await consoleCode('return c.timer;'), null);
        assert.equal(await consoleCode('return c.animationFrame;'), null);
        await consoleCode(`c.style.display='';`);
        await wait(150);
        assert.ok(await consoleCode('return Boolean(c.timer);'));
        await evaluate(`window.savedConsole=document.querySelector('nuclear-power-console');savedConsole.remove();`);
        assert.equal(await evaluate('savedConsole.timer'), null);
        assert.equal(await evaluate('savedConsole.animationFrame'), null);
        assert.deepEqual(exceptions, []);
        console.log(`Browser verified ${appUrl ? 'authenticated Vaadin application' : 'isolated console'}; artifacts: ${artifacts}`);
        await send('Browser.close');
    } finally {
        socket?.close();
        browser.kill('SIGTERM');
        if (server.listening) server.close();
    }
});
