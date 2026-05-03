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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        when(nordpoolRepository.findPricesBetween(any(Instant.class), any(Instant.class))).thenReturn(priceList);

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
        nordpoolEntity.setPriceFi(BigDecimal.valueOf(0.119590));
        return nordpoolEntity;
    }

}
