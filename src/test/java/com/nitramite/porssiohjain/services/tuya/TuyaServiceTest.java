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

package com.nitramite.porssiohjain.services.tuya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TuyaServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TuyaApiClient api = mock(TuyaApiClient.class);
    private final TuyaConnectionRepository connections = mock(TuyaConnectionRepository.class);
    private final TuyaDeviceRepository links = mock(TuyaDeviceRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final DeviceService deviceService = mock(DeviceService.class);
    private final DemoAccountGuard demo = mock(DemoAccountGuard.class);
    private TuyaService service;
    private TuyaConnectionEntity connection;
    private final TuyaSession session = new TuyaSession("code", "https://apigw.tuyaeu.com", "terminal", "user-1",
            "access-token", "refresh-token", Instant.now().plusSeconds(3600));

    @BeforeEach
    void setup() {
        service = new TuyaService(api, mapper, connections, links, accounts, devices, deviceService, demo);
        connection = new TuyaConnectionEntity();
        connection.setId(10L);
        connection.setAccount(AccountEntity.builder().id(7L).build());
        connection.setProviderUserId("user-1");
        when(accounts.findWithLockById(7L)).thenReturn(Optional.of(connection.getAccount()));
        when(connections.lockByAccountId(7L)).thenReturn(Optional.of(connection));
    }

    @Test
    void linksOnceAndRestoresSavedSessionInNewServiceInstance() throws Exception {
        linkAccount();
        assertTrue(connection.getSessionData().contains("refresh-token")); // JPA encrypts this entire field via CryptoConverter.
        TuyaService restarted = new TuyaService(api, mapper, connections, links, accounts, devices, deviceService, demo);
        when(api.get(any(), eq("/v1.0/m/life/users/homes"), anyMap()))
                .thenReturn(json("[{\"ownerId\":123,\"name\":\"Home\"}]"));
        assertEquals("123", restarted.homes(7L).getFirst().id());
        verify(api, times(1)).createQrCode("code");
        verify(api).get(argThat(restored -> restored.accessToken().equals(session.accessToken())
                && restored.refreshToken().equals(session.refreshToken())
                && restored.expiresAt().toEpochMilli() == session.expiresAt().toEpochMilli()), anyString(), anyMap());
        verify(api, never()).refresh(any());
    }

    @Test
    void preventsQrCompletionFromAnotherAccountOrReplacedAttempt() {
        when(api.createQrCode("code")).thenReturn("qr-token");
        var first = service.beginLogin(7L, "code");
        service.beginLogin(7L, "code");
        assertThrows(TuyaException.class, () -> service.finishLogin(7L, first.id()));
        when(accounts.findWithLockById(8L)).thenReturn(Optional.of(AccountEntity.builder().id(8L).build()));
        assertThrows(TuyaException.class, () -> service.finishLogin(8L, first.id()));
        verify(api, never()).loginResult(anyString(), anyString());
    }

    @Test
    void preventsReconnectingExistingDevicesToDifferentTuyaUser() throws Exception {
        when(api.createQrCode("code")).thenReturn("qr-token");
        var qr = service.beginLogin(7L, "code");
        when(api.loginResult("code", "qr-token")).thenReturn(json("{\"success\":true}"));
        when(api.sessionFromLogin(eq("code"), any())).thenReturn(new TuyaSession("code", session.endpoint(), "terminal",
                "other-user", "other-access", "other-refresh", session.expiresAt()));
        when(links.existsByConnectionId(10L)).thenReturn(true);
        assertEquals("tuya.error.accountChanged", assertThrows(TuyaException.class, () -> service.finishLogin(7L, qr.id())).getMessage());
        verify(connections, never()).save(any());
    }

    @Test
    void retainsRotatedRefreshTokenWhenSubsequentCloudReadFails() throws Exception {
        linkAccount();
        var saved = (com.fasterxml.jackson.databind.node.ObjectNode) json(connection.getSessionData());
        saved.put("expiresAt", Instant.now().minusSeconds(1).toEpochMilli());
        connection.setSessionData(saved.toString());
        when(api.refresh(any())).thenReturn(new TuyaSession("code", session.endpoint(), "terminal", "user-1",
                "new-access", "new-refresh", Instant.now().plusSeconds(3600)));
        when(api.get(any(), anyString(), anyMap())).thenThrow(new TuyaException("tuya.error.request"));
        assertThrows(TuyaException.class, () -> service.homes(7L));
        assertEquals("new-refresh", json(connection.getSessionData()).path("refreshToken").asText());
    }

    @Test
    void importsOwnedSensorWithAccountLimitsThroughExistingDeviceServiceAndRejectsDuplicates() throws Exception {
        linkAccount();
        when(api.get(any(), eq("/v1.0/m/life/ha/devices/detail"), anyMap())).thenReturn(json("""
                [{"id":"sensor-1","name":"Sensor","category":"wsdcg","status":[]}]
                """));
        when(api.get(any(), eq("/v1.1/m/life/sensor-1/specifications"), anyMap())).thenReturn(json("{\"status\":[]}"));
        when(deviceService.createDevice(eq(7L), eq(7L), eq("Kitchen"), eq("Europe/Helsinki"),
                eq(DeviceType.TEMPERATURE_SENSOR), eq(DevicePlatform.TUYA_SMART), eq(true),
                isNull(), eq(AcType.NONE), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(DeviceResponse.builder().id(21L).build());
        when(devices.findById(21L)).thenReturn(Optional.of(DeviceEntity.builder().id(21L).build()));
        service.importDevice(7L, "sensor-1", "Kitchen", "Europe/Helsinki", true);
        var captured = org.mockito.ArgumentCaptor.forClass(TuyaDeviceEntity.class);
        verify(links).save(captured.capture());
        assertEquals("sensor-1", captured.getValue().getTuyaDeviceId());
        assertSame(connection, captured.getValue().getConnection());
        when(links.existsByConnectionIdAndTuyaDeviceId(10L, "sensor-1")).thenReturn(true);
        assertEquals("tuya.error.duplicate", assertThrows(TuyaException.class,
                () -> service.importDevice(7L, "sensor-1", "Again", "Europe/Helsinki", true)).getMessage());
    }

    @Test
    void pollingCannotUseOtherAccountsDevice() {
        assertEquals("tuya.error.device", assertThrows(TuyaException.class, () -> service.poll(8L, 21L)).getMessage());
        verifyNoInteractions(api);
    }

    @Test
    void decodesScaleUnitsAndOptionalReadingsWithoutInventingMissingMetadata() throws Exception {
        var readings = service.decodeReadings(json("""
                {"status":[{"code":"va_temperature","value":-35},{"code":"temp_unit_convert","value":"c"},
                  {"code":"va_humidity","value":456},{"code":"battery_state","value":"low"},
                  {"code":"battery_percentage","value":92},{"code":"switch","value":true}]}
                """), json("""
                {"status":[{"code":"va_temperature","type":"Integer","values":"{\\"scale\\":1,\\"unit\\":\\"\\"}"},
                  {"code":"va_humidity","type":"Integer","values":{"scale":1,"unit":"%"}}]}
                """));
        var byCode = readings.stream().collect(java.util.stream.Collectors.toMap(TuyaService.Reading::code, r -> r));
        assertEquals("-3.5", byCode.get("va_temperature").value());
        assertEquals("°C", byCode.get("va_temperature").unit());
        assertEquals("45.6", byCode.get("va_humidity").value());
        assertEquals("low", byCode.get("battery_state").value());
        assertEquals("", byCode.get("battery_percentage").value());
        assertFalse(byCode.containsKey("switch"));
    }

    @Test
    void demoAccountCannotStartCloudLinking() {
        doThrow(new DemoAccountMutationException()).when(demo).assertWritable(7L);
        assertThrows(DemoAccountMutationException.class, () -> service.beginLogin(7L, "code"));
        verifyNoInteractions(api);
    }

    private void linkAccount() throws Exception {
        when(api.createQrCode("code")).thenReturn("qr-token");
        when(api.loginResult("code", "qr-token")).thenReturn(json("{\"success\":true,\"result\":{\"username\":\"My account\"}}"));
        when(api.sessionFromLogin(eq("code"), any())).thenReturn(session);
        var qr = service.beginLogin(7L, "code");
        service.finishLogin(7L, qr.id());
    }

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }
}
