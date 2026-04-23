/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

export default function ProgressHeader({
  step,
  total,
  label
}: {
  step: number;
  total: number;
  label: string;
}) {
  const progress = `${(step / total) * 100}%`;

  return (
    <section className="mb-10">
      <div className="mb-4 flex items-end justify-between">
        <div>
          <span className="metric-label">{`Step ${step} of ${total}`}</span>
          <h2 className="mt-1 font-headline text-2xl font-extrabold text-on-surface md:text-3xl">{label}</h2>
        </div>
        <div className="font-headline text-lg font-bold text-primary-container">{Math.round((step / total) * 100)}%</div>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-container-highest">
        <div className="h-full rounded-full bg-primary-container" style={{ width: progress }} />
      </div>
    </section>
  );
}
