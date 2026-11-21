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

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.AuthInterceptor;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.GlobalExceptionHandler;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.tuya.TuyaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TuyaControllerTest {
    private final TuyaService tuya = mock(TuyaService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        AuthContext context = new AuthContext();
        AuthService auth = mock(AuthService.class);
        when(auth.authenticate("user-token")).thenReturn(AccountEntity.builder().id(7L).build());
        when(auth.authenticate("demo-token")).thenReturn(AccountEntity.builder().id(8L).demo(true).build());
        mvc = MockMvcBuilders.standaloneSetup(new TuyaController(context, tuya))
                .setControllerAdvice(new GlobalExceptionHandler(mock(I18nService.class)))
                .addInterceptors(new AuthInterceptor(auth, context))
                .build();
    }

    @Test
    void cloudOperationsRequireAuthenticationAndBlockDemoWrites() throws Exception {
        mvc.perform(get("/api/tuya/connections")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tuya/devices/12/poll").header("Authorization", "demo-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tuya);
    }

    @Test
    void qrResponseIsNotCacheable() throws Exception {
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-11-21T10:02:00Z");
        when(tuya.beginLogin(7L, null, "Home", "user-code"))
                .thenReturn(new TuyaService.QrLogin(id, "scan-payload", expiresAt));

        mvc.perform(post("/api/tuya/login").header("Authorization", "user-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"label":"Home","userCode":"user-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.payload").value("scan-payload"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(jsonPath("$.userCode").doesNotExist());
    }

    @Test
    void importReturnsLocalDeviceIdAndPreservesDisabledSelection() throws Exception {
        when(tuya.importDevice(7L, 4L, "relay-1", "Heater", "Europe/Helsinki", false)).thenReturn(12L);
        mvc.perform(post("/api/tuya/devices").header("Authorization", "user-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"connectionId":4,"externalId":"relay-1","name":"Heater","timezone":"Europe/Helsinki","enabled":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId").value(12));
    }

    @Test
    void relayRequestDistinguishesExplicitOffFromMissingState() throws Exception {
        String path = "/api/tuya/devices/12/relays/switch_1";
        mvc.perform(post(path).header("Authorization", "user-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tuya);

        when(tuya.switchRelay(7L, 12L, "switch_1", false)).thenReturn(new TuyaService.Snapshot(true,
                Instant.parse("2026-11-21T10:00:00Z"),
                List.of(new TuyaService.Reading("switch_1", "false", "")), List.of("switch_1")));
        mvc.perform(post(path).header("Authorization", "user-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"on":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(true))
                .andExpect(jsonPath("$.readings[0].value").value("false"));
    }
}
