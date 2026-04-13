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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.WeatherControlEntity;
import com.nitramite.porssiohjain.entity.WeatherControlDeviceEntity;
import com.nitramite.porssiohjain.entity.WeatherControlHeatPumpEntity;
import com.nitramite.porssiohjain.entity.ResourceSharingEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.ResourceType;
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlRepository;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlDeviceResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlHeatPumpResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class WeatherControlService {

    private final WeatherControlRepository weatherControlRepository;
    private final AccountRepository accountRepository;
    private final SiteRepository siteRepository;
    private final DeviceRepository deviceRepository;
    private final WeatherControlDeviceRepository weatherControlDeviceRepository;
    private final WeatherControlHeatPumpRepository weatherControlHeatPumpRepository;
    private final ResourceSharingRepository resourceSharingRepository;
    private final AccountLimitService accountLimitService;
    private final SiteWeatherService siteWeatherService;

    public WeatherControlEntity createWeatherControl(Long accountId, String name, Long siteId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        accountLimitService.assertCanCreateWeatherControl(accountId);

        SiteEntity site = siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found with id: " + siteId));

        WeatherControlEntity entity = WeatherControlEntity.builder()
                .account(account)
                .name(name)
                .site(site)
                .build();

        return weatherControlRepository.save(entity);
    }

    public List<WeatherControlResponse> getAllWeatherControls(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        List<WeatherControlResponse> responses = new ArrayList<>();
        weatherControlRepository.findAllByAccountOrderByIdAsc(account).stream()
                .map(entity -> toResponse(entity, false))
                .forEach(responses::add);

        List<Long> sharedWeatherControlIds = resourceSharingRepository
                .findByReceiverAccountIdAndResourceTypeAndEnabledTrue(accountId, ResourceType.WEATHER_CONTROL).stream()
                .map(ResourceSharingEntity::getWeatherControlId)
                .filter(Objects::nonNull)
                .toList();
        if (!sharedWeatherControlIds.isEmpty()) {
            weatherControlRepository.findAllById(sharedWeatherControlIds).stream()
                    .map(entity -> toResponse(entity, true))
                    .forEach(responses::add);
        }

        return responses;
    }

    public List<WeatherControlResponse> getOwnedWeatherControls(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        return weatherControlRepository.findAllByAccountOrderByIdAsc(account).stream()
                .map(entity -> toResponse(entity, false))
                .toList();
    }

    public WeatherControlResponse getWeatherControl(Long accountId, Long weatherControlId) {
        return toResponse(getAccessibleWeatherControl(accountId, weatherControlId), isSharedWeatherControl(accountId, weatherControlId));
    }

    public WeatherControlResponse updateWeatherControl(Long accountId, Long weatherControlId, String name, Long siteId) {
        WeatherControlEntity entity = weatherControlRepository.findByIdAndAccountId(weatherControlId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Weather control not found for your account with ID: " + weatherControlId));

        SiteEntity site = siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found with id: " + siteId));

        entity.setName(name);
        entity.setSite(site);
        return toResponse(weatherControlRepository.save(entity));
    }

    public WeatherControlDeviceResponse addDeviceToWeatherControl(
            Long accountId, Long weatherControlId, Long deviceId, Integer deviceChannel, WeatherMetricType weatherMetric,
            ComparisonType comparisonType, BigDecimal thresholdValue, ControlAction controlAction, boolean priorityRule
    ) {
        WeatherControlEntity weatherControl = getOwnedWeatherControl(accountId, weatherControlId);
        DeviceEntity device = getOwnedDevice(accountId, deviceId);

        if (weatherControlDeviceRepository.existsByWeatherControlIdAndDeviceIdAndDeviceChannel(weatherControlId, deviceId, deviceChannel)) {
            throw new IllegalArgumentException("Device channel is already linked to this weather control");
        }

        WeatherControlDeviceEntity entity = WeatherControlDeviceEntity.builder()
                .weatherControl(weatherControl)
                .device(device)
                .deviceChannel(deviceChannel)
                .weatherMetric(weatherMetric)
                .comparisonType(comparisonType)
                .thresholdValue(thresholdValue)
                .controlAction(controlAction)
                .priorityRule(priorityRule)
                .build();

        return toDeviceResponse(weatherControlDeviceRepository.save(entity));
    }

    public WeatherControlDeviceResponse updateWeatherControlDevice(
            Long accountId, Long weatherControlDeviceId, Long deviceId, Integer deviceChannel, WeatherMetricType weatherMetric,
            ComparisonType comparisonType, BigDecimal thresholdValue, ControlAction controlAction, boolean priorityRule
    ) {
        WeatherControlDeviceEntity entity = weatherControlDeviceRepository.findById(weatherControlDeviceId)
                .orElseThrow(() -> new EntityNotFoundException("WeatherControlDevice not found with id: " + weatherControlDeviceId));
        ensureOwnership(accountId, entity.getWeatherControl().getAccount().getId());

        DeviceEntity device = getOwnedDevice(accountId, deviceId);
        entity.setDevice(device);
        entity.setDeviceChannel(deviceChannel);
        entity.setWeatherMetric(weatherMetric);
        entity.setComparisonType(comparisonType);
        entity.setThresholdValue(thresholdValue);
        entity.setControlAction(controlAction);
        entity.setPriorityRule(priorityRule);

        return toDeviceResponse(weatherControlDeviceRepository.save(entity));
    }

    public void deleteWeatherControlDevice(Long accountId, Long weatherControlDeviceId) {
        WeatherControlDeviceEntity entity = weatherControlDeviceRepository.findById(weatherControlDeviceId)
                .orElseThrow(() -> new EntityNotFoundException("WeatherControlDevice not found with id: " + weatherControlDeviceId));
        ensureOwnership(accountId, entity.getWeatherControl().getAccount().getId());
        weatherControlDeviceRepository.delete(entity);
    }

    public List<WeatherControlDeviceResponse> getWeatherControlDevices(Long accountId, Long weatherControlId) {
        WeatherControlEntity weatherControl = getAccessibleWeatherControl(accountId, weatherControlId);
        Set<WeatherControlDeviceEntity> entities = weatherControl.getWeatherControlDevices();
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toDeviceResponse)
                .sorted(Comparator.comparing(WeatherControlDeviceResponse::getDeviceChannel))
                .toList();
    }

    public WeatherControlHeatPumpResponse addHeatPumpToWeatherControl(
            Long accountId, Long weatherControlId, Long deviceId, String stateHex, WeatherMetricType weatherMetric,
            ComparisonType comparisonType, BigDecimal thresholdValue
    ) {
        WeatherControlEntity weatherControl = getOwnedWeatherControl(accountId, weatherControlId);
        DeviceEntity device = getOwnedDevice(accountId, deviceId);

        WeatherControlHeatPumpEntity entity = WeatherControlHeatPumpEntity.builder()
                .weatherControl(weatherControl)
                .device(device)
                .stateHex(stateHex)
                .weatherMetric(weatherMetric)
                .comparisonType(comparisonType)
                .thresholdValue(thresholdValue)
                .build();

        return toHeatPumpResponse(weatherControlHeatPumpRepository.save(entity));
    }

    public WeatherControlHeatPumpResponse updateWeatherControlHeatPump(
            Long accountId, Long weatherControlHeatPumpId, Long deviceId, String stateHex, WeatherMetricType weatherMetric,
            ComparisonType comparisonType, BigDecimal thresholdValue
    ) {
        WeatherControlHeatPumpEntity entity = weatherControlHeatPumpRepository.findById(weatherControlHeatPumpId)
                .orElseThrow(() -> new EntityNotFoundException("WeatherControlHeatPump not found with id: " + weatherControlHeatPumpId));
        ensureOwnership(accountId, entity.getWeatherControl().getAccount().getId());

        DeviceEntity device = getOwnedDevice(accountId, deviceId);
        entity.setDevice(device);
        entity.setStateHex(stateHex);
        entity.setWeatherMetric(weatherMetric);
        entity.setComparisonType(comparisonType);
        entity.setThresholdValue(thresholdValue);

        return toHeatPumpResponse(weatherControlHeatPumpRepository.save(entity));
    }

    public void deleteWeatherControlHeatPump(Long accountId, Long weatherControlHeatPumpId) {
        WeatherControlHeatPumpEntity entity = weatherControlHeatPumpRepository.findById(weatherControlHeatPumpId)
                .orElseThrow(() -> new EntityNotFoundException("WeatherControlHeatPump not found with id: " + weatherControlHeatPumpId));
        ensureOwnership(accountId, entity.getWeatherControl().getAccount().getId());
        weatherControlHeatPumpRepository.delete(entity);
    }

    public List<WeatherControlHeatPumpResponse> getWeatherControlHeatPumps(Long accountId, Long weatherControlId) {
        WeatherControlEntity weatherControl = getAccessibleWeatherControl(accountId, weatherControlId);
        Set<WeatherControlHeatPumpEntity> entities = weatherControl.getWeatherControlHeatPumps();
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toHeatPumpResponse)
                .sorted(Comparator.comparing(WeatherControlHeatPumpResponse::getId))
                .toList();
    }

    public SiteWeatherForecastResponse getStoredWeatherForecast(Long accountId, Long weatherControlId, Instant start, Instant end) {
        WeatherControlEntity weatherControl = getAccessibleWeatherControl(accountId, weatherControlId);
        return siteWeatherService.getStoredForecastForSite(weatherControl.getSite(), start, end);
    }

    private WeatherControlEntity getOwnedWeatherControl(Long accountId, Long weatherControlId) {
        return weatherControlRepository.findByIdAndAccountId(weatherControlId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Weather control not found for your account with ID: " + weatherControlId));
    }

    private WeatherControlEntity getAccessibleWeatherControl(Long accountId, Long weatherControlId) {
        return weatherControlRepository.findByIdAndAccountId(weatherControlId, accountId)
                .orElseGet(() -> {
                    if (!isSharedWeatherControl(accountId, weatherControlId)) {
                        throw new IllegalArgumentException("Weather control not found for your account with ID: " + weatherControlId);
                    }
                    return weatherControlRepository.findById(weatherControlId)
                            .orElseThrow(() -> new IllegalArgumentException("Shared weather control not found with ID: " + weatherControlId));
                });
    }

    private boolean isSharedWeatherControl(Long accountId, Long weatherControlId) {
        return resourceSharingRepository.existsByReceiverAccountIdAndResourceTypeAndWeatherControlIdAndEnabledTrue(
                accountId,
                ResourceType.WEATHER_CONTROL,
                weatherControlId
        );
    }

    private DeviceEntity getOwnedDevice(Long accountId, Long deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Device not found with id: " + deviceId));
        ensureOwnership(accountId, device.getAccount().getId());
        return device;
    }

    private void ensureOwnership(Long accountId, Long resourceAccountId) {
        if (!resourceAccountId.equals(accountId)) {
            throw new IllegalStateException("Forbidden!");
        }
    }

    private WeatherControlResponse toResponse(WeatherControlEntity entity) {
        return toResponse(entity, false);
    }

    private WeatherControlResponse toResponse(WeatherControlEntity entity, boolean shared) {
        return WeatherControlResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .siteId(entity.getSite().getId())
                .siteName(entity.getSite().getName())
                .siteTimezone(entity.getSite().getTimezone())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .shared(shared)
                .build();
    }

    private WeatherControlDeviceResponse toDeviceResponse(WeatherControlDeviceEntity entity) {
        return WeatherControlDeviceResponse.builder()
                .id(entity.getId())
                .weatherControlId(entity.getWeatherControl().getId())
                .deviceId(entity.getDevice().getId())
                .deviceChannel(entity.getDeviceChannel())
                .weatherMetric(entity.getWeatherMetric())
                .comparisonType(entity.getComparisonType())
                .thresholdValue(entity.getThresholdValue())
                .controlAction(entity.getControlAction())
                .priorityRule(entity.isPriorityRule())
                .device(toDeviceResponse(entity.getDevice()))
                .build();
    }

    private WeatherControlHeatPumpResponse toHeatPumpResponse(WeatherControlHeatPumpEntity entity) {
        return WeatherControlHeatPumpResponse.builder()
                .id(entity.getId())
                .weatherControlId(entity.getWeatherControl().getId())
                .deviceId(entity.getDevice().getId())
                .stateHex(entity.getStateHex())
                .weatherMetric(entity.getWeatherMetric())
                .comparisonType(entity.getComparisonType())
                .thresholdValue(entity.getThresholdValue())
                .device(toDeviceResponse(entity.getDevice()))
                .build();
    }

    private DeviceResponse toDeviceResponse(DeviceEntity entity) {
        return DeviceResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .deviceType(entity.getDeviceType())
                .deviceName(entity.getDeviceName())
                .lastCommunication(entity.getLastCommunication())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

}
