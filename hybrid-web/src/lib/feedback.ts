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

import { apiFetch } from "@/lib/api";

export type FeedbackPayload = {
  contactEmail: string;
  feedback: string;
};

async function readError(response: Response) {
  const text = await response.text();

  return text || `Request failed with status ${response.status}`;
}

export async function sendFeedback(payload: FeedbackPayload) {
  const response = await apiFetch("/api/feedback", {
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }
}
