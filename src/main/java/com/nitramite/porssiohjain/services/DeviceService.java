package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import jakarta.persistence.EntityNotFoundException;
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
    private final ControlRepository controlRepository;

    @Transactional
    public DeviceEntity createDevice(
            Long accountId, String deviceName, String timezone
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        DeviceEntity device = DeviceEntity.builder()
                .deviceName(deviceName)
                .timezone(timezone)
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

        List<DeviceEntity> deviceEntities = deviceRepository.findByAccountIdOrderByIdAsc(account.getId());

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

    @Transactional
    public void updateDevice(
            Long deviceId, String newName, String newTimezone
    ) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        device.setDeviceName(newName);
        device.setTimezone(newTimezone);
        deviceRepository.save(device);
    }

}