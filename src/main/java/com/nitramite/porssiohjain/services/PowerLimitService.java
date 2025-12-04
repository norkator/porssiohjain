package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.PowerLimitDeviceEntity;
import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitDeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PowerLimitService {

    private final PowerLimitRepository powerLimitRepository;
    private final PowerLimitDeviceRepository powerLimitDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final AccountRepository accountRepository;

    public PowerLimitService(
            PowerLimitRepository powerLimitRepository,
            PowerLimitDeviceRepository powerLimitDeviceRepository,
            DeviceRepository deviceRepository,
            AccountRepository accountRepository
    ) {
        this.powerLimitRepository = powerLimitRepository;
        this.powerLimitDeviceRepository = powerLimitDeviceRepository;
        this.deviceRepository = deviceRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public PowerLimitResponse createLimit(Long accountId, String name, Double limitKw, boolean enabled) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        PowerLimitEntity entity = PowerLimitEntity.builder()
                .account(account)
                .name(name)
                .limitKw(BigDecimal.valueOf(limitKw))
                .currentKw(BigDecimal.ZERO)
                .enabled(enabled)
                .timezone("UTC")
                .build();

        powerLimitRepository.save(entity);
        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public PowerLimitResponse getPowerLimit(Long accountId, Long powerLimitId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        return powerLimitRepository.findByAccountIdAndId(account.getId(), powerLimitId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Power limit not found for account " + accountId + " and id " + powerLimitId
                ));
    }

    @Transactional
    public void updatePowerLimit(
            Long accountId, Long powerLimitId, String name,
            BigDecimal limitKw, boolean enabled, String timezone
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        PowerLimitEntity entity = powerLimitRepository
                .findByAccountIdAndId(account.getId(), powerLimitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Power limit not found for account " + accountId + " and id " + powerLimitId
                ));
        entity.setName(name);
        entity.setLimitKw(limitKw);
        entity.setEnabled(enabled);
        entity.setTimezone(timezone);
        powerLimitRepository.save(entity);
        mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<PowerLimitResponse> getAllLimits(Long accountId) {
        return powerLimitRepository.findByAccountId(accountId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PowerLimitDeviceResponse> getPowerLimitDevices(Long powerLimitId) {
        return powerLimitDeviceRepository.findByPowerLimitId(powerLimitId).stream()
                .map(this::mapDeviceToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevicesForPowerLimitId(Long powerLimitId) {
        PowerLimitEntity limit = powerLimitRepository.findById(powerLimitId)
                .orElseThrow(() -> new IllegalArgumentException("Power limit not found: " + powerLimitId));

        Long accountId = limit.getAccount().getId();

        List<Long> linkedIds = powerLimitDeviceRepository.findDeviceIdsByPowerLimitId(powerLimitId);

        return deviceRepository.findByAccountId(accountId).stream()
                .filter(d -> !linkedIds.contains(d.getId()))
                .map(this::mapDeviceToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addDeviceToPowerLimit(Long accountId, Long powerLimitId, Long deviceId, int channel) {
        PowerLimitEntity limit = powerLimitRepository.findById(powerLimitId)
                .orElseThrow(() -> new IllegalArgumentException("Power limit not found: " + powerLimitId));

        if (!limit.getAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException("Access denied");
        }

        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        PowerLimitDeviceEntity entity = PowerLimitDeviceEntity.builder()
                .powerLimit(limit)
                .device(device)
                .deviceChannel(channel)
                .build();

        powerLimitDeviceRepository.save(entity);
    }

    @Transactional
    public void deletePowerLimitDevice(Long accountId, Long powerLimitDeviceId) {
        PowerLimitDeviceEntity entity = powerLimitDeviceRepository.findById(powerLimitDeviceId)
                .orElseThrow(() -> new IllegalArgumentException("Power limit device not found: " + powerLimitDeviceId));

        if (!entity.getPowerLimit().getAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException("Access denied");
        }

        powerLimitDeviceRepository.delete(entity);
    }

    private PowerLimitResponse mapToResponse(PowerLimitEntity entity) {
        return PowerLimitResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .name(entity.getName())
                .limitKw(entity.getLimitKw())
                .currentKw(entity.getCurrentKw())
                .enabled(entity.isEnabled())
                .timezone(entity.getTimezone())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PowerLimitDeviceResponse mapDeviceToResponse(PowerLimitDeviceEntity entity) {
        return PowerLimitDeviceResponse.builder()
                .id(entity.getId())
                .deviceId(entity.getDevice().getId())
                .deviceChannel(entity.getDeviceChannel())
                .device(mapDeviceToResponse(entity.getDevice()))
                .powerLimitId(entity.getPowerLimit().getId())
                .build();
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

    @Transactional
    public void updateCurrentKw(String uuid, Double currentKw) {
        PowerLimitEntity entity = powerLimitRepository.findByUuid(UUID.fromString(uuid))
                .orElseThrow(() -> new IllegalArgumentException("Power limit not found for uuid: " + uuid));
        entity.setCurrentKw(BigDecimal.valueOf(currentKw));
        powerLimitRepository.save(entity);
    }

}
