package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.services.models.PowerLimitResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PowerLimitService {

    private final PowerLimitRepository powerLimitRepository;
    private final AccountRepository accountRepository;

    public PowerLimitService(
            PowerLimitRepository powerLimitRepository,
            AccountRepository accountRepository
    ) {
        this.powerLimitRepository = powerLimitRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public PowerLimitResponse createLimit(Long accountId, String name, Double limitKw, boolean enabled) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        PowerLimitEntity entity = PowerLimitEntity.builder()
                .account(account)
                .name(name)
                .limitKw(BigDecimal.valueOf(limitKw))
                .currentKw(BigDecimal.ZERO)
                .enabled(enabled)
                .timezone("UTC")
                .build();

        powerLimitRepository.save(entity);

        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<PowerLimitResponse> getAllLimits(Long accountId) {
        return powerLimitRepository.findByAccountId(accountId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PowerLimitResponse mapToResponse(PowerLimitEntity entity) {
        return PowerLimitResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .name(entity.getName())
                .limitKw(entity.getLimitKw())
                .currentKw(entity.getCurrentKw())
                .enabled(entity.isEnabled())
                .timezone(entity.getTimezone())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}