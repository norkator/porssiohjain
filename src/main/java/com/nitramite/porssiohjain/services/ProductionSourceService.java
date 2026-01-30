package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionSourceService {

    private final ProductionSourceRepository productionSourceRepository;
    private final AccountRepository accountRepository;
    private final ProductionSourceDeviceRepository deviceRepository;
    private final ProductionHistoryRepository productionHistoryRepository;

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

    @Transactional(readOnly = true)
    public ProductionSourceResponse getSource(Long accountId, Long sourceId) {
        return productionSourceRepository.findByIdAndAccountId(sourceId, accountId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));
    }

    @Transactional(readOnly = true)
    public List<ProductionSourceDeviceResponse> getSourceDevices(Long sourceId) {
        return deviceRepository.findByProductionSourceId(sourceId).stream()
                .map(this::mapDeviceToResponse)
                .collect(Collectors.toList());
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
                .deviceName(entity.getDeviceName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Transactional
    public void addDevice(Long accountId, Long sourceId, Long deviceId, int channel) {
        ProductionSourceDeviceEntity entity = new ProductionSourceDeviceEntity();
        entity.setProductionSource(productionSourceRepository.getReferenceById(sourceId));
        entity.setDevice(deviceRepository.getReferenceById(deviceId).getDevice());
        entity.setDeviceChannel(channel);
        deviceRepository.save(entity);
    }

    @Transactional
    public void removeDevice(Long sourceId, Long deviceMappingId) {
        deviceRepository.deleteById(deviceMappingId);
    }

    @Transactional
    public void updateSource(
            Long accountId,
            Long sourceId,
            String name,
            boolean enabled,
            String appId,
            String appSecret,
            String email,
            String password,
            String stationId
    ) {
        ProductionSourceEntity entity = productionSourceRepository.findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));

        entity.setName(name);
        entity.setEnabled(enabled);
        entity.setAppId(appId);
        entity.setAppSecret(appSecret);
        entity.setEmail(email);
        entity.setPassword(password);
        entity.setStationId(stationId);

        productionSourceRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ProductionHistoryResponse> getProductionHistory(Long sourceId, int hours) {
        return productionHistoryRepository.findLastHoursBySourceId(sourceId, hours)
                .stream()
                .map(h -> ProductionHistoryResponse.builder()
                        .kilowatts(h.getKilowatts())
                        .createdAt(h.getCreatedAt())
                        .build()
                ).collect(Collectors.toList());
    }

}
