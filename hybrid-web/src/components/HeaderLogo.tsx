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

export default function HeaderLogo() {
  return (
    <div className="signature-gradient flex h-11 w-11 items-center justify-center rounded-xl shadow-lg shadow-primary/15">
      <span className="font-headline text-base font-extrabold uppercase tracking-[-0.08em] text-on-primary">EC</span>
    </div>
  );
}
