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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.services.models.ControlTableResponse;
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

        org.mockito.Mockito.lenient().when(controlRepository.findById(2L)).thenReturn(java.util.Optional.of(control));
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

    @Test
    void consecutiveModeChoosesCheapestWholeBlockInsteadOfScatteredQuarters() {
        List<NordpoolEntity> prices = pricesForDay(TODAY, i -> {
            if (i == 0 || i == 10) return "1";
            return i >= 20 && i < 32 ? "5" : "20";
        });
        consecutivePrices(prices, 180);
        service.generateForControl(2L, dayStart(TODAY));

        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(12).allMatch(ControlTableEntity::isConsecutiveBase);
        assertThat(saved.getFirst().getStartTime()).isEqualTo(prices.get(20).getDeliveryStart());
        assertThat(saved.getLast().getEndTime()).isEqualTo(prices.get(31).getDeliveryEnd());
        assertContinuous(saved);
    }

    @Test
    void consecutiveModeRanksAndChecksMaximumWithCombinedPrices() {
        List<NordpoolEntity> prices = pricesForDay(TODAY, i -> i < 4 ? "1" : "10");
        consecutivePrices(prices, 60);
        control.setMaxPriceSnt(new BigDecimal("15"));
        when(controlPriceService.getCombinedPrice(eq(control), any(NordpoolEntity.class)))
                .thenAnswer(invocation -> {
                    NordpoolEntity price = invocation.getArgument(1);
                    return price.getDeliveryStart().isBefore(prices.get(4).getDeliveryStart())
                            ? new BigDecimal("25") : new BigDecimal("12");
                });
        service.generateForControl(2L, dayStart(TODAY));
        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(4).allMatch(row -> row.getPriceSnt().compareTo(new BigDecimal("12")) == 0);
        assertThat(saved.getFirst().getStartTime()).isEqualTo(prices.get(4).getDeliveryStart());
    }

    @Test
    void consecutiveModeNeverBridgesMissingOrOverMaximumQuarters() {
        List<NordpoolEntity> prices = new ArrayList<>(pricesForDay(TODAY, i -> i % 4 == 3 ? "101" : "1"));
        prices.remove(1);
        consecutivePrices(prices, 60);
        service.generateForControl(2L, dayStart(TODAY));
        verify(controlTableRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void consecutiveModeRejectsGapEvenWhenAllPricesAreAffordable() {
        List<NordpoolEntity> prices = new ArrayList<>(pricesForDay(TODAY, i -> "1").subList(0, 5));
        prices.remove(2);
        consecutivePrices(prices, 60);
        service.generateForControl(2L, dayStart(TODAY));
        verify(controlTableRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void consecutiveModeDoesNotCrossMidnightOrRedistributeToCheaperTomorrow() {
        List<NordpoolEntity> prices = new ArrayList<>(pricesForDay(TODAY, i -> i >= 88 ? "1" : "20"));
        prices.addAll(pricesForDay(TODAY.plusDays(1), i -> "0"));
        consecutivePrices(prices, 180);
        service.generateForControl(2L, dayStart(TODAY));
        List<ControlTableEntity> saved = savedEntries();
        List<ControlTableEntity> todayRows = saved.stream().filter(r -> localDate(r).equals(TODAY)).toList();
        List<ControlTableEntity> tomorrowRows = saved.stream().filter(r -> localDate(r).equals(TODAY.plusDays(1))).toList();
        assertThat(todayRows).hasSize(12);
        assertThat(tomorrowRows).hasSize(12);
        assertThat(todayRows.getLast().getEndTime()).isEqualTo(dayStart(TODAY.plusDays(1)));
        assertThat(tomorrowRows.getFirst().getStartTime()).isEqualTo(dayStart(TODAY.plusDays(1)));
        assertContinuous(todayRows);
        assertContinuous(tomorrowRows);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03-29", "2026-10-25", "2026-08-22"})
    void consecutiveModeUsesActualLocalDayLengthAndEarlierTieBreak(String localDay) {
        LocalDate date = LocalDate.parse(localDay);
        List<NordpoolEntity> prices = pricesForDay(date, i -> "-2");
        int minutes = (int) Duration.between(dayStart(date), dayStart(date.plusDays(1))).toMinutes();
        consecutivePrices(prices, minutes);
        service.generateForControl(2L, dayStart(date.plusDays(1)).minusSeconds(60));
        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(minutes / 15);
        assertThat(saved.getFirst().getStartTime()).isEqualTo(dayStart(date));
        assertThat(saved.getLast().getEndTime()).isEqualTo(dayStart(date.plusDays(1)));
        assertContinuous(saved);
    }

    @Test
    void consecutiveModeCannotFitTwentyFourHoursIntoSpringDstDay() {
        LocalDate date = LocalDate.of(2026, 3, 29);
        consecutivePrices(pricesForDay(date, i -> "1"), 1440);
        service.generateForControl(2L, dayStart(date));
        verify(controlTableRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void consecutiveModeUnionsExtraCheapPeriodsWithoutDoubleCountingBase() {
        List<NordpoolEntity> prices = pricesForDay(TODAY, i -> {
            if (i == 0 || i >= 10 && i < 14) return "1";
            return "20";
        });
        consecutivePrices(prices, 60);
        control.setAlwaysOnBelowMinPrice(true);
        service.generateForControl(2L, dayStart(TODAY));
        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(5);
        assertThat(saved.stream().filter(ControlTableEntity::isConsecutiveBase)).hasSize(4);
        assertThat(saved.getFirst().isConsecutiveBase()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 90})
    void consecutiveModeKeepsStartedAndCompletedBaseEvenWhenLaterPricesOrSettingsChange(int elapsedMinutes) {
        List<NordpoolEntity> prices = pricesForDay(TODAY, i -> i < 8 ? "50" : "1");
        consecutivePrices(prices, 180);
        Instant originalStart = dayStart(TODAY).plusSeconds(3600);
        List<ControlTableEntity> previous = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            previous.add(ControlTableEntity.builder().control(control)
                    .startTime(originalStart.plusSeconds(i * 900L)).endTime(originalStart.plusSeconds((i + 1) * 900L))
                    .priceSnt(new BigDecimal("20")).status(Status.FINAL).consecutiveBase(true)
                    .activationPushSentAt(originalStart).build());
        }
        when(controlTableRepository.findActivePeriodsOverlapping(eq(2L), eq(Status.FINAL), any(), any()))
                .thenReturn(previous);
        control.setMaxPriceSnt(new BigDecimal("10"));
        service.generateForControl(2L, originalStart.plusSeconds(elapsedMinutes * 60L));
        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(4);
        assertThat(saved.getFirst().getStartTime()).isEqualTo(originalStart);
        assertThat(saved.getLast().getEndTime()).isEqualTo(originalStart.plusSeconds(3600));
        assertThat(saved).allMatch(row -> row.getActivationPushSentAt().equals(originalStart));
        assertThat(saved).allMatch(row -> row.getPriceSnt().compareTo(new BigDecimal("50")) == 0);
    }

    @Test
    void consecutiveModeCanMoveFutureBaseAndDoesNotMistakeExtrasForCommittedBase() {
        List<NordpoolEntity> prices = pricesForDay(TODAY, i -> i >= 12 && i < 16 ? "1" : "20");
        consecutivePrices(prices, 60);
        when(controlTableRepository.findActivePeriodsOverlapping(eq(2L), eq(Status.FINAL), any(), any()))
                .thenReturn(List.of(
                        ControlTableEntity.builder().startTime(dayStart(TODAY)).endTime(dayStart(TODAY).plusSeconds(900))
                                .priceSnt(BigDecimal.ONE).status(Status.FINAL).consecutiveBase(false).build(),
                        ControlTableEntity.builder().startTime(dayStart(TODAY).plusSeconds(7200))
                                .endTime(dayStart(TODAY).plusSeconds(8100)).priceSnt(BigDecimal.TEN)
                                .status(Status.FINAL).consecutiveBase(true).build()));
        service.generateForControl(2L, dayStart(TODAY).plusSeconds(3600));
        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(4);
        assertThat(saved.getFirst().getStartTime()).isEqualTo(prices.get(12).getDeliveryStart());
    }

    @Test
    void consecutiveModeConsidersElapsedQuarterBoundariesAndPrefersEarlierEqualCostBlock() {
        List<NordpoolEntity> prices = pricesForDay(TODAY, i -> "1");
        consecutivePrices(prices, 60);
        service.generateForControl(2L, dayStart(TODAY).plusSeconds(7 * 60));
        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(4);
        assertThat(saved.getFirst().getStartTime()).isEqualTo(dayStart(TODAY));
        assertContinuous(saved);
    }

    @ParameterizedTest
    @ValueSource(ints = {150, 960, 1439})
    void consecutiveModeCalculatesWholeDayEvenWhenCheapestMorningBlockHasStartedOrFinished(int elapsedMinutes) {
        List<NordpoolEntity> prices = pricesForDay(TODAY, i -> i >= 8 && i < 20 ? "1" : "20");
        consecutivePrices(prices, 180);

        service.generateForControl(2L, dayStart(TODAY).plusSeconds(elapsedMinutes * 60L));

        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(12).allMatch(ControlTableEntity::isConsecutiveBase);
        assertThat(saved.getFirst().getStartTime()).isEqualTo(prices.get(8).getDeliveryStart());
        assertThat(saved.getLast().getEndTime()).isEqualTo(prices.get(19).getDeliveryEnd());
        assertContinuous(saved);
    }

    @Test
    void consecutiveModeSupportsQuarterStartsInsideHourlyPrices() {
        List<NordpoolEntity> prices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            NordpoolEntity price = new NordpoolEntity();
            price.setDeliveryStart(dayStart(TODAY).plusSeconds(i * 3600L));
            price.setDeliveryEnd(dayStart(TODAY).plusSeconds((i + 1) * 3600L));
            price.setPriceFi(i == 1 ? BigDecimal.ONE : BigDecimal.TEN);
            prices.add(price);
        }
        consecutivePrices(prices, 90);
        service.generateForControl(2L, dayStart(TODAY));
        List<ControlTableEntity> saved = savedEntries();
        assertThat(saved).hasSize(6);
        assertThat(saved.getFirst().getStartTime()).isEqualTo(dayStart(TODAY).plusSeconds(1800));
        assertContinuous(saved);
    }

    @Test
    void consecutiveWarningsDistinguishBaseFromExtrasAndOtherModes() {
        LocalDate today = LocalDate.now(CONTROL_ZONE);
        ControlTableResponse extra = ControlTableResponse.builder().status(Status.FINAL)
                .startTime(dayStart(today)).endTime(dayStart(today).plusSeconds(900)).build();
        ControlTableResponse base = ControlTableResponse.builder().status(Status.FINAL).consecutiveBase(true)
                .startTime(dayStart(today.plusDays(1))).endTime(dayStart(today.plusDays(1)).plusSeconds(900)).build();
        assertThat(service.missingConsecutiveBlockDates(ControlMode.CHEAPEST_CONSECUTIVE_HOURS, 60,
                CONTROL_ZONE.getId(), List.of(extra, base))).containsExactly(today);
        ControlTableResponse todayBase = ControlTableResponse.builder().status(Status.FINAL).consecutiveBase(true)
                .startTime(dayStart(today)).endTime(dayStart(today).plusSeconds(3600)).build();
        assertThat(service.missingConsecutiveBlockDates(ControlMode.CHEAPEST_CONSECUTIVE_HOURS, 60,
                CONTROL_ZONE.getId(), List.of(todayBase, base))).isEmpty();
        assertThat(service.missingConsecutiveBlockDates(ControlMode.CHEAPEST_HOURS, 60,
                CONTROL_ZONE.getId(), List.of())).isEmpty();
        assertThat(service.missingConsecutiveBlockDates(ControlMode.CHEAPEST_CONSECUTIVE_HOURS, 0,
                CONTROL_ZONE.getId(), List.of())).isEmpty();
    }

    private void consecutivePrices(List<NordpoolEntity> prices, int minutes) {
        control.setMode(ControlMode.CHEAPEST_CONSECUTIVE_HOURS);
        control.setAlwaysOnBelowMinPrice(false);
        control.setDailyOnMinutes(minutes);
        when(nordpoolRepository.findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                eq("FI"), any(), any())).thenReturn(prices);
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(CONTROL_ZONE).toInstant();
    }

    private void assertContinuous(List<ControlTableEntity> rows) {
        for (int i = 1; i < rows.size(); i++) {
            assertThat(rows.get(i).getStartTime()).isEqualTo(rows.get(i - 1).getEndTime());
        }
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
