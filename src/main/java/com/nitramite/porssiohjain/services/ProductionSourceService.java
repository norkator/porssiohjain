package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionSourceService {

    private final ProductionSourceRepository productionSourceRepository;
    private final AccountRepository accountRepository;

    public void createSource(
            Long accountId,
            String name,
            ProductionApiType apiType,
            String appId,
            String appSecret,
            String email,
            String password,
            String stationId,
            boolean enabled
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        ProductionSourceEntity entity = ProductionSourceEntity.builder()
                .name(name)
                .apiType(apiType)
                .appId(appId)
                .appSecret(appSecret)
                .email(email)
                .password(password)
                .stationId(stationId)
                .enabled(enabled)
                .account(account)
                .build();

        productionSourceRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ProductionSourceResponse> getAllSources(Long accountId) {
        return productionSourceRepository.findByAccountId(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ProductionSourceResponse toResponse(ProductionSourceEntity e) {
        return ProductionSourceResponse.builder()
                .id(e.getId())
                .uuid(e.getUuid())
                .name(e.getName())
                .apiType(e.getApiType())
                .currentKw(e.getCurrentKw())
                .peakKw(e.getPeakKw())
                .enabled(e.isEnabled())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

}
