package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitDeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitHistoryResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitResponse;
import com.nitramite.porssiohjain.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PowerLimitService {

    private final PowerLimitRepository powerLimitRepository;
    private final PowerLimitDeviceRepository powerLimitDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final AccountRepository accountRepository;
    private final PowerLimitHistoryRepository powerLimitHistoryRepository;

    public PowerLimitService(
            PowerLimitRepository powerLimitRepository,
            PowerLimitDeviceRepository powerLimitDeviceRepository,
            DeviceRepository deviceRepository,
            AccountRepository accountRepository,
            PowerLimitHistoryRepository powerLimitHistoryRepository
    ) {
        this.powerLimitRepository = powerLimitRepository;
        this.powerLimitDeviceRepository = powerLimitDeviceRepository;
        this.deviceRepository = deviceRepository;
        this.accountRepository = accountRepository;
        this.powerLimitHistoryRepository = powerLimitHistoryRepository;
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
                .orElseThrow(() -> new IllegalArgumentException(
                        "Power limit not found for uuid: " + uuid
                ));
        BigDecimal kw = BigDecimal.valueOf(currentKw);
        entity.setCurrentKw(kw);
        Instant now = Instant.now();
        Instant minuteStart = now.truncatedTo(ChronoUnit.MINUTES);
        Instant minuteEnd = minuteStart.plus(1, ChronoUnit.MINUTES);
        PowerLimitHistoryEntity history = powerLimitHistoryRepository
                .findForMinute(entity, minuteStart, minuteEnd)
                .orElseGet(() -> {
                    PowerLimitHistoryEntity h = PowerLimitHistoryEntity.builder()
                            .account(entity.getAccount())
                            .powerLimit(entity)
                            .kilowatts(kw)
                            .build();
                    entity.getHistory().add(h);
                    return h;
                });
        history.setKilowatts(kw);
    }

    @Transactional
    public void deleteOldPowerLimitHistory() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int deleted = powerLimitHistoryRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} power limit history rows older than {}", deleted, cutoff);
        }
    }

    @Transactional(readOnly = true)
    public List<PowerLimitHistoryResponse> getPowerLimitHistory(
            Long accountId, Long powerLimitId, int hours
    ) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return powerLimitHistoryRepository.findAllByPowerLimitAndAccount(accountId, powerLimitId)
                .stream()
                .filter(h -> h.getCreatedAt().isAfter(since))
                .map(h -> PowerLimitHistoryResponse.builder()
                        .accountId(h.getPowerLimit().getAccount().getId())
                        .kilowatts(h.getKilowatts())
                        .createdAt(h.getCreatedAt())
                        .build()
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PowerLimitHistoryResponse> getQuarterlyPowerLimitHistory(
            Long accountId, Long powerLimitId, int hours
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        PowerLimitEntity powerLimitEntity = powerLimitRepository
                .findByAccountIdAndId(account.getId(), powerLimitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Power limit not found for account " + accountId + " and id " + powerLimitId
                ));
        ZoneId zone = ZoneId.of(powerLimitEntity.getTimezone());
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        Map<Instant, List<PowerLimitHistoryEntity>> grouped =
                powerLimitHistoryRepository.findAllByPowerLimitAndAccount(accountId, powerLimitId)
                        .stream()
                        .filter(h -> h.getCreatedAt().isAfter(since))
                        .collect(Collectors.groupingBy(h -> Utils.toQuarterHour(h.getCreatedAt(), zone)));
        return grouped.entrySet().stream()
                .map(entry -> {
                    Instant bucketStart = entry.getKey();
                    List<PowerLimitHistoryEntity> values = entry.getValue();

                    BigDecimal avg = values.stream()
                            .map(PowerLimitHistoryEntity::getKilowatts)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(
                                    BigDecimal.valueOf(values.size()),
                                    2,
                                    RoundingMode.HALF_UP
                            );

                    return PowerLimitHistoryResponse.builder()
                            .accountId(accountId)
                            .kilowatts(avg)
                            .createdAt(bucketStart)
                            .build();
                })
                .sorted(Comparator.comparing(PowerLimitHistoryResponse::getCreatedAt))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getCurrentQuarterHourAverage(
            Long accountId, Long powerLimitId
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        PowerLimitEntity powerLimitEntity = powerLimitRepository
                .findByAccountIdAndId(account.getId(), powerLimitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Power limit not found for account " + accountId + " and id " + powerLimitId
                ));
        ZoneId zone = ZoneId.of(powerLimitEntity.getTimezone());
        Instant now = Instant.now();
        Instant intervalStart = Utils.toQuarterHour(now, zone);
        Instant intervalEnd = intervalStart.plus(15, ChronoUnit.MINUTES);
        List<PowerLimitHistoryEntity> values =
                powerLimitHistoryRepository.findByPowerLimitAndCreatedAtBetween(
                        accountId, powerLimitId, intervalStart, intervalEnd
                );
        if (values.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = values.stream()
                .map(PowerLimitHistoryEntity::getKilowatts)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(
                sum.divide(
                        BigDecimal.valueOf(values.size()),
                        2,
                        RoundingMode.HALF_UP
                )
        );
    }

}
