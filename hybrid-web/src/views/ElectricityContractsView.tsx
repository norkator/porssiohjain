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

import PageHeader from "@/components/PageHeader";
import AppDialog from "@/components/AppDialog";
import {
  createElectricityContract,
  fetchElectricityContracts,
  updateElectricityContract,
  type ElectricityContract,
  type ElectricityContractPayload,
  type ElectricityContractType
} from "@/lib/electricity-contracts";
import { formatDate } from "@/lib/automation-resources";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";

const CONTRACT_TYPES: ElectricityContractType[] = ["ENERGY", "TRANSFER"];

function contractTypeLabel(type: ElectricityContractType) {
  return type === "ENERGY" ? "Energy" : "Transfer";
}

function numberValue(value: string) {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export default function ElectricityContractsView() {
  const { t } = useI18n("electricityContractsView");
  const common = useI18n("common").t;
  const [contracts, setContracts] = useState<ElectricityContract[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingContractId, setEditingContractId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [type, setType] = useState<ElectricityContractType>("ENERGY");
  const [basicFee, setBasicFee] = useState("");
  const [nightPrice, setNightPrice] = useState("");
  const [dayPrice, setDayPrice] = useState("");
  const [staticPrice, setStaticPrice] = useState("");
  const [taxPercent, setTaxPercent] = useState("");
  const [taxAmount, setTaxAmount] = useState("");
  const [isStaticPricing, setIsStaticPricing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isFormDialogOpen, setIsFormDialogOpen] = useState(false);

  async function loadContracts() {
    setIsLoading(true);
    setError(null);
    try {
      setContracts(await fetchElectricityContracts());
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t("failedLoad"));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadContracts();
  }, []);

  function resetForm() {
    setEditingContractId(null);
    setName("");
    setType("ENERGY");
    setBasicFee("");
    setNightPrice("");
    setDayPrice("");
    setStaticPrice("");
    setTaxPercent("");
    setTaxAmount("");
    setIsStaticPricing(false);
  }

  function openCreateDialog() {
    resetForm();
    setError(null);
    setIsFormDialogOpen(true);
  }

  function startEdit(contract: ElectricityContract) {
    setEditingContractId(contract.id);
    setName(contract.name);
    setType(contract.type);
    setBasicFee(contract.basicFee?.toString() ?? "");
    setNightPrice(contract.nightPrice?.toString() ?? "");
    setDayPrice(contract.dayPrice?.toString() ?? "");
    setStaticPrice(contract.staticPrice?.toString() ?? "");
    setTaxPercent(contract.taxPercent?.toString() ?? "");
    setTaxAmount(contract.taxAmount?.toString() ?? "");
    setIsStaticPricing(contract.staticPrice !== null);
    setError(null);
    setIsFormDialogOpen(true);
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) return;

    const payload: ElectricityContractPayload = {
      basicFee: numberValue(basicFee),
      dayPrice: isStaticPricing ? null : numberValue(dayPrice),
      name: name.trim(),
      nightPrice: isStaticPricing ? null : numberValue(nightPrice),
      staticPrice: isStaticPricing ? numberValue(staticPrice) : null,
      taxAmount: numberValue(taxAmount),
      taxPercent: numberValue(taxPercent),
      type
    };

    setIsSaving(true);
    setError(null);
    try {
      if (editingContractId === null) {
        await createElectricityContract(payload);
      } else {
        await updateElectricityContract(editingContractId, payload);
      }
      resetForm();
      setIsFormDialogOpen(false);
      await loadContracts();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("failedSave"));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} translucent />
      <main className="app-page pt-4 sm:pt-12">
        <section className="mb-12 flex flex-col gap-8 md:flex-row md:items-end md:justify-between">
          <div className="max-w-2xl">
            <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">{t("title")}</h1>
            <p className="max-w-2xl text-lg text-on-surface-variant">{t("description")}</p>
          </div>

          <button
            className="primary-action transition-all duration-300 hover:-translate-y-0.5 hover:shadow-soft"
            onClick={openCreateDialog}
            type="button"
          >
            <span>+</span>
            {t("addNewContract")}
          </button>
        </section>

        {isLoading ? <div className="app-card p-4 text-sm text-on-surface-variant sm:p-6">{t("loading")}</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container sm:p-6">{error}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && contracts.map((contract) => (
            <article className="group app-card border-l-4 border-primary p-4 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft sm:p-6" key={contract.id}>
              <div className="mb-5 flex justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-surface-container-lowest">{contractTypeLabel(contract.type)}</span>
                <span className="rounded px-2 py-1 text-[10px] font-bold bg-primary-fixed text-primary">{contract.staticPrice !== null ? t("staticBadge") : t("variableBadge")}</span>
              </div>
              <h3 className="font-headline text-2xl font-bold">{contract.name}</h3>
              <p className="mb-6 mt-1 font-mono text-xs text-outline">{common("id", { id: contract.id })}</p>
              <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{t("energyPrice")}</span><p className="font-semibold">{contract.staticPrice ?? contract.dayPrice ?? contract.nightPrice ?? "-"}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{t("taxPercent")}</span><p className="font-semibold">{contract.taxPercent ?? "-"}%</p></div>
              </div>
              <div className="flex items-center justify-between border-t border-surface-container-low pt-4">
                <span className="text-sm text-on-surface-variant">{formatDate(contract.updatedAt)}</span>
                <button className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" onClick={() => startEdit(contract)} type="button">{common("edit")}</button>
              </div>
            </article>
          ))}

          <button
            className="group flex flex-col items-center justify-center gap-4 rounded-xl border-2 border-dashed border-outline-variant bg-surface-container-low p-4 text-center transition-all duration-300 hover:-translate-y-1 hover:border-primary hover:bg-surface-container-high hover:shadow-soft sm:p-6"
            onClick={openCreateDialog}
            type="button"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-container-highest font-headline text-xl font-black text-primary transition-all duration-300 group-hover:scale-110 group-hover:bg-surface-container-lowest">
              +
            </div>
            <div>
              <h3 className="font-headline text-lg font-bold text-on-surface">{t("createContract")}</h3>
              <p className="px-8 text-xs text-on-surface-variant">{t("createContractDescription")}</p>
            </div>
          </button>
        </section>
      </main>

      <AppDialog
        description={editingContractId === null ? t("createContractDescription") : t("updateContractDescription")}
        eyebrow={editingContractId === null ? t("createContractEyebrow") : t("updateContractEyebrow")}
        isOpen={isFormDialogOpen}
        maxWidthClassName="max-w-5xl"
        onClose={() => {
          setIsFormDialogOpen(false);
          resetForm();
          setError(null);
        }}
        title={editingContractId === null ? t("createContract") : t("update")}
      >
        <form className="grid gap-4 md:grid-cols-2 lg:grid-cols-4" onSubmit={handleSubmit}>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setName(event.target.value)} placeholder={t("name")} value={name} />
          <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setType(event.target.value as ElectricityContractType)} value={type}>
            {CONTRACT_TYPES.map((item) => <option key={item} value={item}>{contractTypeLabel(item)}</option>)}
          </select>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" min="0" onChange={(event) => setBasicFee(event.target.value)} placeholder={t("basicFee")} step="0.01" type="number" value={basicFee} />
          <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">{t("staticPricing")}</span><input checked={isStaticPricing} onChange={(event) => setIsStaticPricing(event.target.checked)} type="checkbox" /></label>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none disabled:opacity-50" disabled={isStaticPricing} min="0" onChange={(event) => setNightPrice(event.target.value)} placeholder={t("nightPrice")} step="0.000001" type="number" value={nightPrice} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none disabled:opacity-50" disabled={isStaticPricing} min="0" onChange={(event) => setDayPrice(event.target.value)} placeholder={t("dayPrice")} step="0.000001" type="number" value={dayPrice} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none disabled:opacity-50" disabled={!isStaticPricing} min="0" onChange={(event) => setStaticPrice(event.target.value)} placeholder={t("staticPrice")} step="0.000001" type="number" value={staticPrice} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" min="0" onChange={(event) => setTaxPercent(event.target.value)} placeholder={t("taxPercent")} step="0.01" type="number" value={taxPercent} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" min="0" onChange={(event) => setTaxAmount(event.target.value)} placeholder={t("taxAmount")} step="0.000001" type="number" value={taxAmount} />

          {error ? (
            <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container md:col-span-2 lg:col-span-4">
              {error}
            </div>
          ) : null}

          <div className="flex flex-col-reverse gap-3 md:col-span-2 lg:col-span-4 sm:flex-row sm:justify-end">
            <button
              className="secondary-action justify-center"
              onClick={() => {
                setIsFormDialogOpen(false);
                resetForm();
                setError(null);
              }}
              type="button"
            >
              {common("cancel")}
            </button>
            <button className="primary-action justify-center disabled:opacity-60" disabled={isSaving || !name.trim()} type="submit">
              {isSaving ? (editingContractId === null ? common("creating") : common("save")) : editingContractId === null ? t("add") : t("update")}
            </button>
          </div>
        </form>
      </AppDialog>

      <button
        className="signature-gradient fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full text-3xl text-on-primary shadow-xl transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_20px_40px_rgba(0,67,66,0.22)] active:scale-90 md:hidden"
        onClick={openCreateDialog}
        type="button"
      >
        +
      </button>
    </>
  );
}
