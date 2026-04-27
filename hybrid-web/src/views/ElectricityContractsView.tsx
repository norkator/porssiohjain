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
        <section className="mb-10">
          <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">{t("title")}</h1>
          <p className="max-w-2xl text-lg text-on-surface-variant">{t("description")}</p>
        </section>

        <form className="app-card mb-8 grid gap-4 p-6 md:grid-cols-2 lg:grid-cols-4" onSubmit={handleSubmit}>
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
          <button className="primary-action justify-center disabled:opacity-60 lg:col-span-3" disabled={isSaving || !name.trim()} type="submit">{isSaving ? (editingContractId === null ? common("creating") : common("save")) : editingContractId === null ? t("add") : t("update")}</button>
          {editingContractId !== null ? <button className="secondary-action justify-center" onClick={resetForm} type="button">{common("cancel")}</button> : null}
        </form>

        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">{t("loading")}</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && contracts.map((contract) => (
            <article className="group app-card border-l-4 border-primary p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft" key={contract.id}>
              <div className="mb-5 flex justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-white">{contractTypeLabel(contract.type)}</span>
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
        </section>
      </main>
    </>
  );
}
