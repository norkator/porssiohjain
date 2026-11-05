/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.simulation;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.AdminAuthorizationService;
import com.nitramite.porssiohjain.services.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceSimulationService {
    private final DeviceRepository devices;
    private final DeviceAcDataRepository acData;
    private final AccountRepository accounts;
    private final AdminAuthorizationService authorization;
    private final AccountLimitService limits;
    private final SystemLogService systemLog;

    @Transactional(readOnly = true)
    public List<SimulatedDevice> inventory() {
        return devices.findBySimulatedTrueAndEnabledTrueAndAccountDemoTrueOrderByIdAsc().stream()
                .filter(this::supported).map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDevice> list(Long adminId, Long accountId) throws AccessDeniedException {
        requireAdmin(adminId);
        requireDemo(accountId);
        return devices.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(d -> new AdminDevice(d.getId(), d.getDeviceName(), d.getDeviceType(), d.isSimulated(), supported(d)))
                .toList();
    }

    @Transactional
    public void setSimulated(Long adminId, Long accountId, Long deviceId, boolean simulated) throws AccessDeniedException {
        requireAdmin(adminId);
        var account = requireDemo(accountId);
        var device = devices.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found for demo account"));
        if (simulated && !supported(device)) {
            throw new IllegalArgumentException("Simulation supports generic relays and Toshiba heat pumps only");
        }
        if (device.isSimulated() == simulated) return;
        device.setSimulated(simulated);
        device.setApiOnline(false);
        device.setMqttOnline(false);
        device.setLastCommunication(null);
        devices.save(device);
        acData.findByDevice(device).ifPresent(data -> {
            data.setLastSentStateHex(null);
            data.setLastPolledStateHex(null);
            acData.save(data);
        });
        systemLog.log("Admin account " + adminId + " set device " + deviceId + " simulation=" + simulated);
    }

    @Transactional
    public UUID createHeatPump(Long adminId, Long accountId) throws AccessDeniedException {
        requireAdmin(adminId);
        var account = requireDemo(accountId);
        accounts.findWithLockById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
        limits.assertCanCreateDevice(accountId);
        var device = devices.save(DeviceEntity.builder().account(account).deviceName("Demo Toshiba")
                .timezone("Europe/Helsinki").deviceType(DeviceType.HEAT_PUMP)
                .devicePlatform(DevicePlatform.GENERIC_MQTT).simulated(true).enabled(true).build());
        acData.save(DeviceAcDataEntity.builder().device(device).name("Demo Toshiba").acType(AcType.TOSHIBA)
                .acDeviceId(device.getUuid().toString()).build());
        systemLog.log("Admin account " + adminId + " created simulated Toshiba device " + device.getId());
        return device.getUuid();
    }

    private boolean supported(DeviceEntity device) {
        if (device.getDevicePlatform() != DevicePlatform.GENERIC_MQTT) return false;
        if (device.getDeviceType() == DeviceType.STANDARD) return true;
        return device.getDeviceType() == DeviceType.HEAT_PUMP && acData.findByDevice(device)
                .map(data -> data.getAcType() == AcType.TOSHIBA).orElse(false);
    }

    private SimulatedDevice summary(DeviceEntity device) {
        return new SimulatedDevice(device.getUuid(), device.getDeviceName(), device.getDeviceType(), 4);
    }

    private void requireAdmin(Long adminId) throws AccessDeniedException {
        if (authorization.requireAdmin(adminId).isDemo()) throw new AccessDeniedException("Admin access required");
    }

    private AccountEntity requireDemo(Long accountId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!account.isDemo() || account.isAdmin()) throw new IllegalArgumentException("Simulation requires a non-admin demo account");
        return account;
    }

    public record SimulatedDevice(UUID uuid, String name, DeviceType type, int relayCount) {}
    public record AdminDevice(Long id, String name, DeviceType type, boolean simulated, boolean supported) {}
}
