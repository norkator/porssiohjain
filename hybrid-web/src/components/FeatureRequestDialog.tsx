/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
import { type FormEvent, useEffect, useRef, useState } from "react";
import AppDialog from "@/components/AppDialog";
import ProgressHeader from "@/components/ProgressHeader";
import { fetchMe } from "@/lib/account";
import { submitFeatureRequest } from "@/lib/feature-requests";
import { useI18n } from "@/lib/i18n";

export default function FeatureRequestDialog({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const { t } = useI18n("featureRequest");
  const [step, setStep] = useState(1);
  const [useCase, setUseCase] = useState("");
  const [changes, setChanges] = useState("");
  const [email, setEmail] = useState("");
  const [readOnly, setReadOnly] = useState(false);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  const emailEdited = useRef(false);
  const inFlight = useRef(false);

  useEffect(() => {
    if (!isOpen) return;
    let active = true;
    fetchMe().then((account) => {
      if (!active) return;
      setReadOnly(account.demo || account.impersonating);
      if (!emailEdited.current) setEmail(account.email ?? "");
    }).catch(() => { /* The contact field remains optional when account lookup fails. */ });
    return () => { active = false; };
  }, [isOpen]);

  useEffect(() => {
    if (!useCase && !changes) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [useCase, changes]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current) return;
    if (readOnly) { setError(t("readOnly")); return; }
    setError("");
    if (step < 3) {
      if (!(step === 1 ? useCase : changes).trim()) { setError(t("required")); return; }
      setStep(step + 1);
      return;
    }
    if (!useCase.trim() || !changes.trim()) { setError(t("invalid")); return; }
    inFlight.current = true;
    setSending(true);
    try {
      await submitFeatureRequest({ useCase: useCase.trim(), requestedChanges: changes.trim(), contactEmail: email.trim() });
      setUseCase("");
      setChanges("");
      setSent(true);
    } catch {
      setError(t("failed"));
    } finally {
      inFlight.current = false;
      setSending(false);
    }
  }

  function close() {
    if (sending) return;
    if (sent) { setSent(false); setStep(1); }
    onClose();
  }

  const inputClass = "w-full rounded-lg border border-outline-variant bg-surface-container-highest p-4 text-on-surface outline-none focus:border-primary";
  return (
    <AppDialog description={t("description")} isOpen={isOpen} isDismissible={!sending} maxWidthClassName="max-w-2xl" onClose={close} title={t("title")}>
      {sent ? (
        <div role="status" className="space-y-5">
          <p>{t("sent")}</p>
          <button className="primary-action px-4 py-3" type="button" onClick={close}>{t("close")}</button>
        </div>
      ) : (
        <form className="space-y-5" onSubmit={submit}>
          <ProgressHeader step={step} total={3} label={step === 1 ? t("useCase") : step === 2 ? t("changes") : t("review")} />
          {step < 3 ? (
            <div key={step}>
              <label className="mb-2 block text-sm font-bold" htmlFor="feature-request-answer">{step === 1 ? t("useCase") : t("changes")}</label>
              <p className="mb-3 text-sm text-on-surface-variant" id="feature-request-help">{step === 1 ? t("useCaseHelp") : t("changesHelp")}</p>
              <textarea autoFocus aria-describedby="feature-request-help" className={`${inputClass} min-h-48 resize-y`} id="feature-request-answer" maxLength={5000}
                onChange={(event) => step === 1 ? setUseCase(event.target.value) : setChanges(event.target.value)} required value={step === 1 ? useCase : changes} />
            </div>
          ) : (
            <div className="space-y-4">
              <dl className="space-y-2 rounded-lg bg-surface-container-high p-4">
                <dt className="font-bold">{t("useCase")}</dt><dd className="whitespace-pre-wrap break-words">{useCase}</dd>
                <dt className="font-bold">{t("changes")}</dt><dd className="whitespace-pre-wrap break-words">{changes}</dd>
              </dl>
              <label className="block text-sm font-bold" htmlFor="feature-request-email">{t("email")}</label>
              <input autoFocus className={inputClass} disabled={sending} id="feature-request-email" maxLength={254} type="email" value={email}
                aria-describedby="feature-request-email-help" onChange={(event) => { emailEdited.current = true; setEmail(event.target.value); }} />
              <p className="text-sm text-on-surface-variant" id="feature-request-email-help">{t("emailHelp")}</p>
            </div>
          )}
          {readOnly ? <p className="text-sm text-on-surface-variant">{t("readOnly")}</p> : null}
          {error ? <p role="alert" className="rounded-lg bg-error-container/50 p-3 text-on-error-container">{error}</p> : null}
          <div className="flex flex-wrap justify-end gap-3">
            {step > 1 ? <button className="secondary-action px-4 py-3" disabled={sending} onClick={() => { setStep(step - 1); setError(""); }} type="button">{t("back")}</button> : null}
            <button className="primary-action px-4 py-3 disabled:opacity-60" disabled={sending || readOnly} type="submit">{sending ? t("sending") : step < 3 ? t("next") : t("send")}</button>
          </div>
        </form>
      )}
    </AppDialog>
  );
}
