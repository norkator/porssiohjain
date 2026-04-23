import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import HeaderLogo from "@/components/HeaderLogo";
import { loginWithCredentials } from "@/lib/auth";
import { getSessionData } from "@/lib/session";

const DEMO_UUID = "78b7823f-d5cc-4376-8910-cd62e7b32400";
const DEMO_SECRET = "103058b63f9245099d0c30d81e1636bc";

export default function LoginView() {
  const navigate = useNavigate();
  const session = getSessionData();
  const [uuid, setUuid] = useState("");
  const [secret, setSecret] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (session.source === "android" || session.hasToken) {
    return <Navigate replace to="/menu" />;
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await loginWithCredentials({ uuid: uuid.trim(), secret: secret.trim() });
      navigate("/menu", { replace: true });
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : "Login failed.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const fillDemoAccount = () => {
    setUuid(DEMO_UUID);
    setSecret(DEMO_SECRET);
  };

  return (
    <main className="flex min-h-screen items-center justify-center px-6 py-10">
      <section className="app-card w-full max-w-md p-8">
        <div className="mb-8 flex items-center gap-4">
          <HeaderLogo />
          <div>
            <p className="metric-label">Energy Controller</p>
            <h1 className="font-headline text-3xl font-extrabold text-primary-container">Login</h1>
          </div>
        </div>

        <form className="space-y-5" onSubmit={handleSubmit}>
          <label className="block">
            <span className="mb-2 block font-label text-sm font-bold text-on-surface-variant">UUID</span>
            <input
              autoComplete="username"
              className="w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-4 py-3 font-mono text-sm outline-none transition-colors focus:border-primary"
              onChange={(event) => setUuid(event.target.value)}
              required
              type="text"
              value={uuid}
            />
          </label>

          <label className="block">
            <span className="mb-2 block font-label text-sm font-bold text-on-surface-variant">Secret</span>
            <input
              autoComplete="current-password"
              className="w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-4 py-3 font-mono text-sm outline-none transition-colors focus:border-primary"
              onChange={(event) => setSecret(event.target.value)}
              required
              type="password"
              value={secret}
            />
          </label>

          {error ? (
            <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
              Login failed: {error}
            </div>
          ) : null}

          <button className="primary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isSubmitting} type="submit">
            {isSubmitting ? "Logging in..." : "Login"}
          </button>
        </form>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-4 text-sm">
          <button className="font-label font-bold text-primary-container underline" onClick={fillDemoAccount} type="button">
            Use demo account
          </button>
          <Link className="font-label font-bold text-primary-container underline" to="/create-account">
            Create account
          </Link>
        </div>
      </section>
    </main>
  );
}
