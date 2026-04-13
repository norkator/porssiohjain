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

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.ProductionApiType;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionSourceService {

    private final ProductionSourceRepository productionSourceRepository;
    private final AccountRepository accountRepository;
    private final ProductionSourceDeviceRepository productionSourceDeviceRepository;
    private final ProductionSourceHeatPumpRepository productionSourceHeatPumpRepository;
    private final ProductionHistoryRepository productionHistoryRepository;
    private final DeviceRepository deviceRepository;
    private final SiteRepository siteRepository;
    private final AccountLimitService accountLimitService;

    @Transactional
    public void deleteOldProductionHistory() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int deleted = productionHistoryRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} production history rows older than {}", deleted, cutoff);
        }
    }

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
        accountLimitService.assertCanCreateProductionSource(accountId);

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
                .timezone("UTC")
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
                .timezone(e.getTimezone())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .appId(e.getAppId())
                .appSecret(e.getAppSecret())
                .email(e.getEmail())
                .password(e.getPassword())
                .stationId(e.getStationId())
                .siteId(e.getSite() != null ? e.getSite().getId() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public ProductionSourceResponse getSource(Long accountId, Long sourceId) {
        return productionSourceRepository.findByIdAndAccountId(sourceId, accountId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));
    }

    @Transactional(readOnly = true)
    public List<ProductionSourceDeviceResponse> getSourceDevices(Long accountId, Long sourceId) {
        ProductionSourceEntity source = productionSourceRepository.findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found for account"));
        return productionSourceDeviceRepository.findByProductionSourceId(source.getId()).stream()
                .filter(entity -> entity.getDevice().getDeviceType() == DeviceType.STANDARD)
                .map(this::mapDeviceToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductionSourceHeatPumpResponse> getSourceHeatPumps(Long accountId, Long sourceId) {
        ProductionSourceEntity source = productionSourceRepository.findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found for account"));
        return productionSourceHeatPumpRepository.findByProductionSourceId(source.getId()).stream()
                .filter(entity -> entity.getDevice().getDeviceType() == DeviceType.HEAT_PUMP)
                .map(this::mapHeatPumpToResponse)
                .toList();
    }

    private ProductionSourceDeviceResponse mapDeviceToResponse(ProductionSourceDeviceEntity entity) {
        return ProductionSourceDeviceResponse.builder()
                .id(entity.getId())
                .deviceId(entity.getDevice().getId())
                .deviceChannel(entity.getDeviceChannel())
                .device(mapDeviceToResponse(entity.getDevice()))
                .sourceId(entity.getProductionSource().getId())
                .triggerKw(entity.getTriggerKw())
                .comparisonType(entity.getComparisonType())
                .action(entity.getAction())
                .enabled(entity.isEnabled())
                .build();
    }

    private DeviceResponse mapDeviceToResponse(DeviceEntity entity) {
        return DeviceResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .deviceType(entity.getDeviceType())
                .deviceName(entity.getDeviceName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ProductionSourceHeatPumpResponse mapHeatPumpToResponse(ProductionSourceHeatPumpEntity entity) {
        return ProductionSourceHeatPumpResponse.builder()
                .id(entity.getId())
                .sourceId(entity.getProductionSource().getId())
                .deviceId(entity.getDevice().getId())
                .stateHex(entity.getStateHex())
                .controlAction(entity.getControlAction())
                .comparisonType(entity.getComparisonType())
                .triggerKw(entity.getTriggerKw())
                .device(mapDeviceToResponse(entity.getDevice()))
                .build();
    }

    @Transactional
    public void addDevice(
            Long accountId, Long sourceId, Long deviceId, int channel,
            BigDecimal triggerKw, ComparisonType comparisonType, ControlAction action
    ) {
        AccountEntity account = accountRepository
                .findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        ProductionSourceEntity source = productionSourceRepository
                .findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found for account"));
        DeviceEntity device = deviceRepository
                .findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found for account"));
        ProductionSourceDeviceEntity entity = new ProductionSourceDeviceEntity();
        entity.setProductionSource(source);
        entity.setDevice(device);
        entity.setDeviceChannel(channel);
        entity.setTriggerKw(triggerKw);
        entity.setComparisonType(comparisonType);
        entity.setAction(action);
        entity.setEnabled(true);
        productionSourceDeviceRepository.save(entity);
    }

    @Transactional
    public ProductionSourceHeatPumpResponse addHeatPump(
            Long accountId, Long sourceId, Long deviceId, String stateHex,
            ControlAction controlAction, ComparisonType comparisonType, BigDecimal triggerKw
    ) {
        AccountEntity account = accountRepository
                .findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        ProductionSourceEntity source = productionSourceRepository
                .findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found for account"));
        DeviceEntity device = deviceRepository
                .findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found for account"));

        ProductionSourceHeatPumpEntity entity = ProductionSourceHeatPumpEntity.builder()
                .productionSource(source)
                .device(device)
                .stateHex(stateHex)
                .controlAction(controlAction)
                .comparisonType(comparisonType)
                .triggerKw(triggerKw)
                .build();

        ProductionSourceHeatPumpEntity saved = productionSourceHeatPumpRepository.save(entity);
        return mapHeatPumpToResponse(saved);
    }

    @Transactional
    public ProductionSourceHeatPumpResponse updateHeatPump(
            Long accountId, Long sourceId, Long heatPumpMappingId, Long deviceId, String stateHex,
            ControlAction controlAction, ComparisonType comparisonType, BigDecimal triggerKw
    ) {
        AccountEntity account = accountRepository
                .findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        ProductionSourceEntity source = productionSourceRepository
                .findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found for account"));
        DeviceEntity device = deviceRepository
                .findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found for account"));
        if (device.getDeviceType() != DeviceType.HEAT_PUMP) {
            throw new IllegalArgumentException("Device is not a heat pump");
        }

        ProductionSourceHeatPumpEntity entity = productionSourceHeatPumpRepository
                .findById(heatPumpMappingId)
                .filter(mapping -> mapping.getProductionSource().getId().equals(source.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Heat pump mapping not found for source"));
        entity.setDevice(device);
        entity.setStateHex(stateHex);
        entity.setControlAction(controlAction);
        entity.setComparisonType(comparisonType);
        entity.setTriggerKw(triggerKw);

        return mapHeatPumpToResponse(entity);
    }

    @Transactional
    public void removeDevice(Long accountId, Long sourceId, Long deviceMappingId) {
        AccountEntity account = accountRepository
                .findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        ProductionSourceEntity source = productionSourceRepository
                .findByIdAndAccountId(sourceId, account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Source not found for account"));
        productionSourceDeviceRepository.deleteByIdAndProductionSourceId(deviceMappingId, source.getId());
    }

    @Transactional
    public void removeHeatPump(Long accountId, Long sourceId, Long heatPumpMappingId) {
        AccountEntity account = accountRepository
                .findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        ProductionSourceEntity source = productionSourceRepository
                .findByIdAndAccountId(sourceId, account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Source not found for account"));
        productionSourceHeatPumpRepository.deleteByIdAndProductionSourceId(heatPumpMappingId, source.getId());
    }

    @Transactional
    public void updateSource(
            Long accountId,
            Long sourceId,
            String name,
            boolean enabled,
            String timezone,
            String appId,
            String appSecret,
            String email,
            String password,
            String stationId,
            Long siteId
    ) {
        ProductionSourceEntity entity = productionSourceRepository
                .findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));
        entity.setName(name);
        entity.setEnabled(enabled);
        entity.setTimezone(timezone);
        if (appId != null) entity.setAppId(appId);
        if (email != null) entity.setEmail(email);
        if (stationId != null) entity.setStationId(stationId);
        if (appSecret != null && !appSecret.isBlank()) {
            entity.setAppSecret(appSecret);
        }
        if (password != null && !password.isBlank()) {
            entity.setPassword(password);
        }
        SiteEntity site = siteId == null
                ? null
                : siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found for account"));
        entity.setSite(site);
    }

    @Transactional(readOnly = true)
    public List<ProductionHistoryResponse> getProductionHistory(Long accountId, Long sourceId, int hours) {
        ProductionSourceEntity source = productionSourceRepository.findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Production source not found for account: " + sourceId));
        ZoneId zone = ZoneId.of(source.getTimezone());
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        Map<Instant, List<ProductionHistoryEntity>> grouped =
                productionHistoryRepository.findAllByProductionSource(source)
                        .stream()
                        .filter(h -> h.getCreatedAt().isAfter(since))
                        .collect(Collectors.groupingBy(h -> Utils.toQuarterHour(h.getCreatedAt(), zone)));
        return grouped.entrySet().stream()
                .map(entry -> {
                    Instant bucketStart = entry.getKey();
                    List<ProductionHistoryEntity> values = entry.getValue();
                    BigDecimal avg = values.stream()
                            .map(ProductionHistoryEntity::getKilowatts)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
                    return ProductionHistoryResponse.builder()
                            .kilowatts(avg)
                            .createdAt(bucketStart)
                            .build();
                })
                .sorted(Comparator.comparing(ProductionHistoryResponse::getCreatedAt))
                .toList();
    }

    @Transactional
    public void deleteProductionSource(
            Long accountId, Long sourceId
    ) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        ProductionSourceEntity source = productionSourceRepository
                .findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found for account"));
        productionSourceRepository.delete(source);
    }

}
