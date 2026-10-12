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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.PowerLimitHistoryEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitHistoryRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.PushNotificationService;
import com.nitramite.porssiohjain.services.PushNotificationTokenService;
import com.nitramite.porssiohjain.services.models.DailyUsageCostResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitHistoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public class PowerLimitServiceTest {

    @Autowired
    private PowerLimitService powerLimitService;

    @MockitoBean
    private MqttService mqttService;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private PowerLimitRepository powerLimitRepository;

    @MockitoBean
    private PowerLimitHistoryRepository powerLimitHistoryRepository;

    @MockitoBean
    private NordpoolRepository nordpoolRepository;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    @MockitoBean
    private PushNotificationTokenService pushNotificationTokenService;

    @Test
    void createUser_WithSpringContext() {
        YearMonth selectedMonth = YearMonth.now();

        AccountEntity account = new AccountEntity();
        Long accountId = 1L;
        account.setId(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        PowerLimitEntity powerLimit = new PowerLimitEntity();
        Long powerLimitId = 1L;
        powerLimit.setId(powerLimitId);
        powerLimit.setTimezone("Europe/Helsinki");
        when(powerLimitRepository.findByAccountIdAndId(accountId, powerLimitId))
                .thenReturn(Optional.of(powerLimit));

        ZoneId zone = ZoneId.of(powerLimit.getTimezone());
        ZonedDateTime nowZoned = ZonedDateTime.now(zone);
        YearMonth currentMonth = YearMonth.from(nowZoned);
        ZonedDateTime startOfMonthZoned = selectedMonth.atDay(1).atStartOfDay(zone);
        ZonedDateTime endZoned = selectedMonth.equals(currentMonth)
                ? nowZoned
                : selectedMonth.plusMonths(1).atDay(1).atStartOfDay(zone);
        Instant start = startOfMonthZoned.toInstant();
        Instant end = endZoned.toInstant();
        List<PowerLimitHistoryEntity> historyEntities = new ArrayList<>();
        historyEntities.add(getPowerLimitHistoryEntity(1L, powerLimit, account, start, BigDecimal.valueOf(1.0)));
        historyEntities.add(getPowerLimitHistoryEntity(
                2L, powerLimit, account, start.plusSeconds(60), BigDecimal.valueOf(0.5)
        ));

        when(powerLimitHistoryRepository.findByPowerLimitAndCreatedAtBetween(
                anyLong(), anyLong(), any(Instant.class), any(Instant.class)
        )).thenReturn(historyEntities);

        List<NordpoolEntity> priceList = new ArrayList<>();
        priceList.add(getNordpoolEntity(start, end));
        when(nordpoolRepository.findPricesBetween(anyString(), any(Instant.class), any(Instant.class))).thenReturn(priceList);

        List<DailyUsageCostResponse> result = powerLimitService
                .getDailyUsageCostForMonth(accountId, powerLimitId, selectedMonth);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(1.5), result.getFirst().getTotalUsageKwh());
        assertEquals(BigDecimal.valueOf(0.0002332005).stripTrailingZeros(), result.getFirst().getTotalCostEur().stripTrailingZeros()
        );
    }

    @Test
    void getPowerLimitHistoryForRange_UsesIntervalOverride() {
        AccountEntity account = new AccountEntity();
        Long accountId = 1L;
        account.setId(accountId);

        PowerLimitEntity powerLimit = new PowerLimitEntity();
        Long powerLimitId = 1L;
        powerLimit.setId(powerLimitId);
        powerLimit.setTimezone("Europe/Helsinki");
        powerLimit.setLimitIntervalMinutes(15);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(powerLimitRepository.findByAccountIdAndId(accountId, powerLimitId))
                .thenReturn(Optional.of(powerLimit));

        ZoneId zone = ZoneId.of(powerLimit.getTimezone());
        Instant start = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, zone).toInstant();
        Instant end = start.plusSeconds(3600);
        List<PowerLimitHistoryEntity> historyEntities = new ArrayList<>();
        historyEntities.add(getPowerLimitHistoryEntity(
                1L, powerLimit, account, start.plusSeconds(5 * 60), BigDecimal.valueOf(1.0)
        ));
        historyEntities.add(getPowerLimitHistoryEntity(
                2L, powerLimit, account, start.plusSeconds(20 * 60), BigDecimal.valueOf(0.5)
        ));

        when(powerLimitHistoryRepository.findByPowerLimitAndCreatedAtBetween(accountId, powerLimitId, start, end))
                .thenReturn(historyEntities);

        List<PowerLimitHistoryResponse> result = powerLimitService
                .getPowerLimitHistoryForRange(accountId, powerLimitId, start, end, 60);

        assertEquals(1, result.size());
        assertEquals(start, result.getFirst().getCreatedAt());
        assertEquals(BigDecimal.valueOf(1.5), result.getFirst().getKilowatts());
    }

    @Test
    void getPowerLimitHistoryWithInterval_GeneratesDataForAnyDemoPowerLimit() {
        Long accountId = 1L;
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setDemo(true);
        account.setUuid(UUID.fromString("78b7823f-d5cc-4376-8910-cd62e7b32400"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        PowerLimitEntity firstLimit = new PowerLimitEntity();
        firstLimit.setId(10L);
        firstLimit.setTimezone("Europe/Helsinki");
        firstLimit.setLimitIntervalMinutes(15);
        firstLimit.setLimitKw(BigDecimal.valueOf(4));
        PowerLimitEntity secondLimit = new PowerLimitEntity();
        secondLimit.setId(11L);
        secondLimit.setTimezone("Europe/Helsinki");
        secondLimit.setLimitIntervalMinutes(60);
        secondLimit.setLimitKw(BigDecimal.valueOf(8));
        when(powerLimitRepository.findByAccountIdAndId(accountId, 10L)).thenReturn(Optional.of(firstLimit));
        when(powerLimitRepository.findByAccountIdAndId(accountId, 11L)).thenReturn(Optional.of(secondLimit));

        List<PowerLimitHistoryResponse> first = powerLimitService.getPowerLimitHistoryWithInterval(accountId, 10L, 24);
        List<PowerLimitHistoryResponse> second = powerLimitService.getPowerLimitHistoryWithInterval(accountId, 11L, 24);

        assertEquals(96, first.size());
        assertEquals(24, second.size());
        assertEquals(accountId, first.getFirst().getAccountId());
        assertTrue(first.stream().allMatch(point -> point.getKilowatts().signum() >= 0));
        assertTrue(first.stream().map(PowerLimitHistoryResponse::getKilowatts).distinct().count() > 1);
        assertTrue(second.getFirst().getKilowatts().compareTo(first.getFirst().getKilowatts()) > 0);
        assertTrue(first.getFirst().getCreatedAt().isBefore(first.getLast().getCreatedAt()));
        verifyNoInteractions(powerLimitHistoryRepository);
    }

    @Test
    void getPowerLimitHistoryWithInterval_DemoCannotReadAnotherAccountsLimit() {
        Long accountId = 1L;
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setDemo(true);
        account.setUuid(UUID.fromString("78b7823f-d5cc-4376-8910-cd62e7b32400"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> powerLimitService.getPowerLimitHistoryWithInterval(accountId, 99L, 24));
        verifyNoInteractions(powerLimitHistoryRepository);
    }

    @Test
    void getPowerLimitHistoryWithInterval_DoesNotGenerateDataForOtherAccounts() {
        Long accountId = 2L;
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setDemo(true);
        account.setUuid(UUID.randomUUID());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        PowerLimitEntity limit = new PowerLimitEntity();
        limit.setId(20L);
        limit.setTimezone("Europe/Helsinki");
        limit.setLimitIntervalMinutes(15);
        when(powerLimitRepository.findByAccountIdAndId(accountId, 20L)).thenReturn(Optional.of(limit));

        assertTrue(powerLimitService.getPowerLimitHistoryWithInterval(accountId, 20L, 24).isEmpty());
        verify(powerLimitHistoryRepository).findByPowerLimitAndCreatedAtBetween(eq(accountId), eq(20L), any(Instant.class), any(Instant.class));
    }

    @ParameterizedTest
    @CsvSource({
            "2025-07-01, false, 2025-06-30T21:00:00Z, 2025-07-01T21:00:00Z",
            "2025-03-30, false, 2025-03-29T22:00:00Z, 2025-03-30T21:00:00Z",
            "2025-10-26, false, 2025-10-25T21:00:00Z, 2025-10-26T22:00:00Z",
            "2025-03-30, true, 2025-03-23T22:00:00Z, 2025-03-30T21:00:00Z",
            "2025-10-26, true, 2025-10-19T21:00:00Z, 2025-10-26T22:00:00Z",
            "2025-01-01, true, 2024-12-29T22:00:00Z, 2025-01-05T22:00:00Z"
    })
    void getPowerLimitHistoryForPeriod_UsesLocalDayAndMondayWeekBoundaries(
            String date, boolean week, String startValue, String endValue
    ) {
        AccountEntity account = new AccountEntity();
        account.setId(2L);
        PowerLimitEntity limit = new PowerLimitEntity();
        limit.setId(20L);
        limit.setTimezone("Europe/Helsinki");
        limit.setLimitIntervalMinutes(15);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account));
        when(powerLimitRepository.findByAccountIdAndId(2L, 20L)).thenReturn(Optional.of(limit));
        Instant start = Instant.parse(startValue);
        Instant end = Instant.parse(endValue);
        when(powerLimitHistoryRepository.findByPowerLimitAndCreatedAtBetween(2L, 20L, start, end))
                .thenReturn(List.of(
                        getPowerLimitHistoryEntity(1L, limit, account, start, BigDecimal.ONE),
                        getPowerLimitHistoryEntity(2L, limit, account, start.plusSeconds(60), BigDecimal.valueOf(0.5))
                ));

        List<PowerLimitHistoryResponse> history = powerLimitService
                .getPowerLimitHistoryForPeriod(2L, 20L, LocalDate.parse(date), week);

        verify(powerLimitHistoryRepository).findByPowerLimitAndCreatedAtBetween(2L, 20L, start, end);
        assertEquals(1, history.size());
        assertEquals(start, history.getFirst().getCreatedAt());
        assertEquals(BigDecimal.valueOf(1.5), history.getFirst().getKilowatts());
    }

    @ParameterizedTest
    @CsvSource({"2025-03-30, false, 92", "2025-10-26, false, 100", "2025-03-30, true, 668", "2025-10-26, true, 676"})
    void getPowerLimitHistoryForPeriod_DemoRespectsDstAndExclusiveEnd(String date, boolean week, int expectedPoints) {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setUuid(UUID.fromString("78b7823f-d5cc-4376-8910-cd62e7b32400"));
        PowerLimitEntity limit = new PowerLimitEntity();
        limit.setId(10L);
        limit.setTimezone("Europe/Helsinki");
        limit.setLimitIntervalMinutes(15);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(powerLimitRepository.findByAccountIdAndId(1L, 10L)).thenReturn(Optional.of(limit));

        List<PowerLimitHistoryResponse> history = powerLimitService
                .getPowerLimitHistoryForPeriod(1L, 10L, LocalDate.parse(date), week);

        assertEquals(expectedPoints, history.size());
        assertEquals(0, history.getFirst().getCreatedAt().atZone(ZoneId.of(limit.getTimezone())).getHour());
        assertEquals(23, history.getLast().getCreatedAt().atZone(ZoneId.of(limit.getTimezone())).getHour());
        assertEquals(45, history.getLast().getCreatedAt().atZone(ZoneId.of(limit.getTimezone())).getMinute());
        verifyNoInteractions(powerLimitHistoryRepository);
    }

    @Test
    void getPowerLimitHistoryForPeriod_CannotReadAnotherAccountsLimit() {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> powerLimitService.getPowerLimitHistoryForPeriod(1L, 99L, LocalDate.of(2025, 1, 1), true));
        verifyNoInteractions(powerLimitHistoryRepository);
    }

    private PowerLimitHistoryEntity getPowerLimitHistoryEntity(
            Long id,
            PowerLimitEntity powerLimit, AccountEntity account,
            Instant createdAt, BigDecimal kw
    ) {
        PowerLimitHistoryEntity powerLimitHistory1 = new PowerLimitHistoryEntity();
        powerLimitHistory1.setId(id);
        powerLimitHistory1.setPowerLimit(powerLimit);
        powerLimitHistory1.setKilowatts(kw);
        powerLimitHistory1.setAccount(account);
        powerLimitHistory1.setCreatedAt(createdAt);
        return powerLimitHistory1;
    }

    private NordpoolEntity getNordpoolEntity(
            Instant start, Instant end
    ) {
        NordpoolEntity nordpoolEntity = new NordpoolEntity();
        nordpoolEntity.setId(1L);
        nordpoolEntity.setDeliveryStart(start);
        nordpoolEntity.setDeliveryEnd(end);
        nordpoolEntity.setMarketIndexName("FI");
        nordpoolEntity.setPriceFi(BigDecimal.valueOf(0.119590));
        return nordpoolEntity;
    }

}
