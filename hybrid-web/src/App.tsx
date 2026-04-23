import { Navigate, Route, Routes } from "react-router-dom";
import AppShell from "@/layouts/AppShell";
import ScrollToTop from "@/components/ScrollToTop";
import AddDeviceConfigureView from "@/views/AddDeviceConfigureView";
import AddDeviceIntegrationView from "@/views/AddDeviceIntegrationView";
import AddDeviceReviewView from "@/views/AddDeviceReviewView";
import AddDeviceTypeView from "@/views/AddDeviceTypeView";
import AddControlView from "@/views/AddControlView";
import ControlsView from "@/views/ControlsView";
import CreateAccountView from "@/views/CreateAccountView";
import DevicesView from "@/views/DevicesView";
import LoginView from "@/views/LoginView";
import MainMenuView from "@/views/MainMenuView";
import ManageControlView from "@/views/ManageControlView";
import ManageDeviceView from "@/views/ManageDeviceView";
import ManagePowerLimitView from "@/views/ManagePowerLimitView";
import ManageProductionSourceView from "@/views/ManageProductionSourceView";
import ManageWeatherControlView from "@/views/ManageWeatherControlView";
import PowerLimitsView from "@/views/PowerLimitsView";
import ProductionSourcesView from "@/views/ProductionSourcesView";
import WeatherControlsView from "@/views/WeatherControlsView";
import { getSessionData } from "@/lib/session";

function AndroidSessionUnavailable() {
  const session = getSessionData();

  return (
    <main className="flex min-h-screen items-center justify-center px-6 py-10">
      <section className="app-card max-w-md p-8">
        <p className="metric-label mb-3">Android Session</p>
        <h1 className="mb-4 font-headline text-3xl font-extrabold text-primary-container">Session unavailable</h1>
        <p className="text-on-surface-variant">
          The Android app did not provide a login token for this WebView session.
          {session.error ? ` ${session.error}` : ""}
        </p>
      </section>
    </main>
  );
}

function ProtectedAppRoutes() {
  const session = getSessionData();

  if (!session.hasToken) {
    if (session.source === "android") {
      return <AndroidSessionUnavailable />;
    }

    return <Navigate replace to="/login" />;
  }

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/menu" replace />} />
      <Route path="/menu" element={<MainMenuView />} />
      <Route path="/devices" element={<DevicesView />} />
      <Route path="/devices/:deviceId" element={<ManageDeviceView />} />
      <Route path="/devices/add/type" element={<AddDeviceTypeView />} />
      <Route path="/devices/add/configure" element={<AddDeviceConfigureView />} />
      <Route path="/devices/add/review" element={<AddDeviceReviewView />} />
      <Route path="/devices/add/integration" element={<AddDeviceIntegrationView />} />
      <Route path="/controls" element={<ControlsView />} />
      <Route path="/controls/add" element={<AddControlView />} />
      <Route path="/controls/:controlId" element={<ManageControlView />} />
      <Route path="/weather-controls" element={<WeatherControlsView />} />
      <Route path="/weather-controls/:weatherControlId" element={<ManageWeatherControlView />} />
      <Route path="/production-sources" element={<ProductionSourcesView />} />
      <Route path="/production-sources/:sourceId" element={<ManageProductionSourceView />} />
      <Route path="/power-limits" element={<PowerLimitsView />} />
      <Route path="/power-limits/:powerLimitId" element={<ManagePowerLimitView />} />
      <Route path="*" element={<Navigate to="/menu" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <AppShell>
      <ScrollToTop />
      <Routes>
        <Route path="/login" element={<LoginView />} />
        <Route path="/create-account" element={<CreateAccountView />} />
        <Route path="/*" element={<ProtectedAppRoutes />} />
      </Routes>
    </AppShell>
  );
}
