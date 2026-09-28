import { getUserPreview, stopUserPreview } from "@/lib/impersonation";
import { useI18n } from "@/lib/i18n";

export default function UserPreviewBanner() {
  const preview = getUserPreview();
  const { t } = useI18n("adminUsers");
  if (!preview) return null;
  return (
    <aside className="sticky top-0 z-50 flex flex-wrap items-center justify-between gap-3 bg-secondary-container px-4 py-3 text-on-secondary-container" role="status">
      <div>
        <p className="font-bold">{t("viewing", { user: preview.email || String(preview.accountId) })}</p>
        <p className="text-sm">{t("readOnly")}</p>
      </div>
      <button className="secondary-action px-4 py-2" onClick={stopUserPreview} type="button">{t("returnToAdmin")}</button>
    </aside>
  );
}
