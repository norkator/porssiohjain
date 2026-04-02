/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.enums.ControlMode;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.ControlSchedulerService;
import com.nitramite.porssiohjain.services.SystemLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlSchedulerServiceTest {

    @Mock
    private NordpoolRepository nordpoolRepository;
    @Mock
    private ControlRepository controlRepository;
    @Mock
    private ControlTableRepository controlTableRepository;
    @Mock
    private SystemLogService systemLogService;

    private ControlSchedulerService controlSchedulerService;

    @BeforeEach
    void setUp() {
        controlSchedulerService = new ControlSchedulerService(
                nordpoolRepository,
                controlRepository,
                controlTableRepository,
                systemLogService
        );
    }

    @Test
    void statusIsFinalWhenTomorrowPricesAvailable() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant tomorrowStart = startOfDay.plus(1, ChronoUnit.DAYS);
        Instant tomorrowEnd = startOfDay.plus(2, ChronoUnit.DAYS);

        ControlEntity control = new ControlEntity();
        control.setId(1L);
        control.setMode(ControlMode.MANUAL);
        control.setTaxPercent(BigDecimal.ZERO);
        control.setTimezone("UTC");

        NordpoolEntity price = new NordpoolEntity();
        price.setDeliveryStart(startOfDay);
        price.setDeliveryEnd(startOfDay.plus(1, ChronoUnit.HOURS));
        price.setPriceFi(BigDecimal.TEN);

        when(controlRepository.findAll()).thenReturn(List.of(control));
        when(nordpoolRepository.existsByDeliveryStartBetween(tomorrowStart, tomorrowEnd)).thenReturn(true);
        when(nordpoolRepository.findByDeliveryStartBetween(any(), any())).thenReturn(List.of(price));

        controlSchedulerService.generatePlannedForTomorrow();

        ArgumentCaptor<ControlTableEntity> captor = ArgumentCaptor.forClass(ControlTableEntity.class);
        verify(controlTableRepository, atLeastOnce()).save(captor.capture());
        assertEquals(Status.FINAL, captor.getValue().getStatus());
    }

    @Test
    void statusIsPlannedWhenTomorrowPricesNotAvailable() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant tomorrowStart = startOfDay.plus(1, ChronoUnit.DAYS);
        Instant tomorrowEnd = startOfDay.plus(2, ChronoUnit.DAYS);

        ControlEntity control = new ControlEntity();
        control.setId(1L);
        control.setMode(ControlMode.MANUAL);
        control.setTaxPercent(BigDecimal.ZERO);
        control.setTimezone("UTC");

        NordpoolEntity price = new NordpoolEntity();
        price.setDeliveryStart(startOfDay);
        price.setDeliveryEnd(startOfDay.plus(1, ChronoUnit.HOURS));
        price.setPriceFi(BigDecimal.TEN);

        when(controlRepository.findAll()).thenReturn(List.of(control));
        when(nordpoolRepository.existsByDeliveryStartBetween(tomorrowStart, tomorrowEnd)).thenReturn(false);
        when(nordpoolRepository.findByDeliveryStartBetween(any(), any())).thenReturn(List.of(price));

        controlSchedulerService.generatePlannedForTomorrow();

        ArgumentCaptor<ControlTableEntity> captor = ArgumentCaptor.forClass(ControlTableEntity.class);
        verify(controlTableRepository, atLeastOnce()).save(captor.capture());
        assertEquals(Status.PLANNED, captor.getValue().getStatus());
    }

    @Test
    void statusIsPlannedForGenerateForControlWhenTomorrowPricesNotAvailable() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant tomorrowStart = startOfDay.plus(1, ChronoUnit.DAYS);
        Instant tomorrowEnd = startOfDay.plus(2, ChronoUnit.DAYS);

        ControlEntity control = new ControlEntity();
        control.setId(1L);
        control.setMode(ControlMode.MANUAL);
        control.setTaxPercent(BigDecimal.ZERO);
        control.setTimezone("UTC");

        NordpoolEntity price = new NordpoolEntity();
        price.setDeliveryStart(startOfDay);
        price.setDeliveryEnd(startOfDay.plus(1, ChronoUnit.HOURS));
        price.setPriceFi(BigDecimal.TEN);

        when(controlRepository.findById(1L)).thenReturn(Optional.of(control));
        when(nordpoolRepository.existsByDeliveryStartBetween(tomorrowStart, tomorrowEnd)).thenReturn(false);
        when(nordpoolRepository.findByDeliveryStartBetween(any(), any())).thenReturn(List.of(price));

        controlSchedulerService.generateForControl(1L);

        ArgumentCaptor<ControlTableEntity> captor = ArgumentCaptor.forClass(ControlTableEntity.class);
        verify(controlTableRepository, atLeastOnce()).save(captor.capture());
        assertEquals(Status.PLANNED, captor.getValue().getStatus());
    }

}
