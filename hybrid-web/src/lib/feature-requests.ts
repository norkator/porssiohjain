/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
import { apiFetch, apiGetJson } from "@/lib/api";

export async function submitFeatureRequest(input: { useCase: string; requestedChanges: string; contactEmail: string }) {
  const response = await apiFetch("/api/feature-requests", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  if (!response.ok) throw new Error(`Request failed with status ${response.status}`);
}

export type FeatureRequestSummary = {
  id: number;
  requestedChanges: string;
  createdAt: string;
  handled: boolean;
  unreadComments: boolean;
};
export type FeatureRequestPage = { items: FeatureRequestSummary[]; page: number; totalPages: number; totalElements: number };
export type FeatureRequestDetail = Omit<FeatureRequestSummary, "unreadComments"> & {
  useCase: string;
  contactEmail: string | null;
  comments: { id: number; adminAuthor: boolean; message: string; createdAt: string }[];
};
export function fetchFeatureRequests(page = 0) {
  return apiGetJson<FeatureRequestPage>(`/api/feature-requests?page=${page}`);
}
export function fetchFeatureRequest(id: number) {
  return apiGetJson<FeatureRequestDetail>(`/api/feature-requests/${id}`);
}
async function postRequestAction(id: number, action: string, body: object) {
  const response = await apiFetch(`/api/feature-requests/${id}/${action}`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
  });
  if (!response.ok) throw new Error(`Request failed with status ${response.status}`);
}
export function commentFeatureRequest(id: number, message: string) {
  return postRequestAction(id, "comments", { message });
}
export function markFeatureRequestRead(id: number, seenCommentId: number) {
  return postRequestAction(id, "read", { seenCommentId });
}
