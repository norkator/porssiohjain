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
import com.nitramite.porssiohjain.entity.PowerplantElementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.PowerplantElementType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PowerplantElementRepository;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerplantElementResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PowerplantService {

    private static final int MIN_CHANNEL = 0;
    private static final int MAX_CHANNEL = 3;

    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final PowerplantElementRepository powerplantElementRepository;
    private final DemoAccountGuard demoAccountGuard;
    private final ControlService controlService;

    @Transactional(readOnly = true)
    public List<PowerplantElementResponse> getElements(Long accountId) {
        validateAccount(accountId);
        return powerplantElementRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(this::mapElement)
                .toList();
    }

    @Transactional
    public PowerplantElementResponse saveElement(
            Long accountId,
            Long elementId,
            String name,
            PowerplantElementType elementType,
            String iconName,
            BigDecimal displayValue,
            String displayUnit,
            Long deviceId,
            Integer deviceChannel,
            int canvasX,
            int canvasY
    ) {
        demoAccountGuard.assertWritable(accountId);
        AccountEntity account = validateAccount(accountId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Element name is required");
        }
        if (elementType == null) {
            throw new IllegalArgumentException("Element type is required");
        }
        if (iconName == null || iconName.isBlank()) {
            throw new IllegalArgumentException("Icon is required");
        }

        DeviceEntity device = null;
        if (deviceId != null) {
            device = validateAccountDevice(accountId, deviceId);
        }
        if (elementType == PowerplantElementType.DEVICE_CONTROL) {
            if (device == null) {
                throw new IllegalArgumentException("Device control element requires a standard device");
            }
            validateStandardRelayDevice(device);
            validateChannel(deviceChannel);
        } else {
            deviceChannel = null;
        }

        PowerplantElementEntity entity = elementId != null
                ? powerplantElementRepository.findByIdAndAccountId(elementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant element not found: " + elementId))
                : new PowerplantElementEntity();

        entity.setAccount(account);
        entity.setName(name.strip());
        entity.setElementType(elementType);
        entity.setIconName(iconName);
        entity.setDisplayValue(displayValue);
        entity.setDisplayUnit(displayUnit != null && !displayUnit.isBlank() ? displayUnit.strip() : null);
        entity.setDevice(device);
        entity.setDeviceChannel(deviceChannel);
        entity.setCanvasX(Math.max(0, canvasX));
        entity.setCanvasY(Math.max(0, canvasY));

        return mapElement(powerplantElementRepository.save(entity));
    }

    @Transactional
    public void updateElementPosition(Long accountId, Long elementId, int canvasX, int canvasY) {
        demoAccountGuard.assertWritable(accountId);
        PowerplantElementEntity entity = powerplantElementRepository.findByIdAndAccountId(elementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant element not found: " + elementId));
        entity.setCanvasX(Math.max(0, canvasX));
        entity.setCanvasY(Math.max(0, canvasY));
        powerplantElementRepository.save(entity);
    }

    @Transactional
    public void deleteElement(Long accountId, Long elementId) {
        demoAccountGuard.assertWritable(accountId);
        PowerplantElementEntity entity = powerplantElementRepository.findByIdAndAccountId(elementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant element not found: " + elementId));
        powerplantElementRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public void sendDeviceControl(Long accountId, Long elementId, boolean on) {
        demoAccountGuard.assertWritable(accountId);
        PowerplantElementEntity entity = powerplantElementRepository.findByIdAndAccountId(elementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant element not found: " + elementId));
        if (entity.getElementType() != PowerplantElementType.DEVICE_CONTROL || entity.getDevice() == null) {
            throw new IllegalArgumentException("Element is not a device control");
        }
        validateStandardRelayDevice(entity.getDevice());
        validateChannel(entity.getDeviceChannel());
        controlService.sendDebugMqttRelayCommand(
                accountId,
                entity.getDevice().getId(),
                entity.getDeviceChannel(),
                on
        );
    }

    private AccountEntity validateAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
    }

    private DeviceEntity validateAccountDevice(Long accountId, Long deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + deviceId));
        if (device.getAccount() == null || !device.getAccount().getId().equals(accountId)) {
            throw new IllegalStateException("Forbidden!");
        }
        return device;
    }

    private void validateStandardRelayDevice(DeviceEntity device) {
        if (device.getDeviceType() != DeviceType.STANDARD) {
            throw new IllegalArgumentException("Only standard relay devices are supported");
        }
    }

    private void validateChannel(Integer channel) {
        if (channel == null || channel < MIN_CHANNEL || channel > MAX_CHANNEL) {
            throw new IllegalArgumentException("Unsupported relay channel: " + channel);
        }
    }

    private PowerplantElementResponse mapElement(PowerplantElementEntity entity) {
        return PowerplantElementResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .elementType(entity.getElementType())
                .iconName(entity.getIconName())
                .displayValue(entity.getDisplayValue())
                .displayUnit(entity.getDisplayUnit())
                .device(entity.getDevice() != null ? mapDevice(entity.getDevice()) : null)
                .deviceChannel(entity.getDeviceChannel())
                .canvasX(entity.getCanvasX())
                .canvasY(entity.getCanvasY())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private DeviceResponse mapDevice(DeviceEntity device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .uuid(device.getUuid())
                .deviceType(device.getDeviceType())
                .enabled(device.isEnabled())
                .deviceName(device.getDeviceName())
                .timezone(device.getTimezone())
                .lastCommunication(device.getLastCommunication())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .accountId(device.getAccount().getId())
                .apiOnline(device.isApiOnline())
                .mqttOnline(device.isMqttOnline())
                .mqttUsername(device.getMqttUsername())
                .mqttPassword(device.getMqttPassword())
                .build();
    }

}
