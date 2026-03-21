/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.mappers.DeviceMapper;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final AccountRepository accountRepository;
    private final ControlRepository controlRepository;
    private final PowerLimitRepository powerLimitRepository;
    private final ResourceSharingRepository resourceSharingRepository;

    @Transactional
    public DeviceResponse createDevice(
            Long authAccountId, Long accountId, String deviceName, String timezone
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        if (account.getId().equals(authAccountId)) {
            DeviceEntity device = DeviceEntity.builder()
                    .deviceName(deviceName)
                    .timezone(timezone)
                    .lastCommunication(null)
                    .account(account)
                    .build();

            return DeviceMapper.toResponse(deviceRepository.save(device));
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listDevices(Long authAccountId, Long accountId) {
        if (accountId.equals(authAccountId)) {
            return DeviceMapper.toResponseList(deviceRepository.findByAccountId(accountId));
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDevice(Long authAccountId, Long deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        if (device.getAccount().getId().equals(authAccountId)) {
            return DeviceMapper.toResponse(device);
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevicesForControlId(
            Long controlId
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));
        List<DeviceEntity> deviceEntities = deviceRepository.findByAccountId(control.getAccount().getId());
        return deviceEntities.stream()
                .map(entity -> DeviceResponse.builder()
                        .id(entity.getId())
                        .uuid(entity.getUuid())
                        .deviceName(entity.getDeviceName())
                        .timezone(entity.getTimezone())
                        .lastCommunication(entity.getLastCommunication())
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevices(
            Long accountId
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        List<DeviceEntity> ownDevices = deviceRepository.findByAccountIdOrderByIdAsc(account.getId());

        List<ResourceSharingEntity> sharedResources =
                resourceSharingRepository.findByReceiverAccountIdAndResourceTypeAndEnabledTrue(
                        accountId,
                        ResourceType.DEVICE
                );
        List<Long> sharedDeviceIds = sharedResources.stream()
                .map(ResourceSharingEntity::getDeviceId)
                .filter(Objects::nonNull)
                .toList();
        List<DeviceEntity> sharedDevices = sharedDeviceIds.isEmpty()
                ? List.of()
                : deviceRepository.findAllById(sharedDeviceIds);
        List<DeviceResponse> responses = new ArrayList<>();

        ownDevices.forEach(entity -> responses.add(DeviceResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .deviceName(entity.getDeviceName())
                .timezone(entity.getTimezone())
                .lastCommunication(entity.getLastCommunication())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .accountId(entity.getAccount().getId())
                .shared(false)
                .apiOnline(entity.isApiOnline())
                .mqttOnline(entity.isMqttOnline())
                .mqttUsername(entity.getMqttUsername())
                .mqttPassword(entity.getMqttPassword())
                .build()));

        sharedDevices.forEach(entity -> responses.add(DeviceResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .deviceName(entity.getDeviceName())
                .timezone(entity.getTimezone())
                .lastCommunication(entity.getLastCommunication())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .accountId(entity.getAccount().getId())
                .shared(true)
                .apiOnline(entity.isApiOnline())
                .mqttOnline(entity.isMqttOnline())
                .mqttUsername(entity.getMqttUsername())
                .mqttPassword(entity.getMqttPassword())
                .build()));

        return responses;
    }

    @Transactional
    public void updateDevice(
            Long accountId, Long deviceId, String newName, String newTimezone
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        DeviceEntity device = deviceRepository.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        device.setDeviceName(newName);
        device.setTimezone(newTimezone);
        deviceRepository.save(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevicesForPowerLimitId(Long powerLimitId) {
        PowerLimitEntity limit = powerLimitRepository.findById(powerLimitId)
                .orElseThrow(() -> new IllegalArgumentException("Power limit not found: " + powerLimitId));
        Long accountId = limit.getAccount().getId();
        // List<Long> linkedIds = powerLimitDeviceRepository.findDeviceIdsByPowerLimitId(powerLimitId);
        return deviceRepository.findByAccountId(accountId).stream()
                // .filter(d -> !linkedIds.contains(d.getId()))
                .map(this::mapDeviceToResponse)
                .collect(Collectors.toList());
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

    public void checkOfflineDevices() {
        Instant threshold = Instant.now().minusSeconds(300);
        List<DeviceEntity> apiDevices = deviceRepository.findByApiOnlineTrueAndLastCommunicationBefore(threshold);
        for (DeviceEntity device : apiDevices) {
            device.setApiOnline(false);
            deviceRepository.save(device);
        }
        List<DeviceEntity> mqttDevices = deviceRepository.findByMqttOnlineTrueAndLastCommunicationBefore(threshold);
        for (DeviceEntity device : mqttDevices) {
            device.setMqttOnline(false);
            deviceRepository.save(device);
        }
    }

}