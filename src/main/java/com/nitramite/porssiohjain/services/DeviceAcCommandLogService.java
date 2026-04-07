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
import com.nitramite.porssiohjain.entity.DeviceAcCommandLogEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceAcCommandLogRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.models.DeviceAcCommandLogResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAcCommandLogService {

    private static final int MAX_LOG_ROWS_PER_DEVICE = 100;

    private final DeviceAcCommandLogRepository deviceAcCommandLogRepository;
    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public void logSentCommand(Long deviceId, String sentData) {
        if (deviceId == null) {
            return;
        }

        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElse(null);
        if (device == null || device.getDeviceType() != DeviceType.HEAT_PUMP) {
            return;
        }
        if (sentData == null || sentData.isBlank()) {
            return;
        }

        DeviceAcCommandLogEntity logEntry = DeviceAcCommandLogEntity.builder()
                .device(device)
                .sentData(sentData)
                .sentAt(Instant.now())
                .build();
        deviceAcCommandLogRepository.save(logEntry);

        int deletedRows = deviceAcCommandLogRepository.deleteAllExceptNewest(device.getId(), MAX_LOG_ROWS_PER_DEVICE);
        if (deletedRows > 0) {
            log.debug("Pruned {} AC command log rows for deviceId={}", deletedRows, device.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<DeviceAcCommandLogResponse> getCommandLogs(Long accountId, Long deviceId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
        DeviceEntity device = deviceRepository.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        if (device.getDeviceType() != DeviceType.HEAT_PUMP) {
            throw new IllegalArgumentException("AC command log is only available for heat pump devices");
        }

        return deviceAcCommandLogRepository.findTop100ByDeviceOrderBySentAtDescIdDesc(device).stream()
                .map(entry -> DeviceAcCommandLogResponse.builder()
                        .id(entry.getId())
                        .sentData(entry.getSentData())
                        .sentAt(entry.getSentAt())
                        .build())
                .toList();
    }
}
