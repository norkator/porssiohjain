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

import { apiFetch, apiGetJson } from "@/lib/api";

export type ElectricityContractType = "ENERGY" | "TRANSFER";

export type ElectricityContract = {
  id: number;
  name: string;
  type: ElectricityContractType;
  basicFee: number | null;
  nightPrice: number | null;
  dayPrice: number | null;
  staticPrice: number | null;
  taxPercent: number | null;
  taxAmount: number | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type ElectricityContractPayload = {
  name: string;
  type: ElectricityContractType;
  basicFee: number | null;
  nightPrice: number | null;
  dayPrice: number | null;
  staticPrice: number | null;
  taxPercent: number | null;
  taxAmount: number | null;
};

async function postJson<T>(path: string, payload: unknown, method = "POST") {
  const response = await apiFetch(path, {
    body: payload === undefined ? undefined : JSON.stringify(payload),
    headers: { "Content-Type": "application/json" },
    method
  });

  if (!response.ok) throw new Error(`Request failed with status ${response.status}`);
  if (response.status === 204) return undefined as T;

  const text = await response.text();
  if (!text) return undefined as T;

  return JSON.parse(text) as T;
}

export async function fetchElectricityContracts(type?: ElectricityContractType) {
  const params = new URLSearchParams();

  if (type) {
    params.set("type", type);
  }

  const suffix = params.toString();

  return apiGetJson<ElectricityContract[]>(`/api/electricity-contracts${suffix ? `?${suffix}` : ""}`);
}

export function createElectricityContract(payload: ElectricityContractPayload) {
  return postJson<ElectricityContract>("/api/electricity-contracts", payload);
}

export function updateElectricityContract(contractId: number, payload: ElectricityContractPayload) {
  return postJson<ElectricityContract>(`/api/electricity-contracts/${contractId}`, payload, "PUT");
}
