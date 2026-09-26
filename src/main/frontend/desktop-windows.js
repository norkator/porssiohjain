// Window positions belong to this page only; no browser or backend persistence.
window.initDesktopWindow = frame => {
    if (frame.desktopCleanup) return;
    const desktop = frame.closest('.retro-desktop');
    const title = frame.querySelector('.retro-titlebar');
    const handle = frame.querySelector('.retro-resize-handle');
    const mobile = () => matchMedia('(max-width: 620px)').matches;
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
    const end = () => { drag = null; frame.classList.remove('retro-window-dragging'); };
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
        handle.addEventListener(event, () => { resize = null; });
    }
    handle.addEventListener('keydown', e => {
        if (mobile() || frame.classList.contains('retro-window-maximized')) return;
        const delta = { ArrowLeft: [-10, 0], ArrowRight: [10, 0], ArrowUp: [0, -10], ArrowDown: [0, 10] }[e.key];
        if (!delta) return;
        e.preventDefault();
        setSize(frame.offsetWidth + delta[0], frame.offsetHeight + delta[1]);
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
    frame.addEventListener('pointerdown', raise);
    const observer = new ResizeObserver(clamp);
    observer.observe(desktop);
    frame.desktopCleanup = () => observer.disconnect();
    const detached = new MutationObserver(() => {
        if (!frame.isConnected) { frame.desktopCleanup(); detached.disconnect(); }
    });
    detached.observe(desktop, { childList: true });
};
