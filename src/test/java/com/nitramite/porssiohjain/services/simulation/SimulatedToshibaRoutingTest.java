package com.nitramite.porssiohjain.services.simulation;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.DeviceOfflineNotificationService;
import com.nitramite.porssiohjain.services.proxy.OutboundProxyTransport;
import com.nitramite.porssiohjain.services.toshiba.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulatedToshibaRoutingTest {
    private final DeviceSimulatorClient client = mock(DeviceSimulatorClient.class);
    private final DeviceEntity device = DeviceEntity.builder().id(3L).uuid(UUID.randomUUID())
            .simulated(true).enabled(true).apiOnline(true).lastCommunication(Instant.now().minusSeconds(30)).build();
    private final DeviceAcDataEntity data = DeviceAcDataEntity.builder().device(device).acType(AcType.TOSHIBA)
            .acDeviceId("demo").lastPolledStateHex("previous-observation").build();

    @Test
    void commandDispatchUsesSimulatorWithoutRegisteringOrSendingToManufacturer() {
        var registration = mock(ToshibaRegisterControllerService.class);
        var amqp = mock(ToshibaAcAmqpSendService.class);
        var dispatcher = new ToshibaAcCommandDispatcher(registration, amqp, client);
        doThrow(new ResourceAccessException("Simulator down")).when(client).sendCommand(data, "state");

        assertThatThrownBy(() -> dispatcher.dispatchHexState(data, "state")).isInstanceOf(ResourceAccessException.class);

        verify(client).sendCommand(data, "state");
        verifyNoInteractions(registration, amqp);
    }

    @Test
    void stateReadDecodesMockStateAndFailurePreservesLastSuccessfulRead() {
        var login = mock(ToshibaLoginService.class);
        var cloud = mock(OutboundProxyTransport.class);
        var devices = mock(DeviceRepository.class);
        var acData = mock(DeviceAcDataRepository.class);
        when(devices.findWithAccountById(3L)).thenReturn(Optional.of(device));
        var service = new ToshibaAcStateService(login, new ToshibaAcStateHexDecoderService(), acData,
                devices, mock(DeviceOfflineNotificationService.class), cloud, client);
        clearInvocations(cloud); // Constructor obtains the real transport's RestTemplate, but makes no request.
        Instant observedAt = Instant.now();
        String hex = "30431641313200101605FE0B00001002010000";
        when(client.getState(data)).thenReturn(new DeviceSimulatorClient.State(hex, observedAt));

        var result = service.getAcState(data);
        assertThat(result.getResObj().getDecodedAcState().isValid()).isTrue();
        assertThat(data.getLastPolledStateHex()).isEqualTo(hex);
        assertThat(device.getLastCommunication()).isEqualTo(observedAt);
        when(client.getState(data)).thenThrow(new ResourceAccessException("Simulator down"));

        assertThatThrownBy(() -> service.getAcState(data)).isInstanceOf(ResourceAccessException.class);
        assertThat(device.isApiOnline()).isFalse();
        assertThat(device.getLastCommunication()).isEqualTo(observedAt);
        assertThat(data.getLastPolledStateHex()).isEqualTo(hex);
        verifyNoInteractions(login, cloud);
    }
}
