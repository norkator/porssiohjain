/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
import { apiFetch } from "@/lib/api";

export async function submitFeatureRequest(input: { useCase: string; requestedChanges: string; contactEmail: string }) {
  const response = await apiFetch("/api/feature-requests", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  if (!response.ok) throw new Error(`Request failed with status ${response.status}`);
}
