import { getUserPreview, stopUserPreview } from "@/lib/impersonation";
import { useI18n } from "@/lib/i18n";

export default function UserPreviewBanner() {
  const preview = getUserPreview();
  const { t } = useI18n("adminUsers");
  if (!preview) return null;
  return (
    <aside className="sticky top-0 z-50 flex h-11 min-w-0 items-center justify-between gap-2 bg-secondary-container px-3 text-xs text-on-secondary-container sm:px-6 sm:text-sm" role="status">
      <p className="min-w-0 truncate font-semibold" title={t("viewing", { user: preview.email || String(preview.accountId) })}>
        {t("viewing", { user: preview.email || String(preview.accountId) })}
      </p>
      <button aria-label={t("returnToAdmin")} className="secondary-action min-h-8 shrink-0 px-2 py-1 text-xs sm:px-3 sm:text-sm" onClick={stopUserPreview} type="button">
        <svg aria-hidden="true" className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M9 14 4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3" />
        </svg>
        <span className="sm:hidden">{t("returnToAdminShort")}</span>
        <span className="hidden sm:inline">{t("returnToAdmin")}</span>
      </button>
    </aside>
  );
}
