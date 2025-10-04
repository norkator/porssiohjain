package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public DeviceEntity createDevice(
            Long accountId, String deviceName
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        DeviceEntity device = DeviceEntity.builder()
                .deviceName(deviceName)
                .lastCommunication(null)
                .account(account)
                .build();

        return deviceRepository.save(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceEntity> listDevices(Long accountId) {
        return deviceRepository.findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public DeviceEntity getDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
    }
}