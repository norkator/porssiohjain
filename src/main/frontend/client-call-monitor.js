// Component-local refresh avoids competing with other windows for UI polling.
window.initClientCallMonitor = (status, countdown, refreshing) => {
    status.clientCallCleanup?.();
    let nextRefresh = Date.now() + 10000;
    let pending = false;
    const tick = () => {
        if (!status.isConnected) {
            status.clientCallCleanup();
            return;
        }
        if (document.hidden || !status.getClientRects().length || pending) return;
        const seconds = Math.max(0, Math.ceil((nextRefresh - Date.now()) / 1000));
        status.textContent = countdown.replace('{seconds}', seconds);
        if (seconds === 0) {
            pending = true;
            status.textContent = refreshing;
            status.dispatchEvent(new CustomEvent('client-call-refresh'));
        }
    };
    status.clientCallRefreshComplete = () => {
        pending = false;
        nextRefresh = Date.now() + 10000;
        tick();
    };
    const timer = setInterval(tick, 250);
    const detached = new MutationObserver(() => {
        if (!status.isConnected) status.clientCallCleanup();
    });
    status.clientCallCleanup = () => {
        clearInterval(timer);
        detached.disconnect();
    };
    detached.observe(document.body, { childList: true, subtree: true });
    tick();
};
