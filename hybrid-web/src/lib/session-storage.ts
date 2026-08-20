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

const APPLICATION_SESSION_STORAGE_PREFIXES = ["porssiohjain.", "energy-controller."];

export function clearApplicationSessionStorage() {
  if (typeof window === "undefined") {
    return;
  }

  try {
    const keysToRemove: string[] = [];

    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index);

      if (key && APPLICATION_SESSION_STORAGE_PREFIXES.some((prefix) => key.startsWith(prefix))) {
        keysToRemove.push(key);
      }
    }

    keysToRemove.forEach((key) => window.sessionStorage.removeItem(key));
  } catch {
    // Storage can be unavailable in restricted WebViews; logout must still continue.
  }
}
