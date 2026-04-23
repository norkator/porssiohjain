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

/// <reference types="vite/client" />

type DevSessionApi = {
  clear: () => unknown;
  setBaseUrl: (baseUrl: string) => unknown;
  setToken: (token: string) => unknown;
  show: () => unknown;
};

interface Window {
  devSession?: DevSessionApi;
}
