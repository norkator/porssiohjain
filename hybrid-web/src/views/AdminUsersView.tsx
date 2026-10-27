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
            <div aria-label={t("title")} className="overflow-x-auto rounded-lg border border-outline-variant" role="region" tabIndex={0}>
              <table className="w-full table-fixed border-collapse text-left text-sm sm:table-auto">
                <thead className="bg-surface-container-high text-xs text-on-surface-variant">
                  <tr>
                    <th className="px-3 py-2 font-semibold" scope="col">{t("account")}</th>
                    <th className="hidden px-3 py-2 font-semibold sm:table-cell" scope="col">{t("tier")}</th>
                    <th className="hidden px-3 py-2 text-right font-semibold sm:table-cell" scope="col">{t("devices")}</th>
                    <th className="hidden px-3 py-2 font-semibold md:table-cell" scope="col">{t("status")}</th>
                    <th className="w-20 px-3 py-2 text-right font-semibold sm:w-auto" scope="col">{t("actions")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant bg-surface-container-lowest">
                  {result.users.map((user) => (
                    <tr className="align-top" key={user.id}>
                      <td className="px-3 py-2">
                        <p className="max-w-xs truncate font-semibold" title={user.email || t("noEmail")}>{user.email || t("noEmail")}</p>
                        <div className="flex flex-wrap items-baseline gap-x-3 text-xs text-on-surface-variant">
                          <span>{t("accountId", { id: user.id })}</span>
                          <details>
                            <summary className="cursor-pointer rounded-sm hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary">{t("details")}</summary>
                            <dl className="space-y-1 py-2">
                              <div className="sm:hidden"><dt className="font-semibold">{t("tier")}</dt><dd>{user.tier} · {user.admin ? t("admin") : t("user")}</dd></div>
                              <div className="sm:hidden"><dt className="font-semibold">{t("devices")}</dt><dd>{user.deviceCount}</dd></div>
                              <div className="md:hidden"><dt className="font-semibold">{t("status")}</dt><dd>{user.blocked ? t("blocked") : t("allowed")}</dd></div>
                              <div><dt className="font-semibold">UUID</dt><dd className="break-all">{user.uuid}</dd></div>
                              <div><dt className="font-semibold">{t("created")}</dt><dd>{date(user.createdAt)}</dd></div>
                              <div><dt className="font-semibold">{t("updated")}</dt><dd>{date(user.updatedAt)}{user.lastActivitySource ? ` · ${user.lastActivitySource}` : ""}</dd></div>
                            </dl>
                          </details>
                        </div>
                      </td>
                      <td className="hidden whitespace-nowrap px-3 py-2 sm:table-cell">
                        {user.tier}
                        <p className="text-xs text-on-surface-variant">{user.admin ? t("admin") : t("user")}</p>
                      </td>
                      <td className="hidden px-3 py-2 text-right tabular-nums sm:table-cell">{user.deviceCount}</td>
                      <td className="hidden whitespace-nowrap px-3 py-2 text-xs md:table-cell">
                        <span className={user.blocked ? "font-semibold text-on-error-container" : "text-on-surface-variant"}>{user.blocked ? t("blocked") : t("allowed")}</span>
                      </td>
                      <td className="px-3 py-2 text-right">
                        <button aria-label={`${t("viewAsUser")} · ${user.email || user.id}`} className="secondary-action whitespace-nowrap px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-50"
                          disabled={user.admin || user.id === adminId || previewing !== null} onClick={() => void viewUser(user)} type="button">
                          <span className="sm:hidden">{previewing === user.id ? t("openingShort") : t("viewAsUserShort")}</span>
                          <span className="hidden sm:inline">{previewing === user.id ? t("opening") : t("viewAsUser")}</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
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
