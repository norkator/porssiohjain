/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.enums.ControlMode;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlSchedulerServiceTest {

    private static final ZoneId CONTROL_ZONE = ZoneId.of("Europe/Helsinki");
    private static final Instant NOW = Instant.parse("2026-01-15T13:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    @Mock NordpoolRepository nordpoolRepository;
    @Mock ControlRepository controlRepository;
    @Mock ControlTableRepository controlTableRepository;
    @Mock SystemLogService systemLogService;
    @Mock ControlPriceService controlPriceService;

    private ControlSchedulerService service;
    private ControlEntity control;

    @BeforeEach
    void setUp() {
        service = new ControlSchedulerService(
                nordpoolRepository,
                controlRepository,
                controlTableRepository,
                systemLogService,
                controlPriceService
        );
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setMarketIndexName("FI");
        control = ControlEntity.builder()
                .id(2L)
                .account(account)
                .name("Water heater")
                .timezone(CONTROL_ZONE.getId())
                .maxPriceSnt(new BigDecimal("100"))
                .minPriceSnt(new BigDecimal("6.4"))
                .dailyOnMinutes(240)
                .mode(ControlMode.CHEAPEST_HOURS_TOMORROW_AWARE)
                .alwaysOnBelowMinPrice(true)
                .build();

        when(controlRepository.findById(2L)).thenReturn(java.util.Optional.of(control));
        org.mockito.Mockito.lenient()
                .when(controlPriceService.getCombinedPrice(eq(control), any(NordpoolEntity.class)))
                .thenAnswer(invocation -> invocation.<NordpoolEntity>getArgument(1).getPriceFi());
    }

    @Test
    void keepsElapsedTodayQuotaWhenTomorrowIsCheaper() {
        control.setAlwaysOnBelowMinPrice(false);
        List<NordpoolEntity> prices = new ArrayList<>();
        prices.addAll(pricesForDay(TODAY, ignored -> "20"));
        prices.addAll(pricesForDay(TODAY.plusDays(1), ignored -> "1"));
        when(nordpoolRepository
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        eq("FI"), any(), any()))
                .thenReturn(prices);

        service.generateForControl(2L, NOW);

        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(32);
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY))).hasSize(16);
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY.plusDays(1)))).hasSize(16);
    }

    @Test
    void doesNotMoveTomorrowsBaseQuotaIntoTodayWhenTodayIsCheaper() {
        control.setAlwaysOnBelowMinPrice(false);
        List<NordpoolEntity> prices = new ArrayList<>();
        prices.addAll(pricesForDay(TODAY, ignored -> "1"));
        prices.addAll(pricesForDay(TODAY.plusDays(1), ignored -> "20"));
        when(nordpoolRepository
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        eq("FI"), any(), any()))
                .thenReturn(prices);

        service.generateForControl(2L, NOW);

        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY))).hasSize(16);
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY.plusDays(1)))).hasSize(16);
    }

    @Test
    void movesOnlyFutureTodayMinutesForWhichTomorrowHasCheaperCapacity() {
        control.setAlwaysOnBelowMinPrice(false);
        Instant recalculationTime = NOW.plus(Duration.ofMinutes(7));
        List<NordpoolEntity> prices = new ArrayList<>();
        prices.addAll(pricesForDay(TODAY, index -> {
            if (index < 8) return "10";
            if (index == 60 || index >= 80 && index < 87) return "20";
            return "101";
        }));
        prices.addAll(pricesForDay(TODAY.plusDays(1), index -> {
            if (index < 16) return "1";
            if (index < 24) return "5";
            return "101";
        }));
        when(nordpoolRepository
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        eq("FI"), any(), any()))
                .thenReturn(prices);

        service.generateForControl(2L, recalculationTime);

        List<ControlTableEntity> saved = savedEntries();
        List<ControlTableEntity> savedToday = saved.stream()
                .filter(entry -> localDate(entry).equals(TODAY))
                .toList();
        assertThat(savedToday).hasSize(9);
        assertThat(savedToday).allMatch(entry -> entry.getStartTime().isBefore(recalculationTime));
        assertThat(savedToday).anyMatch(entry -> entry.getStartTime().equals(NOW)
                && entry.getEndTime().isAfter(recalculationTime));
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY.plusDays(1)))).hasSize(23);
    }

    @Test
    void alwaysOnBelowMinimumAddsPeriodsBeyondTheTwoDayBaseQuota() {
        List<NordpoolEntity> prices = new ArrayList<>();
        prices.addAll(pricesForDay(TODAY, ignored -> "20"));
        prices.addAll(pricesForDay(TODAY.plusDays(1), ignored -> "1"));
        when(nordpoolRepository
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        eq("FI"), any(), any()))
                .thenReturn(prices);

        service.generateForControl(2L, NOW);

        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(112);
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY))).hasSize(16);
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY.plusDays(1)))).hasSize(96);
    }

    @Test
    void fallsBackToStrictDailyQuotaWhenTomorrowHasAPriceGap() {
        List<NordpoolEntity> prices = new ArrayList<>();
        prices.addAll(pricesForDay(TODAY, ignored -> "20"));
        List<NordpoolEntity> incompleteTomorrow = new ArrayList<>(
                pricesForDay(TODAY.plusDays(1), ignored -> "1")
        );
        incompleteTomorrow.remove(40);
        prices.addAll(incompleteTomorrow);
        when(nordpoolRepository
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        eq("FI"), any(), any()))
                .thenReturn(prices);

        service.generateForControl(2L, NOW);

        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY))).hasSize(16);
    }

    @Test
    void existingCheapestHoursModeKeepsItsStrictDailyQuota() {
        control.setMode(ControlMode.CHEAPEST_HOURS);
        List<NordpoolEntity> prices = new ArrayList<>();
        prices.addAll(pricesForDay(TODAY, ignored -> "20"));
        prices.addAll(pricesForDay(TODAY.plusDays(1), ignored -> "1"));
        when(nordpoolRepository
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        eq("FI"), any(), any()))
                .thenReturn(prices);

        service.generateForControl(2L, NOW);

        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved.stream().filter(entry -> localDate(entry).equals(TODAY))).hasSize(16);
    }

    @Test
    void usesControlLocalMidnightsForThePlanningRange() {
        Instant summerNow = Instant.parse("2026-08-22T12:00:00Z");
        LocalDate summerToday = LocalDate.of(2026, 8, 22);
        when(nordpoolRepository
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        eq("FI"), any(), any()))
                .thenReturn(List.of());

        service.generateForControl(2L, summerNow);

        Instant expectedStart = summerToday.atStartOfDay(CONTROL_ZONE).toInstant();
        Instant expectedEnd = summerToday.plusDays(2).atStartOfDay(CONTROL_ZONE).toInstant();
        verify(nordpoolRepository)
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        "FI", expectedStart, expectedEnd);
        verify(controlTableRepository)
                .deleteByControlAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        control, expectedStart, expectedEnd);
    }

    private List<ControlTableEntity> savedEntries() {
        ArgumentCaptor<ControlTableEntity> captor = ArgumentCaptor.forClass(ControlTableEntity.class);
        verify(controlTableRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private List<NordpoolEntity> pricesForDay(LocalDate date, IntFunction<String> priceAtIndex) {
        List<NordpoolEntity> prices = new ArrayList<>();
        Instant cursor = date.atStartOfDay(CONTROL_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(CONTROL_ZONE).toInstant();
        int index = 0;
        while (cursor.isBefore(end)) {
            Instant periodEnd = cursor.plus(Duration.ofMinutes(15));
            NordpoolEntity price = new NordpoolEntity();
            price.setDeliveryStart(cursor);
            price.setDeliveryEnd(periodEnd);
            price.setMarketIndexName("FI");
            price.setPriceFi(new BigDecimal(priceAtIndex.apply(index)));
            prices.add(price);
            cursor = periodEnd;
            index++;
        }
        return prices;
    }

    private LocalDate localDate(ControlTableEntity entry) {
        return entry.getStartTime().atZone(CONTROL_ZONE).toLocalDate();
    }
}
