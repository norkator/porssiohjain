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
import java.util.HashSet;
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
                    new BigDecimal("25.50"), null, List.of());
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
                            source == null || source.getControllingDevice() == null
                                    ? null : source.getControllingDevice().getId()
                    );
                })
                .toList();
        return new Configuration(settingsEntity.isEnabled(), settingsEntity.getPlannerActiveBelowTemperature(),
                settingsEntity.getWoodRecommendationBelowTemperature(), settingsEntity.getTaxPercent(),
                settingsEntity.getTransferContract() == null ? null : settingsEntity.getTransferContract().getId(), rooms);
    }

    @Transactional
    public void setEnabled(Long accountId, Long siteId, boolean enabled) {
        HeatingPlannerSettingsEntity settings = requireOrCreateSettings(accountId, siteId);
        settings.setEnabled(enabled);
        settingsRepository.save(settings);
    }

    @Transactional
    public void saveSettings(Long accountId, Long siteId, SettingsConfiguration settingsConfiguration) {
        HeatingPlannerSettingsEntity settings = requireOrCreateSettings(accountId, siteId);
        settings.setEnabled(settingsConfiguration.enabled());
        settings.setPlannerActiveBelowTemperature(settingsConfiguration.plannerActiveBelowTemperature());
        settings.setWoodRecommendationBelowTemperature(settingsConfiguration.woodRecommendationBelowTemperature());
        settings.setTaxPercent(settingsConfiguration.taxPercent());
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
        ElectricityContractEntity transferContract = settingsConfiguration.transferContractId() == null ? null
                : electricityContractRepository.findByIdAndAccountId(settingsConfiguration.transferContractId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer contract not found"));
        settings.setTransferContract(transferContract);
        settings = settingsRepository.save(settings);

        List<HeatingPlannerRoomEntity> existingRooms = roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(settings.getId());
        roomRepository.deleteAll(existingRooms);
        roomRepository.flush();

        int sortOrder = 0;
        for (RoomConfiguration roomConfiguration : rooms) {
            String roomName = roomConfiguration.name() == null ? "" : roomConfiguration.name().trim();
            if (roomName.isBlank()) {
                throw new IllegalArgumentException("Room name is required");
            }
            BigDecimal target = roomConfiguration.targetRoomTemperature() == null
                    ? new BigDecimal("21.00") : roomConfiguration.targetRoomTemperature();
            HeatingPlannerHeatSourceType sourceType = roomConfiguration.sourceType() == null
                    ? HeatingPlannerHeatSourceType.OTHER : roomConfiguration.sourceType();
            HeatingPlannerRoomEntity room = HeatingPlannerRoomEntity.builder()
                    .settings(settings)
                    .account(account)
                    .site(site)
                    .name(roomName)
                    .targetRoomTemperature(target)
                    .minimumRoomTemperature(target.subtract(BigDecimal.ONE))
                    .maximumRoomTemperature(target.add(new BigDecimal("2.50")))
                    .sortOrder(sortOrder)
                    .build();
            DeviceEntity controller = roomConfiguration.controllingDeviceId() == null ? null
                    : deviceRepository.findByIdAndAccount(roomConfiguration.controllingDeviceId(), account)
                    .orElseThrow(() -> new IllegalArgumentException("Selected controlling device not found"));
            room.getHeatSources().add(HeatingPlannerRoomHeatSourceEntity.builder()
                    .room(room)
                    .account(account)
                    .site(site)
                    .name(sourceType.label())
                    .sourceType(sourceType)
                    .controllingDevice(controller)
                    .sortOrder(0)
                    .build());
            roomRepository.save(room);
            sortOrder++;
        }
    }

    public record Configuration(
            boolean enabled,
            BigDecimal plannerActiveBelowTemperature,
            BigDecimal woodRecommendationBelowTemperature,
            BigDecimal taxPercent,
            Long transferContractId,
            List<RoomConfiguration> rooms
    ) {
    }

    public record SettingsConfiguration(
            boolean enabled,
            BigDecimal plannerActiveBelowTemperature,
            BigDecimal woodRecommendationBelowTemperature,
            BigDecimal taxPercent,
            Long transferContractId
    ) {
    }

    public record RoomConfiguration(
            String name,
            HeatingPlannerHeatSourceType sourceType,
            BigDecimal targetRoomTemperature,
            Long controllingDeviceId
    ) {
    }
}
