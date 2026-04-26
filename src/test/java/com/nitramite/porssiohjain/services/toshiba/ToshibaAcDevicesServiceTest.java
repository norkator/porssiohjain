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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ToshibaAcDevicesServiceTest {

    private static final String VALID_CONSUMER_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void invalidConsumerIdReturnsEmptyListWithoutHttpRequest() {
        ToshibaAcDevicesService service = new ToshibaAcDevicesService();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);

        DeviceAcDataEntity acData = DeviceAcDataEntity.builder()
                .acAccessToken("token")
                .acConsumerId("https://example.test/internal")
                .build();
        acData.setId(42L);

        List<ToshibaAcMappingResponse.AcDevice> devices = service.getAcDevices(acData);

        assertTrue(devices.isEmpty());
        server.verify();
    }

    @Test
    void validConsumerIdFetchesAndFlattensAcDevices() {
        ToshibaAcDevicesService service = new ToshibaAcDevicesService();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        String responseJson = """
                {
                  "IsSuccess": true,
                  "ResObj": [
                    {
                      "GroupId": "group-1",
                      "GroupName": "Home",
                      "ConsumerId": "123e4567-e89b-12d3-a456-426614174000",
                      "ACList": [
                        {
                          "Id": "ac-1",
                          "DeviceUniqueId": "unique-1",
                          "Name": "Living room",
                          "ACStateData": "state"
                        }
                      ]
                    }
                  ]
                }
                """;
        server.expect(once(), requestTo(
                        "https://mobileapi.toshibahomeaccontrols.com/api/AC/GetConsumerACMapping?consumerId="
                                + VALID_CONSUMER_ID
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
        DeviceAcDataEntity acData = DeviceAcDataEntity.builder()
                .acAccessToken("token")
                .acConsumerId(VALID_CONSUMER_ID)
                .build();

        List<ToshibaAcMappingResponse.AcDevice> devices = service.getAcDevices(acData);

        assertEquals(1, devices.size());
        assertEquals("ac-1", devices.getFirst().getId());
        server.verify();
    }

    @Test
    void consumerIdValidationOnlyAllowsUuidFormat() {
        assertTrue(ToshibaAcDevicesService.isValidConsumerId(VALID_CONSUMER_ID));
        assertTrue(ToshibaAcDevicesService.isValidConsumerId("123E4567-E89B-12D3-A456-426614174000"));
        assertTrue(ToshibaAcDevicesService.isValidConsumerId("00000000-0000-0000-0000-000000000000"));

        assertFalse(ToshibaAcDevicesService.isValidConsumerId(null));
        assertFalse(ToshibaAcDevicesService.isValidConsumerId(""));
        assertFalse(ToshibaAcDevicesService.isValidConsumerId("123e4567e89b12d3a456426614174000"));
        assertFalse(ToshibaAcDevicesService.isValidConsumerId("123e4567-e89b-12d3-a456-426614174000/../metadata"));
    }
}
