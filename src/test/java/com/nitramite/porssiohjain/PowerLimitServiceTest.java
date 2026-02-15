package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.models.DailyUsageCostResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.test.context.ActiveProfiles;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@SpringBootTest
@ActiveProfiles("test")
public class PowerLimitServiceTest {

    @Autowired
    private PowerLimitService powerLimitService;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private PowerLimitRepository powerLimitRepository;

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

        List<DailyUsageCostResponse> result = powerLimitService
                .getDailyUsageCostForMonth(accountId, powerLimitId, selectedMonth);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

}
