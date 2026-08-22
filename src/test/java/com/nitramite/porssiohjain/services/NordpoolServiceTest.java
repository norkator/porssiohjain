/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NordpoolServiceTest {

    @Mock NordpoolRepository nordpoolRepository;
    @Mock ControlRepository controlRepository;
    @Mock AccountRepository accountRepository;

    @Test
    void defaultControlPriceRangeUsesControlLocalMidnightInSummer() {
        AccountEntity account = new AccountEntity();
        account.setMarketIndexName("FI");
        ControlEntity control = ControlEntity.builder()
                .id(1L)
                .account(account)
                .timezone("Europe/Helsinki")
                .taxPercent(java.math.BigDecimal.ZERO)
                .build();
        when(controlRepository.findById(1L)).thenReturn(Optional.of(control));
        when(nordpoolRepository.findPricesBetween(
                "FI",
                Instant.parse("2026-08-21T21:00:00Z"),
                Instant.parse("2026-08-23T20:59:59.999999999Z")
        )).thenReturn(List.of());

        NordpoolService service = new NordpoolService(
                nordpoolRepository,
                controlRepository,
                accountRepository
        );

        service.getNordpoolPricesForControl(
                1L,
                null,
                null,
                Instant.parse("2026-08-22T12:00:00Z")
        );

        verify(nordpoolRepository).findPricesBetween(
                "FI",
                Instant.parse("2026-08-21T21:00:00Z"),
                Instant.parse("2026-08-23T20:59:59.999999999Z")
        );
    }
}
