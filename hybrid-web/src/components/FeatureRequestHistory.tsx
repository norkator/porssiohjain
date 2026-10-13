/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
import { useEffect, useRef, useState } from "react";
import { commentFeatureRequest, fetchFeatureRequest, fetchFeatureRequests, markFeatureRequestRead,
  type FeatureRequestDetail, type FeatureRequestPage } from "@/lib/feature-requests";
import { getCurrentIntlLocales, useI18n } from "@/lib/i18n";

export default function FeatureRequestHistory({ isOpen, readOnly, reloadVersion }: { isOpen: boolean; readOnly: boolean; reloadVersion: number }) {
  const { t, locale } = useI18n("featureRequest");
  const [refreshVersion, setRefreshVersion] = useState(0);
  const loadFailed = t("loadFailed");
  const [page, setPage] = useState(0);
  const [requests, setRequests] = useState<FeatureRequestPage>();
  const [detail, setDetail] = useState<FeatureRequestDetail>();
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const generation = useRef(0);
  const visible = useRef(isOpen);
  visible.current = isOpen;
  const inFlight = useRef(false);
  const date = (value: string) => new Date(value).toLocaleString(getCurrentIntlLocales());

  useEffect(() => {
    if (!Object.values(drafts).some(value => value.trim())) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [drafts]);

  useEffect(() => {
    if (!isOpen) return;
    const current = ++generation.current;
    setLoading(true);
    setError("");
    setDetail(undefined);
    fetchFeatureRequests(page).then(result => {
      if (current !== generation.current) return;
      if (page > 0 && !result.items.length) setPage(Math.max(0, result.totalPages - 1));
      else setRequests(result);
    }).catch(() => { if (current === generation.current) setError(loadFailed); })
      .finally(() => { if (current === generation.current) setLoading(false); });
    return () => { generation.current++; };
  }, [isOpen, page, locale, loadFailed, reloadVersion, refreshVersion]);

  async function openRequest(id: number) {
    const current = ++generation.current;
    setLoading(true);
    setError("");
    try {
      const result = await fetchFeatureRequest(id);
      if (current !== generation.current) return;
      setDetail(result);
      if (!readOnly) {
        await markFeatureRequestRead(id, result.comments[result.comments.length - 1]?.id ?? 0);
        if (current !== generation.current) return;
        const updated = await fetchFeatureRequests(page);
        if (current === generation.current) setRequests(updated);
      }
    } catch { if (current === generation.current) setError(t("loadFailed")); }
    finally { if (current === generation.current) setLoading(false); }
  }

  async function sendReply() {
    if (!detail || readOnly || inFlight.current) return;
    const message = (drafts[detail.id] ?? "").trim();
    if (!message || message.length > 5000) { setError(t("commentInvalid")); return; }
    const id = detail.id;
    const started = generation.current;
    inFlight.current = true;
    setSending(true);
    setError("");
    try {
      await commentFeatureRequest(id, message);
      setDrafts(values => ({ ...values, [id]: "" }));
      if (visible.current && generation.current === started) await openRequest(id);
    } catch { setError(t("failed")); }
    finally { inFlight.current = false; setSending(false); }
  }

  const action = "secondary-action px-4 py-2 disabled:opacity-60";
  return <section className="space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h3 className="font-bold">{t(detail ? "viewRequest" : "myRequests")}</h3>
      <button className={action} disabled={loading || sending} type="button" onClick={() => detail ? void openRequest(detail.id) : setRefreshVersion(value => value + 1)}>{t("refresh")}</button>
    </div>
    {loading ? <p role="status">{t("loading")}</p> : null}
    {error ? <p role="alert" className="rounded-lg bg-error-container/50 p-3 text-on-error-container">{error}</p> : null}
    {detail ? <div className="space-y-4">
      <button className={action} disabled={loading || sending} type="button" onClick={() => { generation.current++; setDetail(undefined); setError(""); }}>{t("backToRequests")}</button>
      <p className="text-sm">#{detail.id} · {date(detail.createdAt)} · {t(detail.handled ? "handled" : "open")}</p>
      <dl className="space-y-2 rounded-lg bg-surface-container-high p-4">
        <dt className="font-bold">{t("useCase")}</dt><dd className="whitespace-pre-wrap break-words">{detail.useCase}</dd>
        <dt className="font-bold">{t("changes")}</dt><dd className="whitespace-pre-wrap break-words">{detail.requestedChanges}</dd>
      </dl>
      <h4 className="font-bold">{t("comments")}</h4>
      {detail.comments.length === 0 ? <p>{t("noComments")}</p> : null}
      {detail.comments.map(comment => <article className="space-y-2 rounded-lg bg-surface-container-high p-4" key={comment.id}>
        <p className="text-sm font-bold">{t(comment.adminAuthor ? "adminAuthor" : "userAuthor")} · {date(comment.createdAt)}</p>
        <p className="whitespace-pre-wrap break-words">{comment.message}</p>
      </article>)}
      <form className="space-y-3" onSubmit={event => { event.preventDefault(); void sendReply(); }}>
        <label className="block font-bold" htmlFor="feature-request-reply">{t("reply")}</label>
        <textarea className="min-h-28 w-full rounded-lg border border-outline-variant bg-surface-container-highest p-4" disabled={sending || readOnly} id="feature-request-reply" maxLength={5000} required
          value={drafts[detail.id] ?? ""} onChange={event => setDrafts(values => ({ ...values, [detail.id]: event.target.value }))} />
        {readOnly ? <p>{t("readOnly")}</p> : null}
        <button className="primary-action px-4 py-3 disabled:opacity-60" disabled={sending || loading || readOnly} type="submit">{t(sending ? "sending" : "sendComment")}</button>
      </form>
    </div> : <>
      {!loading && requests?.items.length === 0 ? <p>{t("noRequests")}</p> : null}
      <div className="max-h-72 space-y-3 overflow-y-auto">
        {requests?.items.map(request => <button className="block w-full space-y-2 rounded-lg border border-outline-variant bg-surface-container-high p-4 text-left disabled:opacity-60" disabled={loading} key={request.id} type="button" onClick={() => void openRequest(request.id)}>
          <span className="block text-sm">#{request.id} · {date(request.createdAt)} · {t(request.handled ? "handled" : "open")}</span>
          <span className="block break-words">{request.requestedChanges.slice(0, 160)}</span>
          {request.unreadComments ? <span className="inline-block rounded bg-primary-container px-2 py-1 text-sm font-bold text-on-primary-container">{t("newComment")}</span> : null}
        </button>)}
      </div>
      <div className="flex gap-3">
        <button className={action} disabled={loading || page === 0} type="button" onClick={() => setPage(page - 1)}>{t("back")}</button>
        <button className={action} disabled={loading || !requests || page + 1 >= requests.totalPages} type="button" onClick={() => setPage(page + 1)}>{t("next")}</button>
      </div>
    </>}
  </section>;
}
