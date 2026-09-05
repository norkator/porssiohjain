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
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.enums.ControlMode;
import com.nitramite.porssiohjain.services.ControlChartService;
import com.nitramite.porssiohjain.services.ControlNotificationService;
import com.nitramite.porssiohjain.services.ControlSchedulerService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.ControlResponse;
import com.nitramite.porssiohjain.services.models.CreateControlRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControlsControllerTest {

    private final AuthContext authContext = new AuthContext();
    private final ControlService controlService = mock(ControlService.class);
    private final ControlSchedulerService controlSchedulerService = mock(ControlSchedulerService.class);
    private final ControlsController controller = new ControlsController(
            authContext,
            controlService,
            mock(ControlChartService.class),
            controlSchedulerService,
            mock(ControlNotificationService.class)
    );

    @AfterEach
    void tearDown() {
        authContext.clear();
    }

    @Test
    void createControlGeneratesControlTableForCreatedControl() {
        authContext.setAccountId(1L);

        CreateControlRequest request = new CreateControlRequest();
        request.setName("Water heater");
        request.setTimezone("Europe/Helsinki");
        request.setMaxPriceSnt(BigDecimal.valueOf(12.5));
        request.setMinPriceSnt(BigDecimal.valueOf(0.5));
        request.setDailyOnMinutes(180);
        request.setTaxPercent(BigDecimal.valueOf(25.5));
        request.setMode(ControlMode.CHEAPEST_HOURS_TOMORROW_AWARE);
        request.setManualOn(false);
        request.setAlwaysOnBelowMinPrice(true);
        request.setEnergyContractId(2L);
        request.setTransferContractId(3L);
        request.setSiteId(4L);

        ControlEntity createdControl = ControlEntity.builder()
                .id(10L)
                .name("Water heater")
                .build();
        ControlResponse response = ControlResponse.builder()
                .id(10L)
                .name("Water heater")
                .build();

        when(controlService.createControl(
                eq(1L),
                eq("Water heater"),
                eq("Europe/Helsinki"),
                eq(BigDecimal.valueOf(12.5)),
                eq(BigDecimal.valueOf(0.5)),
                eq(180),
                eq(BigDecimal.valueOf(25.5)),
                eq(ControlMode.CHEAPEST_HOURS_TOMORROW_AWARE),
                eq(false),
                eq(true),
                eq(2L),
                eq(3L),
                eq(4L)
        )).thenReturn(createdControl);
        when(controlService.getControl(1L, 10L)).thenReturn(response);

        ControlResponse createdResponse = controller.createControl(request);

        assertEquals(10L, createdResponse.getId());
        verify(controlSchedulerService).generateForControl(10L);
    }
}
