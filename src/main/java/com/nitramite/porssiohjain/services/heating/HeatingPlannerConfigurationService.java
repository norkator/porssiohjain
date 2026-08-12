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

package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomHeatSourceEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerSettingsEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerSettingsRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HeatingPlannerConfigurationService {

    private final AccountRepository accountRepository;
    private final SiteRepository siteRepository;
    private final DeviceRepository deviceRepository;
    private final ElectricityContractRepository electricityContractRepository;
    private final HeatingPlannerSettingsRepository settingsRepository;
    private final HeatingPlannerRoomRepository roomRepository;

    @Transactional(readOnly = true)
    public Optional<Long> preferredSiteId(Long accountId) {
        List<HeatingPlannerSettingsEntity> settings = settingsRepository.findByAccountIdOrderByUpdatedAtDesc(accountId);
        return settings.stream()
                .filter(HeatingPlannerSettingsEntity::isEnabled)
                .findFirst()
                .or(() -> settings.stream().findFirst())
                .map(settingsEntity -> settingsEntity.getSite().getId());
    }

    @Transactional(readOnly = true)
    public Configuration configuration(Long accountId, Long siteId) {
        Optional<HeatingPlannerSettingsEntity> settings = settingsRepository.findByAccountIdAndSiteId(accountId, siteId);
        if (settings.isEmpty()) {
            return new Configuration(false, new BigDecimal("5.00"), new BigDecimal("0.00"),
                    new BigDecimal("25.50"), null, false, LocalTime.of(6, 0), LocalTime.of(22, 0),
                    new BigDecimal("8.00"), 45, 360, new BigDecimal("0.2500"),
                    new BigDecimal("0.7500"), List.of());
        }
        HeatingPlannerSettingsEntity settingsEntity = settings.get();
        List<RoomConfiguration> rooms = roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(settingsEntity.getId())
                .stream()
                .map(room -> {
                    HeatingPlannerRoomHeatSourceEntity source = room.getHeatSources().stream()
                            .findFirst()
                            .orElse(null);
                    return new RoomConfiguration(
                            room.getName(),
                            source == null ? HeatingPlannerHeatSourceType.OTHER : source.getSourceType(),
                            room.getTargetRoomTemperature(),
                            room.getNormalFloorTemperature(),
                            room.getMaximumPreheatFloorTemperature(),
                            room.getAbsoluteMaximumFloorTemperature(),
                            room.getDischargeFloorSetpoint(),
                            source == null || source.getControllingDevice() == null
                                    ? null : source.getControllingDevice().getId(),
                            room.getRoomSensorDevice() == null ? null : room.getRoomSensorDevice().getId(),
                            room.getFloorSensorDevice() == null ? null : room.getFloorSensorDevice().getId()
                    );
                })
                .toList();
        return new Configuration(settingsEntity.isEnabled(), settingsEntity.getPlannerActiveBelowTemperature(),
                settingsEntity.getWoodRecommendationBelowTemperature(), settingsEntity.getTaxPercent(),
                settingsEntity.getTransferContract() == null ? null : settingsEntity.getTransferContract().getId(),
                settingsEntity.isStoveLoaded(), settingsEntity.getStoveAvailableFrom(), settingsEntity.getStoveAvailableTo(),
                settingsEntity.getWoodAmount(), settingsEntity.getWoodReleaseDelayMinutes(),
                settingsEntity.getWoodReleaseDurationMinutes(), settingsEntity.getCheapPricePercentile(),
                settingsEntity.getExpensivePricePercentile(), rooms);
    }

    @Transactional
    public void setEnabled(Long accountId, Long siteId, boolean enabled) {
        HeatingPlannerSettingsEntity settings = requireOrCreateSettings(accountId, siteId);
        settings.setEnabled(enabled);
        settingsRepository.save(settings);
    }

    @Transactional
    public void saveSettings(Long accountId, Long siteId, SettingsConfiguration settingsConfiguration) {
        validatePricePercentiles(settingsConfiguration);
        HeatingPlannerSettingsEntity settings = requireOrCreateSettings(accountId, siteId);
        settings.setEnabled(settingsConfiguration.enabled());
        settings.setPlannerActiveBelowTemperature(settingsConfiguration.plannerActiveBelowTemperature());
        settings.setWoodRecommendationBelowTemperature(settingsConfiguration.woodRecommendationBelowTemperature());
        settings.setTaxPercent(settingsConfiguration.taxPercent());
        settings.setStoveLoaded(settingsConfiguration.stoveLoaded());
        settings.setStoveAvailableFrom(settingsConfiguration.stoveAvailableFrom());
        settings.setStoveAvailableTo(settingsConfiguration.stoveAvailableTo());
        settings.setWoodAmount(settingsConfiguration.woodAmount());
        settings.setWoodReleaseDelayMinutes(settingsConfiguration.woodReleaseDelayMinutes());
        settings.setWoodReleaseDurationMinutes(settingsConfiguration.woodReleaseDurationMinutes());
        settings.setCheapPricePercentile(settingsConfiguration.cheapPricePercentile());
        settings.setExpensivePricePercentile(settingsConfiguration.expensivePricePercentile());
        ElectricityContractEntity transferContract = settingsConfiguration.transferContractId() == null ? null
                : electricityContractRepository.findByIdAndAccountId(settingsConfiguration.transferContractId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer contract not found"));
        settings.setTransferContract(transferContract);
        settingsRepository.save(settings);
    }

    private HeatingPlannerSettingsEntity requireOrCreateSettings(Long accountId, Long siteId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        SiteEntity site = siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        HeatingPlannerSettingsEntity settings = settingsRepository.findByAccountIdAndSiteId(accountId, siteId)
                .orElseGet(() -> HeatingPlannerSettingsEntity.builder()
                        .account(account)
                        .site(site)
                        .build());
        settings.setTimezone(site.getTimezone());
        return settings;
    }

    @Transactional
    public void save(Long accountId, Long siteId, SettingsConfiguration settingsConfiguration,
                     List<RoomConfiguration> rooms) {
        validatePricePercentiles(settingsConfiguration);
        if (rooms == null || rooms.isEmpty()) {
            throw new IllegalArgumentException("Add at least one room before saving");
        }
        Set<String> normalizedRoomNames = new HashSet<>();
        for (RoomConfiguration roomConfiguration : rooms) {
            String roomName = roomConfiguration.name() == null ? "" : roomConfiguration.name().trim();
            if (roomName.isBlank()) {
                throw new IllegalArgumentException("Room name is required");
            }
            if (!normalizedRoomNames.add(roomName.toLowerCase())) {
                throw new IllegalArgumentException("Room names must be unique");
            }
        }
        HeatingPlannerSettingsEntity settings = requireOrCreateSettings(accountId, siteId);
        AccountEntity account = settings.getAccount();
        SiteEntity site = settings.getSite();
        settings.setEnabled(settingsConfiguration.enabled());
        settings.setPlannerActiveBelowTemperature(settingsConfiguration.plannerActiveBelowTemperature());
        settings.setWoodRecommendationBelowTemperature(settingsConfiguration.woodRecommendationBelowTemperature());
        settings.setTaxPercent(settingsConfiguration.taxPercent());
        settings.setStoveLoaded(settingsConfiguration.stoveLoaded());
        settings.setStoveAvailableFrom(settingsConfiguration.stoveAvailableFrom());
        settings.setStoveAvailableTo(settingsConfiguration.stoveAvailableTo());
        settings.setWoodAmount(settingsConfiguration.woodAmount());
        settings.setWoodReleaseDelayMinutes(settingsConfiguration.woodReleaseDelayMinutes());
        settings.setWoodReleaseDurationMinutes(settingsConfiguration.woodReleaseDurationMinutes());
        settings.setCheapPricePercentile(settingsConfiguration.cheapPricePercentile());
        settings.setExpensivePricePercentile(settingsConfiguration.expensivePricePercentile());
        ElectricityContractEntity transferContract = settingsConfiguration.transferContractId() == null ? null
                : electricityContractRepository.findByIdAndAccountId(settingsConfiguration.transferContractId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer contract not found"));
        settings.setTransferContract(transferContract);
        settings = settingsRepository.save(settings);

        List<HeatingPlannerRoomEntity> existingRooms = roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(settings.getId());
        Map<String, HeatingPlannerRoomEntity> existingByName = new HashMap<>();
        existingRooms.forEach(room -> existingByName.put(room.getName().toLowerCase(Locale.ROOT), room));

        int sortOrder = 0;
        for (RoomConfiguration roomConfiguration : rooms) {
            String roomName = roomConfiguration.name() == null ? "" : roomConfiguration.name().trim();
            if (roomName.isBlank()) {
                throw new IllegalArgumentException("Room name is required");
            }
            BigDecimal target = roomConfiguration.targetRoomTemperature() == null
                    ? new BigDecimal("21.00") : roomConfiguration.targetRoomTemperature();
            BigDecimal normalFloor = roomConfiguration.normalFloorTemperature() == null
                    ? new BigDecimal("23.00") : roomConfiguration.normalFloorTemperature();
            BigDecimal maximumPreheatFloor = roomConfiguration.maximumPreheatFloorTemperature() == null
                    ? new BigDecimal("27.00") : roomConfiguration.maximumPreheatFloorTemperature();
            BigDecimal absoluteMaximumFloor = roomConfiguration.absoluteMaximumFloorTemperature() == null
                    ? new BigDecimal("29.00") : roomConfiguration.absoluteMaximumFloorTemperature();
            BigDecimal dischargeFloor = roomConfiguration.dischargeFloorSetpoint() == null
                    ? new BigDecimal("19.00") : roomConfiguration.dischargeFloorSetpoint();
            HeatingPlannerHeatSourceType sourceType = roomConfiguration.sourceType() == null
                    ? HeatingPlannerHeatSourceType.OTHER : roomConfiguration.sourceType();
            HeatingPlannerRoomEntity room = existingByName.remove(roomName.toLowerCase(Locale.ROOT));
            if (room == null) {
                room = HeatingPlannerRoomEntity.builder().settings(settings).account(account).site(site).build();
            }
            room.setName(roomName);
            room.setTargetRoomTemperature(target);
            room.setNormalFloorTemperature(normalFloor);
            room.setMaximumPreheatFloorTemperature(maximumPreheatFloor);
            room.setAbsoluteMaximumFloorTemperature(absoluteMaximumFloor);
            room.setDischargeFloorSetpoint(dischargeFloor);
            room.setMinimumRoomTemperature(target.subtract(BigDecimal.ONE));
            room.setMaximumRoomTemperature(target.add(new BigDecimal("2.50")));
            room.setSortOrder(sortOrder);
            DeviceEntity controller = roomConfiguration.controllingDeviceId() == null ? null
                    : deviceRepository.findByIdAndAccount(roomConfiguration.controllingDeviceId(), account)
                    .orElseThrow(() -> new IllegalArgumentException("Selected controlling device not found"));
            if (controller != null && controller.getDeviceType() != DeviceType.THERMOSTAT) {
                throw new IllegalArgumentException("Selected controlling device is not a thermostat");
            }
            DeviceEntity roomSensor = roomConfiguration.roomSensorDeviceId() == null ? null
                    : deviceRepository.findByIdAndAccount(roomConfiguration.roomSensorDeviceId(), account)
                    .orElseThrow(() -> new IllegalArgumentException("Selected room sensor not found"));
            if (roomSensor != null && roomSensor.getDeviceType() != DeviceType.TEMPERATURE_SENSOR) {
                throw new IllegalArgumentException("Selected room sensor is not a temperature sensor");
            }
            room.setRoomSensorDevice(roomSensor);
            room.setRoomSensorMeasurementKey(roomSensor == null ? null : HeatingPlannerMeasurementService.DEFAULT_TEMPERATURE_KEY);
            DeviceEntity floorSensor = roomConfiguration.floorSensorDeviceId() == null ? null
                    : deviceRepository.findByIdAndAccount(roomConfiguration.floorSensorDeviceId(), account)
                    .orElseThrow(() -> new IllegalArgumentException("Selected floor sensor not found"));
            if (floorSensor != null && floorSensor.getDeviceType() != DeviceType.TEMPERATURE_SENSOR
                    && floorSensor.getDeviceType() != DeviceType.THERMOSTAT) {
                throw new IllegalArgumentException("Selected floor sensor must report temperature");
            }
            room.setFloorSensorDevice(floorSensor);
            room.setFloorSensorMeasurementKey(floorSensor == null ? null : HeatingPlannerMeasurementService.DEFAULT_TEMPERATURE_KEY);
            HeatingPlannerRoomHeatSourceEntity heatSource = room.getHeatSources().stream().findFirst().orElse(null);
            if (heatSource == null) {
                heatSource = HeatingPlannerRoomHeatSourceEntity.builder()
                        .room(room).account(account).site(site).sortOrder(0).build();
                room.getHeatSources().add(heatSource);
            }
            heatSource.setName(sourceType.label());
            heatSource.setSourceType(sourceType);
            heatSource.setControllingDevice(controller);
            roomRepository.save(room);
            sortOrder++;
        }
        roomRepository.deleteAll(existingByName.values());
    }

    private void validatePricePercentiles(SettingsConfiguration settingsConfiguration) {
        BigDecimal cheap = settingsConfiguration.cheapPricePercentile();
        BigDecimal expensive = settingsConfiguration.expensivePricePercentile();
        if (cheap == null || expensive == null
                || cheap.compareTo(BigDecimal.ZERO) < 0
                || expensive.compareTo(BigDecimal.ONE) > 0
                || cheap.compareTo(expensive) >= 0) {
            throw new IllegalArgumentException("Cheap price percentile must be below expensive price percentile");
        }
    }

    public record Configuration(
            boolean enabled,
            BigDecimal plannerActiveBelowTemperature,
            BigDecimal woodRecommendationBelowTemperature,
            BigDecimal taxPercent,
            Long transferContractId,
            boolean stoveLoaded,
            LocalTime stoveAvailableFrom,
            LocalTime stoveAvailableTo,
            BigDecimal woodAmount,
            Integer woodReleaseDelayMinutes,
            Integer woodReleaseDurationMinutes,
            BigDecimal cheapPricePercentile,
            BigDecimal expensivePricePercentile,
            List<RoomConfiguration> rooms
    ) {
    }

    public record SettingsConfiguration(
            boolean enabled,
            BigDecimal plannerActiveBelowTemperature,
            BigDecimal woodRecommendationBelowTemperature,
            BigDecimal taxPercent,
            Long transferContractId,
            boolean stoveLoaded,
            LocalTime stoveAvailableFrom,
            LocalTime stoveAvailableTo,
            BigDecimal woodAmount,
            Integer woodReleaseDelayMinutes,
            Integer woodReleaseDurationMinutes,
            BigDecimal cheapPricePercentile,
            BigDecimal expensivePricePercentile
    ) {
    }

    public record RoomConfiguration(
            String name,
            HeatingPlannerHeatSourceType sourceType,
            BigDecimal targetRoomTemperature,
            BigDecimal normalFloorTemperature,
            BigDecimal maximumPreheatFloorTemperature,
            BigDecimal absoluteMaximumFloorTemperature,
            BigDecimal dischargeFloorSetpoint,
            Long controllingDeviceId,
            Long roomSensorDeviceId,
            Long floorSensorDeviceId
    ) {
    }
}
