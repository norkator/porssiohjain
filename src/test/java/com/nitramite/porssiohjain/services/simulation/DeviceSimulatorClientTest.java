package com.nitramite.porssiohjain.services.simulation;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.services.DeviceAcCommandLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DeviceSimulatorClientTest {
    private static final String HEX = "30431641313200101605FE0B00001002010000";
    private final DeviceSimulatorProperties properties = new DeviceSimulatorProperties();
    private final DeviceAcDataRepository repository = mock(DeviceAcDataRepository.class);
    private final DeviceAcCommandLogService logs = mock(DeviceAcCommandLogService.class);
    private DeviceSimulatorClient client;
    private MockRestServiceServer server;
    private DeviceAcDataEntity ac;
    private String url;

    @BeforeEach
    void setup() {
        properties.setBaseUrl("http://device-simulator:8080/");
        properties.setToken("private-service-token");
        client = new DeviceSimulatorClient(properties, repository, logs);
        var rest = new RestTemplate();
        ReflectionTestUtils.setField(client, "restTemplate", rest);
        server = MockRestServiceServer.bindTo(rest).build();
        var device = DeviceEntity.builder().id(4L).uuid(UUID.randomUUID()).simulated(true).enabled(true)
                .account(AccountEntity.builder().demo(true).build()).build();
        ac = DeviceAcDataEntity.builder().device(device).acType(AcType.TOSHIBA)
                .lastPolledStateHex("previous-observation").lastSentStateHex("previous-command").build();
        url = "http://device-simulator:8080/heat-pumps/" + device.getUuid();
    }

    @Test
    void acceptanceUpdatesSentStateButPreservesObservedState() {
        server.expect(requestTo(url + "/commands")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer private-service-token"))
                .andExpect(content().json("{\"stateHex\":\"" + HEX + "\"}"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accepted\":true}"));
        client.sendCommand(ac, HEX.toLowerCase());
        assertThat(ac.getLastSentStateHex()).isEqualTo(HEX);
        assertThat(ac.getLastPolledStateHex()).isEqualTo("previous-observation");
        verify(repository).save(ac);
        verify(logs).logSentCommand(eq(4L), contains("SIMULATED"));
        server.verify();
    }

    @Test
    void failedCommandPreservesLastSuccessfulSentState() {
        server.expect(requestTo(url + "/commands")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.sendCommand(ac, HEX)).isInstanceOf(RestClientException.class);
        assertThat(ac.getLastSentStateHex()).isEqualTo("previous-command");
        verifyNoInteractions(repository, logs);
        server.verify();
    }

    @Test
    void missingConfigurationAndNonDemoDevicesCannotUseSimulator() {
        properties.setBaseUrl("");
        assertThatThrownBy(() -> client.getState(ac)).isInstanceOf(RestClientException.class);
        properties.setBaseUrl("http://device-simulator:8080");
        ac.getDevice().getAccount().setDemo(false);
        assertThatThrownBy(() -> client.sendCommand(ac, HEX)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, logs);
        server.verify();
    }

    @Test
    void queriesReadBackStateWithServiceAuthentication() {
        server.expect(requestTo(url + "/state")).andExpect(header("Authorization", "Bearer private-service-token"))
                .andRespond(withSuccess("{\"stateHex\":\"" + HEX + "\",\"observedAt\":\"2026-11-04T12:00:00Z\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.getState(ac).stateHex()).isEqualTo(HEX);
        verifyNoInteractions(repository, logs);
        server.verify();
    }
}
