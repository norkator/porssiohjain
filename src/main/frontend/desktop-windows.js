// Geometry stays in this browser and is scoped to the effective account and feature.
window.addEventListener('beforeunload', event => {
    if (document.querySelector('[data-desktop-dirty="true"]')) {
        event.preventDefault();
        event.returnValue = '';
    }
});
window.initDesktopWindow = frame => {
    if (frame.desktopCleanup) return;
    const desktop = frame.closest('.retro-desktop');
    const title = frame.querySelector('.retro-titlebar');
    const handle = frame.querySelector('.retro-resize-handle');
    const mobile = () => matchMedia('(max-width: 620px)').matches;
    const storageKey = 'porssiohjain:window:v1:' + frame.dataset.windowKey;
    const saveGeometry = () => {
        if (mobile()) return;
        try {
            localStorage.setItem(storageKey, JSON.stringify({
                maximized: frame.classList.contains('retro-window-maximized'),
                left: parseFloat(frame.style.left), top: parseFloat(frame.style.top),
                width: parseFloat(frame.style.width), height: parseFloat(frame.style.height)
            }));
        } catch (_) { /* Private browsing or full storage must not prevent window use. */ }
    };
    const raise = () => {
        const frames = [...desktop.querySelectorAll('.retro-window')];
        frames.sort((a, b) => (+a.style.zIndex || 0) - (+b.style.zIndex || 0));
        frames.filter(w => w !== frame).forEach((w, i) => w.style.zIndex = i + 2);
        frame.style.zIndex = frames.length + 2;
    };
    const clamp = () => {
        if (mobile() || frame.classList.contains('retro-window-maximized')) return;
        if (!frame.offsetWidth) return;
        if (!frame.style.left) {
            frame.style.left = frame.offsetLeft + 'px';
            frame.style.top = frame.offsetTop + 'px';
        }
        if (frame.style.width) frame.style.width = Math.min(parseFloat(frame.style.width), desktop.clientWidth) + 'px';
        if (frame.style.height) frame.style.height = Math.min(parseFloat(frame.style.height), desktop.clientHeight - 38) + 'px';
        frame.style.left = Math.max(0, Math.min(parseFloat(frame.style.left), desktop.clientWidth - frame.offsetWidth)) + 'px';
        frame.style.top = Math.max(0, Math.min(parseFloat(frame.style.top), desktop.clientHeight - 38 - frame.offsetHeight)) + 'px';
    };
    let drag;
    const end = () => { if (drag) saveGeometry(); drag = null; frame.classList.remove('retro-window-dragging'); };
    const down = e => {
        if (e.button !== 0 || e.target.closest('vaadin-button') || mobile() || frame.classList.contains('retro-window-maximized')) return;
        const bounds = frame.getBoundingClientRect();
        const area = desktop.getBoundingClientRect();
        drag = { x: e.clientX, y: e.clientY, left: bounds.left - area.left, top: bounds.top - area.top };
        title.setPointerCapture(e.pointerId);
        frame.classList.add('retro-window-dragging');
        raise();
        e.preventDefault();
    };
    const move = e => {
        if (!drag) return;
        frame.style.left = (drag.left + e.clientX - drag.x) + 'px';
        frame.style.top = (drag.top + e.clientY - drag.y) + 'px';
        clamp();
    };
    let resize;
    const setSize = (width, height) => {
        const maxWidth = desktop.clientWidth - frame.offsetLeft;
        const maxHeight = desktop.clientHeight - 38 - frame.offsetTop;
        frame.style.width = Math.max(0, Math.min(maxWidth, Math.max(320, width))) + 'px';
        frame.style.height = Math.max(0, Math.min(maxHeight, Math.max(220, height))) + 'px';
    };
    handle.addEventListener('pointerdown', e => {
        if (e.button !== 0 || mobile() || frame.classList.contains('retro-window-maximized')) return;
        clamp();
        resize = { x: e.clientX, y: e.clientY, width: frame.offsetWidth, height: frame.offsetHeight };
        handle.setPointerCapture(e.pointerId);
        handle.focus({ preventScroll: true });
        raise();
        e.preventDefault();
    });
    handle.addEventListener('pointermove', e => {
        if (resize) setSize(resize.width + e.clientX - resize.x, resize.height + e.clientY - resize.y);
    });
    for (const event of ['pointerup', 'pointercancel', 'lostpointercapture']) {
        handle.addEventListener(event, () => { if (resize) saveGeometry(); resize = null; });
    }
    handle.addEventListener('keydown', e => {
        if (mobile() || frame.classList.contains('retro-window-maximized')) return;
        const delta = { ArrowLeft: [-10, 0], ArrowRight: [10, 0], ArrowUp: [0, -10], ArrowDown: [0, 10] }[e.key];
        if (!delta) return;
        e.preventDefault();
        setSize(frame.offsetWidth + delta[0], frame.offsetHeight + delta[1]);
        saveGeometry();
    });
    const maximize = e => {
        if (!e.target.closest('vaadin-button') && !mobile()) frame.dispatchEvent(new CustomEvent('desktop-maximize'));
    };
    title.addEventListener('pointerdown', down);
    title.addEventListener('pointermove', move);
    title.addEventListener('pointerup', end);
    title.addEventListener('pointercancel', end);
    title.addEventListener('lostpointercapture', end);
    title.addEventListener('dblclick', maximize);
    frame.addEventListener('desktop-raise', raise);
    frame.addEventListener('desktop-save-geometry', () => { clamp(); saveGeometry(); });
    frame.addEventListener('pointerdown', raise);
    if (!mobile()) {
        try {
            const saved = JSON.parse(localStorage.getItem(storageKey));
            if (saved && typeof saved.maximized === 'boolean') {
                for (const property of ['left', 'top', 'width', 'height']) {
                    if (Number.isFinite(saved[property]) && saved[property] >= 0
                            && (!['width', 'height'].includes(property) || saved[property] > 0)) {
                        frame.style[property] = saved[property] + 'px';
                    }
                }
                frame.classList.toggle('retro-window-maximized', saved.maximized);
                frame.dispatchEvent(new CustomEvent('desktop-restore-geometry', { detail: { maximized: saved.maximized } }));
                clamp();
            }
        } catch (_) { /* Ignore obsolete or malformed saved geometry. */ }
    }
    const observer = new ResizeObserver(clamp);
    observer.observe(desktop);
    frame.desktopCleanup = () => observer.disconnect();
    const detached = new MutationObserver(() => {
        if (!frame.isConnected) { frame.desktopCleanup(); detached.disconnect(); }
    });
    detached.observe(desktop, { childList: true });
};

// Keep whole shortcuts within the available desktop, including on small screens.
window.initDesktopIcons = icons => {
    if (icons.desktopIconsObserver) return;
    const fit = () => {
        const rows = Math.floor(icons.clientHeight / 90);
        const columns = Math.floor(icons.clientWidth / 104);
        icons.style.setProperty('--desktop-icon-rows', Math.max(1, rows));
        [...icons.children].forEach((icon, index) => icon.hidden = index >= rows * columns);
    };
    const observer = new ResizeObserver(fit);
    icons.desktopIconsObserver = observer;
    observer.observe(icons);
    fit();
    const detached = new MutationObserver(() => {
        if (!icons.isConnected) { observer.disconnect(); detached.disconnect(); }
    });
    detached.observe(icons.parentElement, { childList: true });
};

window.initDesktopStatus = widget => {
    if (widget.desktopStatusCleanup) return;
    const timer = setInterval(() => {
        if (!document.hidden && widget.isConnected) {
            widget.dispatchEvent(new CustomEvent('desktop-status-refresh'));
        }
    }, 60000);
    const detached = new MutationObserver(() => {
        if (!widget.isConnected) widget.desktopStatusCleanup();
    });
    widget.desktopStatusCleanup = () => { clearInterval(timer); detached.disconnect(); };
    detached.observe(document.body, { childList: true, subtree: true });
};
