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
    <div className="signature-gradient flex h-9 w-9 items-center justify-center rounded-lg shadow-lg shadow-primary/15 sm:h-11 sm:w-11 sm:rounded-xl">
      <span className="font-headline text-sm font-extrabold uppercase tracking-[-0.08em] text-on-primary sm:text-base">EC</span>
    </div>
  );
}
