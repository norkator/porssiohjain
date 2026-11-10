// Presentation state only: fuel renewal remains the existing atomic model action.
// One displayed assembly represents each selected or batch replacement operation.
export const CHANNEL_PITCH = .33;
export const FUEL_TRANSFER_DURATION = 10.5;
export const FUEL_HANDLING = Object.freeze({
    deckY: 6.18, homeZ: -4.75, bridgeY: 10.38, trolleyY: .18,
    parkedGrabberY: 8.76, clearanceGrabberY: 9.3,
    assemblyHeight: 2.45, gripOffset: .4, coreFuelY: 3, storedFuelY: 3.4,
});
export const FUEL_POOL_SLOTS = Array.from({ length: 35 }, (_, index) => ({
    id: `SF-${String(index + 1).padStart(2, '0')}`,
    x: (index % 7 - 3) * .53, z: FUEL_HANDLING.homeZ + (Math.floor(index / 7) - 2) * .62,
}));

const mix = (a, b, t) => a + (b - a) * t;
const ease = value => { const t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t); };

export class FuelTransferAnimation {
    constructor(channels) {
        this.channels = channels.filter(channel => channel.fuelId);
        this.reset();
    }

    reset() {
        this.job = null;
        this.samples = Array(FUEL_POOL_SLOTS.length).fill(null);
        this.nextSlot = 0;
    }

    start(id, count = 1, immediate = false) {
        const channel = this.channels.find(channel => channel.fuelId === id);
        if (!channel) return false;
        this.job = { id, count, elapsed: 0, deposited: false, slotIndex: this.nextSlot,
            x: channel.x * CHANNEL_PITCH, z: channel.y * CHANNEL_PITCH };
        if (immediate) this.finish();
        return true;
    }

    deposit() {
        if (!this.job || this.job.deposited) return;
        const { id, count, slotIndex } = this.job;
        this.samples[slotIndex] = { id, count, slotIndex };
        this.nextSlot = (slotIndex + 1) % FUEL_POOL_SLOTS.length;
        this.job.deposited = true;
    }

    finish() {
        this.deposit();
        this.job = null;
    }

    cancel() { this.job = null; }

    advance(seconds) {
        if (!this.job || !Number.isFinite(seconds)) return;
        // Long frames must not fast-forward a lift after background suspension.
        this.job.elapsed += Math.max(0, Math.min(.1, seconds));
        if (this.job.elapsed >= 8.3) this.deposit();
        if (this.job.elapsed >= FUEL_TRANSFER_DURATION) this.finish();
    }

    get pose() {
        const layout = FUEL_HANDLING;
        const parked = { x: 0, z: layout.homeZ, grabberY: layout.parkedGrabberY,
            phase: 'Parked', carrying: false, openCap: false, sourceHidden: false };
        if (!this.job) return parked;
        const job = this.job, t = job.elapsed;
        const slot = FUEL_POOL_SLOTS[job.slotIndex];
        const pickupY = layout.coreFuelY + layout.assemblyHeight / 2 + layout.gripOffset;
        const dropY = layout.storedFuelY + layout.assemblyHeight / 2 + layout.gripOffset;
        const pose = { ...parked, sourceId: job.id, slotIndex: job.slotIndex, x: job.x, z: job.z };
        if (t < 1.2) {
            const f = ease(t / 1.2);
            return { ...pose, x: mix(0, job.x, f), z: mix(layout.homeZ, job.z, f), phase: 'Aligning over core' };
        }
        if (t < 2.6) return { ...pose, grabberY: mix(layout.parkedGrabberY, pickupY, ease((t - 1.2) / 1.4)),
            openCap: true, phase: 'Lowering grabber' };
        if (t < 4.6) return { ...pose, grabberY: mix(pickupY, layout.clearanceGrabberY, ease((t - 2.6) / 2)),
            carrying: true, sourceHidden: true, openCap: true, phase: 'Lifting assembly' };
        if (t < 6.5) {
            const f = ease((t - 4.6) / 1.9);
            return { ...pose, x: mix(job.x, slot.x, f), z: mix(job.z, slot.z, f),
                grabberY: layout.clearanceGrabberY, carrying: true, phase: 'Moving to pool' };
        }
        if (t < 8.3) return { ...pose, x: slot.x, z: slot.z,
            grabberY: mix(layout.clearanceGrabberY, dropY, ease((t - 6.5) / 1.8)),
            carrying: true, phase: 'Lowering into pool' };
        if (t < 9.5) return { ...pose, x: slot.x, z: slot.z,
            grabberY: mix(dropY, layout.parkedGrabberY, ease((t - 8.3) / 1.2)), phase: 'Retracting grabber' };
        const f = ease((t - 9.5) / 1);
        return { ...pose, x: mix(slot.x, 0, f), z: mix(slot.z, layout.homeZ, f), phase: 'Returning to park' };
    }
}
