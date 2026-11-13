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

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.AuthInterceptor;
import com.nitramite.porssiohjain.contollers.WaterMeterMeasurementsController;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.WaterMeterWidgetQueryService.Type;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.WaterMeterWidgetQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WaterMeterMeasurementsControllerTest {
    private final AuthService auth = mock();
    private final AuthContext context = new AuthContext();
    private final WaterMeterWidgetQueryService query = mock();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new WaterMeterMeasurementsController(context, query))
                .addInterceptors(new AuthInterceptor(auth, context)).build();
        when(auth.authenticate("raw-token")).thenReturn(AccountEntity.builder().id(7L).build());
        when(auth.authenticate("Bearer raw-token")).thenThrow(new IllegalArgumentException("Invalid token"));
    }

    @AfterEach void clear() { context.clear(); }

    @Test
    void bothEndpointsRequireRawAccountAuthentication() throws Exception {
        for (String path : new String[]{"/api/measurements/water-meters/latest", "/api/measurements/water-meters/history"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer raw-token")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(query);
    }

    @Test
    void latestReturnsOnlySafeFieldsAndPreservesUnknownReportTime() throws Exception {
        var at = Instant.parse("2026-11-05T10:00:00Z");
        when(query.latest(7L)).thenReturn(List.of(new WaterMeterWidgetQueryService.Reading(12L, "Home water",
                Type.WATER_METER_TOTAL, "total_m3", "WATER_METER", "WRM_SYSTEMS_WMD", "m³",
                new BigDecimal("21.25"), at, null, at, false, null)));
        mvc.perform(get("/api/measurements/water-meters/latest").header("Authorization", "raw-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].deviceId").value(12))
                .andExpect(jsonPath("$[0].value").value(21.25)).andExpect(jsonPath("$[0].measuredAt").isEmpty())
                .andExpect(jsonPath("$[0].observedAt").value(at.toString()))
                .andExpect(jsonPath("$[0].account").doesNotExist()).andExpect(jsonPath("$[0].device").doesNotExist());
    }

    @Test
    void historyUsesAuthenticatedAccountAndExactSelection() throws Exception {
        mvc.perform(get("/api/measurements/water-meters/history").header("Authorization", "raw-token")
                .param("waterMeterId", "12").param("type", "WATER_CONSUMPTION").param("key", "daily_m3").param("hours", "48"))
                .andExpect(status().isOk());
        verify(query).history(eq(7L), eq(12L), eq(Type.WATER_CONSUMPTION), eq("daily_m3"), eq(48), any(Instant.class));
    }

    @Test
    void malformedParametersFailBeforeTheServiceRuns() throws Exception {
        mvc.perform(get("/api/measurements/water-meters/history").header("Authorization", "raw-token"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/measurements/water-meters/history").header("Authorization", "raw-token")
                .param("waterMeterId", "12").param("type", "UNKNOWN").param("key", "temperature"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(query);
    }
}
