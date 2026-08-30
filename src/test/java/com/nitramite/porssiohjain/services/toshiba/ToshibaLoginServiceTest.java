/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.toshiba;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.services.models.AcLoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ToshibaLoginServiceTest {

    @Test
    void loginReusesStillValidTokenWithoutHttpRequest() {
        ToshibaLoginService service = newServiceWithMockServer();
        DeviceAcDataEntity acData = DeviceAcDataEntity.builder()
                .acUsername("user@example.com")
                .acPassword("password")
                .acAccessToken("existing-token")
                .acTokenExpiresAt(Instant.now().plusSeconds(3600))
                .build();

        AcLoginResponse response = service.login(acData);

        assertTrue(response.isSuccess());
        assertEquals("existing-token", response.getAccessToken());
    }

    @Test
    void tooManyRequestsSuppressesImmediateNextLoginAttempt() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ToshibaLoginService service = new ToshibaLoginService(mock(DeviceAcDataRepository.class));
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        server.expect(once(), requestTo("https://mobileapi.toshibahomeaccontrols.com/api/Consumer/Login"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"resObj":null,"isSuccess":false,"message":"Too many requests. Try again in 60 seconds.","statusCode":null}
                                """));
        DeviceAcDataEntity acData = DeviceAcDataEntity.builder()
                .acUsername("user@example.com")
                .acPassword("password")
                .build();

        AcLoginResponse first = service.login(acData);
        AcLoginResponse second = service.login(acData);

        assertFalse(first.isSuccess());
        assertFalse(second.isSuccess());
        server.verify();
    }

    private ToshibaLoginService newServiceWithMockServer() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer.bindTo(restTemplate).build();
        ToshibaLoginService service = new ToshibaLoginService(mock(DeviceAcDataRepository.class));
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        return service;
    }
}
