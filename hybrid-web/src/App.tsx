import { Navigate, Route, Routes } from "react-router-dom";
import AppShell from "@/layouts/AppShell";
import ScrollToTop from "@/components/ScrollToTop";
import AddDeviceConfigureView from "@/views/AddDeviceConfigureView";
import AddDeviceIntegrationView from "@/views/AddDeviceIntegrationView";
import AddDeviceReviewView from "@/views/AddDeviceReviewView";
import AddDeviceTypeView from "@/views/AddDeviceTypeView";
import AddControlView from "@/views/AddControlView";
import ControlsView from "@/views/ControlsView";
import DevicesView from "@/views/DevicesView";
import MainMenuView from "@/views/MainMenuView";
import ManageControlView from "@/views/ManageControlView";
import ManageDeviceView from "@/views/ManageDeviceView";

export default function App() {
  return (
    <AppShell>
      <ScrollToTop />
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
      </Routes>
    </AppShell>
  );
}
