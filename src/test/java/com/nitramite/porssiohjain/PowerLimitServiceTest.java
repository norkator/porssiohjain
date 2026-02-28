package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.PowerLimitHistoryEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitHistoryRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.models.DailyUsageCostResponse;
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
    private AccountRepository accountRepository;

    @MockitoBean
    private PowerLimitRepository powerLimitRepository;

    @MockitoBean
    private PowerLimitHistoryRepository powerLimitHistoryRepository;

    @MockitoBean
    private NordpoolRepository nordpoolRepository;

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
