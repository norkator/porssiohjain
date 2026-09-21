/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 */

import { apiGetJson } from "@/lib/api";

export type ServiceNotice = {
  active: boolean;
  text: string;
  updatedAt: string | null;
};

export function fetchServiceNotice(locale: string) {
  return apiGetJson<ServiceNotice>(`/api/service-notice?locale=${encodeURIComponent(locale)}`);
}
