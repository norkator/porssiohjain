/* Phase-preserving SVG motion driven by reported simulation speed, not commands. */
export function advancePumpMotion(previous, speedPercent, seconds) {
    const speed = Number.isFinite(speedPercent) ? Math.min(100, Math.max(0, speedPercent)) / 100 : 0;
    const dt = Number.isFinite(seconds) ? Math.min(0.1, Math.max(0, seconds)) : 0;
    return {
        angle: (previous.angle + 180 * speed * dt) % 360,
        offset: (previous.offset - 38 * speed * dt) % 19
    };
}
