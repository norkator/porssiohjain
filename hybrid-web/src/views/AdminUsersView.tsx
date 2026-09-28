import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate } from "react-router-dom";
import PageHeader from "@/components/PageHeader";
import { fetchMe } from "@/lib/account";
import { fetchAdminUsers, previewAdminUser, type AdminUsersPage, type AdminUser } from "@/lib/admin-users";
import { getUserPreview, startUserPreview } from "@/lib/impersonation";
import { useI18n } from "@/lib/i18n";

export default function AdminUsersView() {
  const { t, locale } = useI18n("adminUsers");
  const [adminId, setAdminId] = useState<number | null>(null);
  const [denied, setDenied] = useState(false);
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<AdminUsersPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [previewing, setPreviewing] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    fetchMe().then((account) => {
      if (!active) return;
      if (!account.admin || account.impersonating) setDenied(true);
      else setAdminId(account.accountId);
    }).catch(() => {
      if (active) { setError(t("loadFailed")); setLoading(false); }
    });
    return () => { active = false; };
  }, [reload]);

  useEffect(() => {
    if (adminId === null) return;
    let active = true;
    setLoading(true);
    setError(null);
    setResult(null);
    fetchAdminUsers(query, page).then((users) => {
      if (active) setResult(users);
    }).catch(() => {
      if (active) setError(t("loadFailed"));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [adminId, query, page, reload]);

  if (denied || getUserPreview()) return <Navigate replace to="/menu" />;

  function searchUsers(event: FormEvent) {
    event.preventDefault();
    setQuery(search.trim());
    setPage(0);
  }

  async function viewUser(user: AdminUser) {
    if (adminId === null) return;
    setPreviewing(user.id);
    setError(null);
    try {
      const target = await previewAdminUser(user.id);
      startUserPreview({ accountId: target.id, email: target.email, adminAccountId: adminId });
    } catch {
      setError(t("previewFailed"));
      setPreviewing(null);
    }
  }

  function date(value: string | null) {
    return value ? new Date(value).toLocaleString(locale) : "—";
  }

  return (
    <>
      <PageHeader title={t("title")} />
      <main className="mx-auto max-w-7xl space-y-6 px-4 py-6 sm:px-6">
        <Link className="secondary-action inline-flex px-4 py-2" to="/menu">{t("back")}</Link>
        <section className="app-card space-y-5 p-5 sm:p-8">
          <div>
            <h1 className="font-headline text-2xl font-bold text-primary-container">{t("title")}</h1>
            <p className="mt-2 text-on-surface-variant">{t("description")}</p>
          </div>
          <form className="flex flex-wrap items-end gap-3" onSubmit={searchUsers}>
            <label className="flex min-w-0 flex-1 flex-col gap-2 font-bold" htmlFor="admin-user-search">
              {t("search")}
              <input className="rounded-lg bg-surface-container-highest px-4 py-3 font-normal" id="admin-user-search"
                maxLength={200} onChange={(event) => setSearch(event.target.value)} value={search} />
            </label>
            <button className="primary-action px-4 py-3" type="submit">{t("searchButton")}</button>
          </form>
          {error ? <div className="rounded-lg bg-error-container p-4 text-on-error-container" role="alert">
            <p>{error}</p><button className="secondary-action mt-3 px-4 py-2" onClick={() => setReload((value) => value + 1)} type="button">{t("retry")}</button>
          </div> : null}
          {loading ? <p role="status">{t("loading")}</p> : null}
          {result ? <>
            <p className="text-sm text-on-surface-variant">{t("count", { count: result.totalElements })}</p>
            {result.users.length === 0 ? <p>{t("empty")}</p> : null}
            <div className="grid gap-4 lg:grid-cols-2">
              {result.users.map((user) => (
                <article className="min-w-0 rounded-xl bg-surface-container-low p-5" key={user.id}>
                  <h2 className="break-all font-headline text-lg font-bold">{user.email || t("noEmail")}</h2>
                  <p className="mt-1 text-sm text-on-surface-variant">{t("accountId", { id: user.id })} · {user.tier} · {user.admin ? t("admin") : t("user")} · {user.blocked ? t("blocked") : t("allowed")}</p>
                  <dl className="my-4 grid gap-2 text-sm">
                    <div><dt className="font-bold">UUID</dt><dd className="break-all text-on-surface-variant">{user.uuid}</dd></div>
                    <div><dt className="font-bold">{t("created")}</dt><dd className="text-on-surface-variant">{date(user.createdAt)}</dd></div>
                    <div><dt className="font-bold">{t("updated")}</dt><dd className="text-on-surface-variant">{date(user.updatedAt)}{user.lastActivitySource ? ` · ${user.lastActivitySource}` : ""}</dd></div>
                  </dl>
                  <button className="primary-action px-4 py-3 disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={user.admin || user.id === adminId || previewing !== null} onClick={() => void viewUser(user)} type="button">
                    {previewing === user.id ? t("opening") : t("viewAsUser")}
                  </button>
                </article>
              ))}
            </div>
            <nav aria-label={t("pagination")} className="flex flex-wrap items-center justify-between gap-3">
              <button className="secondary-action px-4 py-2 disabled:opacity-50" disabled={page === 0} onClick={() => setPage((value) => value - 1)} type="button">{t("previous")}</button>
              <span>{t("page", { page: result.page + 1, total: Math.max(result.totalPages, 1) })}</span>
              <button className="secondary-action px-4 py-2 disabled:opacity-50" disabled={page + 1 >= result.totalPages} onClick={() => setPage((value) => value + 1)} type="button">{t("next")}</button>
            </nav>
          </> : null}
        </section>
      </main>
    </>
  );
}
