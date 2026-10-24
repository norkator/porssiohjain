package com.nitramite.porssiohjain.services.toshiba;

import com.nitramite.porssiohjain.services.proxy.OutboundProxyTransport;
import com.nitramite.porssiohjain.services.proxy.OutboundProxyProperties;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.DeviceOfflineNotificationService;
import com.nitramite.porssiohjain.services.models.AcLoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ToshibaAcStateServiceTest {
    private static final String URL = "https://mobileapi.toshibahomeaccontrols.com/api/AC/GetCurrentACState?ACId=pump-id";
    private final ToshibaLoginService login = mock(ToshibaLoginService.class);
    private final ToshibaAcStateService service = new ToshibaAcStateService(login, new ToshibaAcStateHexDecoderService(),
            mock(DeviceAcDataRepository.class), mock(DeviceRepository.class), mock(DeviceOfflineNotificationService.class), new OutboundProxyTransport(new OutboundProxyProperties()));
    private final DeviceAcDataEntity data = DeviceAcDataEntity.builder().acDeviceId("pump-id")
            .acAccessToken("old-token").lastPolledStateHex("previous-settings").build();

    private MockRestServiceServer server() {
        var rest = new RestTemplate();
        ReflectionTestUtils.setField(service, "restTemplate", rest);
        when(login.refreshLogin(data)).thenReturn(AcLoginResponse.builder().success(true).accessToken("renewed-token").build());
        return MockRestServiceServer.bindTo(rest).build();
    }

    @Test
    void vendor403RefreshesLoginAndRetriesWithRenewedToken() {
        var server = server();
        server.expect(requestTo(URL)).andExpect(header("Authorization", "Bearer old-token"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(URL)).andExpect(header("Authorization", "Bearer renewed-token"))
                .andRespond(withSuccess("""
                        {"IsSuccess":true,"ResObj":{"ACId":"pump-id","ACStateData":"30431641313200101605fe0b00001002010000"}}
                        """, MediaType.APPLICATION_JSON));

        var result = service.getAcState(data);

        assertThat(result.isSuccess()).isTrue();
        assertThat(data.getLastPolledStateHex()).isEqualTo(result.getResObj().getAcStateData());
        verify(login, times(1)).refreshLogin(data);
        server.verify();
    }

    @Test
    void repeatedVendor403StopsAfterOneRefreshAndPreservesPreviouslyPolledState() {
        var server = server();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.getAcState(data)).isInstanceOf(HttpClientErrorException.Forbidden.class);

        assertThat(data.getLastPolledStateHex()).isEqualTo("previous-settings");
        verify(login, times(1)).refreshLogin(data);
        server.verify();
    }
}
