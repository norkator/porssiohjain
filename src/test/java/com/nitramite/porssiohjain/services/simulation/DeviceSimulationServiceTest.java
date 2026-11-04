package com.nitramite.porssiohjain.services.simulation;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceSimulationServiceTest {
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final DeviceAcDataRepository acData = mock(DeviceAcDataRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
    private final AccountLimitService limits = mock(AccountLimitService.class);
    private final DeviceSimulationService service = new DeviceSimulationService(devices, acData, accounts,
            authorization, limits, mock(SystemLogService.class));
    private final AccountEntity demo = AccountEntity.builder().id(7L).demo(true).build();
    private final DeviceEntity relay = DeviceEntity.builder().id(9L).uuid(UUID.randomUUID()).account(demo)
            .deviceType(DeviceType.STANDARD).devicePlatform(DevicePlatform.GENERIC_MQTT).build();

    @BeforeEach
    void setup() throws Exception {
        when(authorization.requireAdmin(1L)).thenReturn(AccountEntity.builder().id(1L).admin(true).build());
        when(accounts.findById(7L)).thenReturn(Optional.of(demo));
        when(accounts.findWithLockById(7L)).thenReturn(Optional.of(demo));
        when(devices.findByIdAndAccount(9L, demo)).thenReturn(Optional.of(relay));
    }

    @Test
    void onlyAdminCanEnableSimulationOnDemoDevices() throws Exception {
        doThrow(new AccessDeniedException("Admin access required")).when(authorization).requireAdmin(2L);
        assertThatThrownBy(() -> service.setSimulated(2L, 7L, 9L, true)).isInstanceOf(AccessDeniedException.class);
        verify(devices, never()).save(any());
        demo.setDemo(false);
        assertThatThrownBy(() -> service.setSimulated(1L, 7L, 9L, true)).isInstanceOf(IllegalArgumentException.class);
        verify(devices, never()).save(any());
    }

    @Test
    void enablingClearsOldConnectivityAndCommandCaches() throws Exception {
        relay.setApiOnline(true);
        var data = DeviceAcDataEntity.builder().lastSentStateHex("old").lastPolledStateHex("old").build();
        when(acData.findByDevice(relay)).thenReturn(Optional.of(data));
        service.setSimulated(1L, 7L, 9L, true);
        assertThat(relay.isSimulated()).isTrue();
        assertThat(relay.isApiOnline()).isFalse();
        assertThat(data.getLastSentStateHex()).isNull();
        assertThat(data.getLastPolledStateHex()).isNull();
    }

    @Test
    void unsupportedIntegrationsAreRejectedAndExcludedFromInventory() throws Exception {
        relay.setDevicePlatform(DevicePlatform.TUYA_SMART);
        assertThatThrownBy(() -> service.setSimulated(1L, 7L, 9L, true)).isInstanceOf(IllegalArgumentException.class);
        when(devices.findBySimulatedTrueAndEnabledTrueAndAccountDemoTrueOrderByIdAsc()).thenReturn(List.of(relay));
        assertThat(service.inventory()).isEmpty();
    }

    @Test
    void creatingPumpEnforcesExistingDeviceQuotaBeforeSaving() {
        doThrow(new IllegalArgumentException("Device limit reached")).when(limits).assertCanCreateDevice(7L);
        assertThatThrownBy(() -> service.createHeatPump(1L, 7L)).hasMessage("Device limit reached");
        verify(devices, never()).save(any());
    }
}
