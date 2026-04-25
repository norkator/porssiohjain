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

import { apiGetJson } from "@/lib/api";

export type ElectricityContractType = "ENERGY" | "TRANSFER";

export type ElectricityContract = {
  id: number;
  name: string;
  type: ElectricityContractType;
};

export async function fetchElectricityContracts(type?: ElectricityContractType) {
  const params = new URLSearchParams();

  if (type) {
    params.set("type", type);
  }

  const suffix = params.toString();

  return apiGetJson<ElectricityContract[]>(`/api/electricity-contracts${suffix ? `?${suffix}` : ""}`);
}
