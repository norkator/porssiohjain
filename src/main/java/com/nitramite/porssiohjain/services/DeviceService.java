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
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.ResourceType;
import com.nitramite.porssiohjain.entity.repository.*;
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
    private final DeviceAcDataRepository deviceAcDataRepository;

    @Transactional
    public DeviceResponse createDevice(
            Long authAccountId, Long accountId, String deviceName, String timezone, DeviceType deviceType,
            String hpName, AcType acType, String acUsername, String acPassword
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        if (account.getId().equals(authAccountId)) {
            DeviceEntity device = DeviceEntity.builder()
                    .deviceName(deviceName)
                    .timezone(timezone)
                    .deviceType(deviceType)
                    .lastCommunication(null)
                    .account(account)
                    .build();
            device = deviceRepository.save(device);

            if (deviceType == DeviceType.HEAT_PUMP) {
                DeviceAcDataEntity acData = DeviceAcDataEntity.builder()
                        .device(device)
                        .name(hpName)
                        .acType(acType)
                        .acUsername(acUsername)
                        .acPassword(acPassword)
                        .build();
                deviceAcDataRepository.save(acData);
            }

            return mapToResponse(device, false);
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listDevices(Long authAccountId, Long accountId) {
        if (accountId.equals(authAccountId)) {
            return deviceRepository.findByAccountId(accountId).stream()
                    .map(d -> mapToResponse(d, false))
                    .toList();
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDevice(Long authAccountId, Long deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        if (device.getAccount().getId().equals(authAccountId)) {
            return mapToResponse(device, false);
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
                .map(d -> mapToResponse(d, false))
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

        ownDevices.forEach(entity -> responses.add(mapToResponse(entity, false)));
        sharedDevices.forEach(entity -> responses.add(mapToResponse(entity, true)));

        return responses;
    }

    private DeviceResponse mapToResponse(DeviceEntity entity, boolean isShared) {
        DeviceResponse response = DeviceResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .deviceType(entity.getDeviceType())
                .deviceName(entity.getDeviceName())
                .timezone(entity.getTimezone())
                .lastCommunication(entity.getLastCommunication())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .accountId(entity.getAccount().getId())
                .shared(isShared)
                .apiOnline(entity.isApiOnline())
                .mqttOnline(entity.isMqttOnline())
                .mqttUsername(entity.getMqttUsername())
                .mqttPassword(entity.getMqttPassword())
                .build();

        if (entity.getDeviceType() == DeviceType.HEAT_PUMP) {
            deviceAcDataRepository.findByDevice(entity).ifPresent(acData -> {
                response.setHpName(acData.getName());
                response.setAcType(acData.getAcType());
                response.setAcUsername(acData.getAcUsername());
                response.setAcPassword(acData.getAcPassword());
                response.setAcDeviceId(acData.getAcDeviceId());
            });
        }

        return response;
    }

    @Transactional(readOnly = true)
    public DeviceAcDataEntity getDeviceAcData(Long accountId, Long deviceId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
        DeviceEntity device = deviceRepository.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        return deviceAcDataRepository.findByDevice(device)
                .orElseThrow(() -> new EntityNotFoundException("AC data not found for device: " + deviceId));
    }

    @Transactional
    public void updateAcDeviceId(Long accountId, Long deviceId, String acDeviceId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));
        DeviceEntity device = deviceRepository.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
        DeviceAcDataEntity acData = deviceAcDataRepository.findByDevice(device)
                .orElseThrow(() -> new IllegalStateException("AC data not found"));
        acData.setAcDeviceId(acDeviceId);
        deviceAcDataRepository.save(acData);
    }

    @Transactional
    public void updateDevice(
            Long accountId, Long deviceId, String newName, String newTimezone, DeviceType deviceType,
            String hpName, AcType acType, String acUsername, String acPassword
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        DeviceEntity device = deviceRepository.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        device.setDeviceName(newName);
        device.setTimezone(newTimezone);
        device.setDeviceType(deviceType);
        deviceRepository.save(device);

        if (deviceType == DeviceType.HEAT_PUMP) {
            DeviceAcDataEntity acData = deviceAcDataRepository.findByDevice(device)
                    .orElseGet(() -> DeviceAcDataEntity.builder().device(device).build());
            acData.setName(hpName);
            acData.setAcType(acType);
            acData.setAcUsername(acUsername);
            acData.setAcPassword(acPassword);
            deviceAcDataRepository.save(acData);
        }
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevicesForPowerLimitId(Long powerLimitId) {
        PowerLimitEntity limit = powerLimitRepository.findById(powerLimitId)
                .orElseThrow(() -> new IllegalArgumentException("Power limit not found: " + powerLimitId));
        Long accountId = limit.getAccount().getId();
        // List<Long> linkedIds = powerLimitDeviceRepository.findDeviceIdsByPowerLimitId(powerLimitId);
        return deviceRepository.findByAccountId(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                // .filter(d -> !linkedIds.contains(d.getId()))
                .map(this::mapDeviceToResponse)
                .collect(Collectors.toList());
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
