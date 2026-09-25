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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ProductionHistoryEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import com.nitramite.porssiohjain.entity.enums.ResourceType;
import com.nitramite.porssiohjain.entity.repository.ProductionHistoryRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceRepository;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import com.nitramite.porssiohjain.services.models.ProductionHistoryResponse;
import com.nitramite.porssiohjain.services.models.ProductionSourceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductionSourceServiceTest {

    @Mock
    private ProductionSourceRepository productionSourceRepository;

    @Mock
    private ProductionHistoryRepository productionHistoryRepository;

    @Mock
    private ResourceSharingRepository resourceSharingRepository;

    private ProductionSourceService service;

    @BeforeEach
    void setUp() {
        service = new ProductionSourceService(
                productionSourceRepository, null, null, null, productionHistoryRepository,
                null, null, resourceSharingRepository, null, null
        );
    }

    @Test
    void demoAccountGetsRollingHistoryForEveryOwnedSource() {
        AccountEntity demo = account(1L, "78b7823f-d5cc-4376-8910-cd62e7b32400");
        ProductionSourceEntity firstSource = source(10L, demo);
        ProductionSourceEntity secondSource = source(11L, demo);
        secondSource.setPeakKw(BigDecimal.valueOf(8));
        when(productionSourceRepository.findByIdAndAccountId(10L, 1L)).thenReturn(Optional.of(firstSource));
        when(productionSourceRepository.findByIdAndAccountId(11L, 1L)).thenReturn(Optional.of(secondSource));

        List<ProductionHistoryResponse> first = service.getProductionHistory(1L, 10L, 24);
        List<ProductionHistoryResponse> second = service.getProductionHistory(1L, 11L, 24);

        assertEquals(96, first.size());
        assertEquals(96, second.size());
        assertEquals(BigDecimal.ZERO, first.stream().map(ProductionHistoryResponse::getKilowatts)
                .min(BigDecimal::compareTo).orElseThrow().stripTrailingZeros());
        assertTrue(first.stream().map(ProductionHistoryResponse::getKilowatts)
                .max(BigDecimal::compareTo).orElseThrow().signum() > 0);
        assertTrue(second.stream().map(ProductionHistoryResponse::getKilowatts)
                .max(BigDecimal::compareTo).orElseThrow().compareTo(
                        first.stream().map(ProductionHistoryResponse::getKilowatts)
                                .max(BigDecimal::compareTo).orElseThrow()) > 0);
        assertTrue(first.getFirst().getCreatedAt().isBefore(first.getLast().getCreatedAt()));
        verifyNoInteractions(productionHistoryRepository);
    }

    @Test
    void otherAccountsKeepRecordedHistory() {
        AccountEntity account = account(2L, "bdf6db0b-9df1-4772-85f8-29535b29254b");
        ProductionSourceEntity source = source(20L, account);
        ProductionHistoryEntity reading = new ProductionHistoryEntity();
        reading.setCreatedAt(Instant.now().minusSeconds(60));
        reading.setKilowatts(BigDecimal.valueOf(2));
        when(productionSourceRepository.findByIdAndAccountId(20L, 2L)).thenReturn(Optional.of(source));
        when(productionHistoryRepository.findAllByProductionSource(source)).thenReturn(List.of(reading));

        List<ProductionHistoryResponse> history = service.getProductionHistory(2L, 20L, 24);

        assertEquals(1, history.size());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(history.getFirst().getKilowatts()));
    }

    @Test
    void demoSourceSummaryUsesGeneratedHistoryWithoutChangingStoredValues() {
        AccountEntity demo = account(1L, "78b7823f-d5cc-4376-8910-cd62e7b32400");
        ProductionSourceEntity source = source(10L, demo);
        when(productionSourceRepository.findByAccountId(1L)).thenReturn(List.of(source));

        ProductionSourceResponse response = service.getAllSources(1L).getFirst();

        assertTrue(response.getPeakKw().signum() > 0);
        assertTrue(response.getCurrentKw().signum() >= 0);
        assertEquals(BigDecimal.ZERO, source.getPeakKw());
        assertEquals(BigDecimal.ZERO, source.getCurrentKw());
        verifyNoInteractions(productionHistoryRepository);
    }

    @Test
    void sharedDemoSourceKeepsRecordedHistoryForAnotherAccount() {
        AccountEntity demo = account(1L, "78b7823f-d5cc-4376-8910-cd62e7b32400");
        ProductionSourceEntity source = source(10L, demo);
        when(resourceSharingRepository.existsByReceiverAccountIdAndResourceTypeAndProductionSourceIdAndEnabledTrue(
                2L, ResourceType.PRODUCTION_SOURCE, 10L)).thenReturn(true);
        when(productionSourceRepository.findById(10L)).thenReturn(Optional.of(source));

        assertTrue(service.getProductionHistory(2L, 10L, 24).isEmpty());
        verify(productionHistoryRepository).findAllByProductionSource(source);
    }

    private AccountEntity account(Long id, String uuid) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setUuid(UUID.fromString(uuid));
        return account;
    }

    private ProductionSourceEntity source(Long id, AccountEntity account) {
        ProductionSourceEntity source = new ProductionSourceEntity();
        source.setId(id);
        source.setAccount(account);
        source.setTimezone("Europe/Helsinki");
        return source;
    }
}
