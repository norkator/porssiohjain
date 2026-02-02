package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PowerLimitService {

    private final PowerLimitRepository powerLimitRepository;
    private final PowerLimitDeviceRepository powerLimitDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final AccountRepository accountRepository;
    private final PowerLimitHistoryRepository powerLimitHistoryRepository;
    private final EmailService emailService;
    private final Map<Long, Instant> lastNotificationSent = new ConcurrentHashMap<>();

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
            BigDecimal limitKw, Integer limitIntervalMinutes, boolean enabled, boolean notifyEnabled, String timezone
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
        entity.setLimitIntervalMinutes(limitIntervalMinutes);
        entity.setEnabled(enabled);
        entity.setNotifyEnabled(notifyEnabled);
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
                .limitIntervalMinutes(entity.getLimitIntervalMinutes())
                .peakKw(entity.getPeakKw())
                .enabled(entity.isEnabled())
                .notifyEnabled(entity.isNotifyEnabled())
                .timezone(entity.getTimezone())
                .createdAt(entity.getCreatedAt())
                .lastTotalKwh(entity.getLastTotalKwh())
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
    public void updateCurrentKw(String uuid, CurrentKwRequest request) {
        PowerLimitEntity entity = powerLimitRepository.findByUuid(UUID.fromString(uuid))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Power limit not found for uuid: " + uuid
                ));
        BigDecimal kw = BigDecimal.valueOf(request.getCurrentKw());
        BigDecimal totalKwh = BigDecimal.valueOf(request.getTotalKwh());
        Instant measuredAt = Instant.ofEpochMilli(request.getMeasuredAt());
        entity.setCurrentKw(kw);
        if (entity.getPeakKw() == null || kw.compareTo(entity.getPeakKw()) > 0) {
            entity.setPeakKw(kw);
        }
        BigDecimal deltaKwh = BigDecimal.ZERO;
        if (entity.getLastTotalKwh() != null) {
            if (totalKwh.compareTo(entity.getLastTotalKwh()) >= 0) {
                deltaKwh = totalKwh.subtract(entity.getLastTotalKwh());
            } else {
                entity.setLastTotalKwh(totalKwh);
                entity.setLastMeasuredAt(measuredAt);
                return;
            }
        }
        entity.setLastTotalKwh(totalKwh);
        entity.setLastMeasuredAt(measuredAt);
        Instant minuteStart = measuredAt.truncatedTo(ChronoUnit.MINUTES);
        Instant minuteEnd = minuteStart.plus(1, ChronoUnit.MINUTES);
        BigDecimal finalDeltaKwh = deltaKwh;
        PowerLimitHistoryEntity history = powerLimitHistoryRepository
                .findForMinute(entity, minuteStart, minuteEnd)
                .orElseGet(() -> {
                    PowerLimitHistoryEntity h = PowerLimitHistoryEntity.builder()
                            .account(entity.getAccount())
                            .powerLimit(entity)
                            .kilowatts(finalDeltaKwh)
                            .createdAt(minuteStart)
                            .build();
                    entity.getHistory().add(h);
                    return h;
                });
        history.setKilowatts(finalDeltaKwh);
        history.setCreatedAt(minuteStart);
        if (entity.isNotifyEnabled()) {
            checkAndSendNotification(entity);
        }
    }

    private void checkAndSendNotification(PowerLimitEntity entity) {
        ZoneId zone = ZoneId.of(entity.getTimezone());
        Instant now = Instant.now();
        Instant quarterStart = Utils.toQuarterHour(now, zone);
        Instant quarterEnd = quarterStart.plus(15, ChronoUnit.MINUTES);
        List<PowerLimitHistoryEntity> values = powerLimitHistoryRepository
                .findByPowerLimitAndCreatedAtBetween(
                        entity.getAccount().getId(),
                        entity.getId(),
                        quarterStart,
                        quarterEnd
                );
        if (values.isEmpty()) return;
        BigDecimal sum = values.stream()
                .map(PowerLimitHistoryEntity::getKilowatts)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
        boolean currentlyOver = avg.compareTo(entity.getLimitKw()) > 0;
        Instant lastSent = lastNotificationSent.get(entity.getId());
        boolean canSend = lastSent == null || Duration.between(lastSent, now).toHours() >= 24;
        if (currentlyOver && canSend) {
            emailService.sendPowerLimitExceededEmail(
                    entity.getAccount().getEmail(),
                    entity.getName(),
                    entity.getLimitKw(),
                    avg,
                    Locale.of(entity.getAccount().getLocale())
            );
            lastNotificationSent.put(entity.getId(), now);
        }
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
