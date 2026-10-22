package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcStateService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevicesControllerHeatPumpStateTest {
    private final AuthContext auth = new AuthContext();
    private final DeviceService devices = mock(DeviceService.class);
    private final ToshibaAcStateService cloud = mock(ToshibaAcStateService.class);
    private final AcCommandDispatchService dispatch = mock(AcCommandDispatchService.class);
    private final DevicesController controller = new DevicesController(auth, devices, mock(ControlService.class),
            mock(HeatPumpAcDeviceSelectionService.class), cloud, mock(MitsubishiAcStateService.class), dispatch,
            mock(FactoryProvisioningService.class));
    private final DeviceAcDataEntity acData = DeviceAcDataEntity.builder().acType(AcType.TOSHIBA)
            .lastPolledStateHex("previous-settings").build();

    @BeforeEach
    void setup() {
        auth.setAccountId(1L);
        when(devices.getDeviceAcData(1L, 20L)).thenReturn(acData);
    }

    @AfterEach
    void cleanup() { auth.clear(); }

    @Test
    void cloud403PreservesDeviceTypeAndSavedStateWithoutClaimingFreshState() {
        when(cloud.getAcState(acData)).thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN,
                "Forbidden", HttpHeaders.EMPTY, new byte[0], null));

        var result = controller.getHeatPumpState(20L);

        assertThat(result.acType()).isEqualTo(AcType.TOSHIBA);
        assertThat(result.currentState()).isNull();
        assertThat(result.lastPolledState()).isEqualTo("previous-settings");
        assertThat(result.queryFailed()).isTrue();
        assertThat(result.queryStatus()).isEqualTo(403);
        verifyNoInteractions(dispatch);
    }

    @Test
    void cloudOutagePreservesSavedStateAndNoSavedStateIsNotInvented() {
        acData.setLastPolledStateHex(null);
        when(cloud.getAcState(acData)).thenThrow(new ResourceAccessException("Network unavailable"));

        var result = controller.getHeatPumpState(20L);

        assertThat(result.acType()).isEqualTo(AcType.TOSHIBA);
        assertThat(result.queryFailed()).isTrue();
        assertThat(result.queryStatus()).isNull();
        assertThat(result.currentState()).isNull();
        assertThat(result.lastPolledState()).isNull();
    }

    @Test
    void successfulQueryReturnsFreshStateWithoutAWarning() {
        var response = new ToshibaAcStateResponse();
        response.setSuccess(true);
        var state = new ToshibaAcStateResponse.ResObj();
        state.setAcStateData("fresh-settings");
        response.setResObj(state);
        when(cloud.getAcState(acData)).thenReturn(response);

        var result = controller.getHeatPumpState(20L);

        assertThat(result.currentState()).isEqualTo("fresh-settings");
        assertThat(result.queryFailed()).isFalse();
        assertThat(result.queryStatus()).isNull();
    }

    @Test
    void ownershipFailureIsNotConvertedIntoACloudWarningOrCachedResponse() {
        when(devices.getDeviceAcData(1L, 20L)).thenThrow(new IllegalArgumentException("Device not found"));

        assertThatThrownBy(() -> controller.getHeatPumpState(20L)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(cloud, dispatch);
    }
}
