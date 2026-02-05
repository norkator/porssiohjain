package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ControlService {

    private final ControlRepository controlRepository;
    private final ControlDeviceRepository controlDeviceRepository;
    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final ControlTableRepository controlTableRepository;
    private final PowerLimitDeviceRepository powerLimitDeviceRepository;
    private final ElectricityContractRepository electricityContractRepository;
    private final PowerLimitService powerLimitService;
    private final ProductionSourceDeviceRepository productionSourceDeviceRepository;
    private final SiteRepository siteRepository;

    public ControlEntity createControl(
            Long accountId, String name, String timezone,
            BigDecimal maxPriceSnt, BigDecimal minPriceSnt, Integer dailyOnMinutes,
            BigDecimal taxPercent, ControlMode mode, Boolean manualOn,
            Boolean alwaysOnBelowMinPrice
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        if (account.getId().equals(accountId)) {
            ControlEntity control = ControlEntity.builder()
                    .account(account)
                    .name(name)
                    .timezone(timezone)
                    .maxPriceSnt(maxPriceSnt)
                    .minPriceSnt(minPriceSnt)
                    .dailyOnMinutes(dailyOnMinutes)
                    .taxPercent(taxPercent)
                    .mode(mode != null ? mode : ControlMode.BELOW_MAX_PRICE)
                    .manualOn(manualOn != null ? manualOn : false)
                    .alwaysOnBelowMinPrice(alwaysOnBelowMinPrice != null ? alwaysOnBelowMinPrice : false)
                    .build();

            return controlRepository.save(control);
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public ControlEntity updateControl(
            Long accountId,
            Long controlId, String name, BigDecimal maxPriceSnt, BigDecimal minPriceSnt,
            Integer dailyOnMinutes, BigDecimal taxPercent, ControlMode mode, Boolean manualOn,
            Boolean alwaysOnBelowMinPrice, Long energyContractId, Long transferContractId,
            Long siteId
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));

        ElectricityContractEntity e = energyContractId == null
                ? null
                : electricityContractRepository.findByIdAndAccountId(energyContractId, accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Energy contract not found or does not belong to account"));

        ElectricityContractEntity t = transferContractId == null
                ? null
                : electricityContractRepository.findByIdAndAccountId(transferContractId, accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Transfer contract not found or does not belong to account"));

        if (control.getAccount().getId().equals(accountId)) {
            control.setName(name);
            control.setMaxPriceSnt(maxPriceSnt);
            control.setMinPriceSnt(minPriceSnt);
            control.setDailyOnMinutes(dailyOnMinutes);
            control.setTaxPercent(taxPercent);
            control.setMode(mode);
            control.setManualOn(manualOn);
            control.setAlwaysOnBelowMinPrice(alwaysOnBelowMinPrice);
            control.setEnergyContract(e);
            control.setTransferContract(t);
            control.setSite(siteId != null ? siteRepository.getReferenceById(siteId) : null);
            return controlRepository.save(control);
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public void deleteControl(
            Long accountId, Long controlId
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));
        if (control.getAccount().getId().equals(accountId)) {
            controlRepository.deleteById(controlId);
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public List<ControlResponse> getAllControls(
            Long accountId
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        List<ControlEntity> controlEntities = controlRepository.findAllByAccountOrderByIdAsc(account);
        return controlEntities.stream()
                .map(entity -> ControlResponse.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .timezone(entity.getTimezone())
                        .maxPriceSnt(entity.getMaxPriceSnt())
                        .minPriceSnt(entity.getMinPriceSnt())
                        .dailyOnMinutes(entity.getDailyOnMinutes())
                        .taxPercent(entity.getTaxPercent())
                        .mode(entity.getMode())
                        .manualOn(entity.isManualOn())
                        .alwaysOnBelowMinPrice(entity.isAlwaysOnBelowMinPrice())
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build())
                .toList();
    }

    public ControlResponse getControl(
            Long accountId, Long controlId
    ) {
        return controlRepository.findByIdAndAccountId(controlId, accountId)
                .map(entity -> ControlResponse.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .timezone(entity.getTimezone())
                        .maxPriceSnt(entity.getMaxPriceSnt())
                        .minPriceSnt(entity.getMinPriceSnt())
                        .dailyOnMinutes(entity.getDailyOnMinutes())
                        .taxPercent(entity.getTaxPercent())
                        .mode(entity.getMode())
                        .manualOn(entity.isManualOn())
                        .alwaysOnBelowMinPrice(entity.isAlwaysOnBelowMinPrice())
                        .energyContractId(entity.getEnergyContract() != null ? entity.getEnergyContract().getId() : null)
                        .energyContractName(entity.getEnergyContract() != null ? entity.getEnergyContract().getName() : null)
                        .transferContractId(entity.getTransferContract() != null ? entity.getTransferContract().getId() : null)
                        .transferContractName(entity.getTransferContract() != null ? entity.getTransferContract().getName() : null)
                        .siteId(entity.getSite() != null ? entity.getSite().getId() : null)
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build())
                .orElseThrow(() -> new IllegalArgumentException("Control not found for your account with ID: " + controlId));
    }

    public ControlDeviceResponse addDeviceToControl(
            Long accountId, Long controlId, Long deviceId, Integer deviceChannel
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));
        if (control.getAccount().getId().equals(accountId)) {
            DeviceEntity device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new EntityNotFoundException("Device not found with id: " + deviceId));

            if (controlDeviceRepository.existsByControlIdAndDeviceIdAndDeviceChannel(controlId, deviceId, deviceChannel)) {
                throw new DuplicateEntityException(
                        String.format("Device %d with channel %d is already linked to control %d",
                                deviceId, deviceChannel, controlId)
                );
            }

            ControlDeviceEntity controlDevice = ControlDeviceEntity.builder()
                    .control(control)
                    .device(device)
                    .deviceChannel(deviceChannel)
                    .build();

            ControlDeviceEntity saved = controlDeviceRepository.save(controlDevice);

            return ControlDeviceResponse.builder()
                    .id(saved.getId())
                    .controlId(saved.getControl().getId())
                    .deviceId(saved.getDevice().getId())
                    .deviceChannel(saved.getDeviceChannel())
                    .build();
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public ControlDeviceResponse updateControlDevice(
            Long accountId, Long controlDeviceId, Long deviceId, Integer deviceChannel
    ) {
        ControlDeviceEntity controlDevice = controlDeviceRepository.findById(controlDeviceId)
                .orElseThrow(() -> new EntityNotFoundException("ControlDevice not found with id: " + controlDeviceId));

        if (controlDevice.getControl().getAccount().getId().equals(accountId)) {
            if (deviceId != null) {
                DeviceEntity device = deviceRepository.findById(deviceId)
                        .orElseThrow(() -> new EntityNotFoundException("Device not found with id: " + deviceId));
                controlDevice.setDevice(device);
            }

            if (deviceChannel != null) {
                controlDevice.setDeviceChannel(deviceChannel);
            }

            ControlDeviceEntity updated = controlDeviceRepository.save(controlDevice);

            return ControlDeviceResponse.builder()
                    .id(updated.getId())
                    .controlId(updated.getControl().getId())
                    .deviceId(updated.getDevice().getId())
                    .deviceChannel(updated.getDeviceChannel())
                    .build();
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public void deleteControlDevice(
            Long accountId, Long controlDeviceId
    ) {
        ControlDeviceEntity controlDevice = controlDeviceRepository.findById(controlDeviceId)
                .orElseThrow(() -> new EntityNotFoundException("ControlDevice not found with id: " + controlDeviceId));
        if (controlDevice.getControl().getAccount().getId().equals(accountId)) {
            controlDeviceRepository.deleteById(controlDeviceId);
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public List<ControlDeviceEntity> getDevicesByControl(
            Long accountId, Long controlId
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));
        if (control.getAccount().getId().equals(accountId)) {
            return control.getControlDevices().stream().toList();
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public List<ControlDeviceResponse> getControlDevices(
            Long controlId
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));
        Set<ControlDeviceEntity> controlDeviceEntities = control.getControlDevices();
        return controlDeviceEntities.stream()
                .map(entity -> ControlDeviceResponse.builder()
                        .id(entity.getId())
                        .controlId(control.getId())
                        .deviceId(entity.getDevice().getId())
                        .deviceChannel(entity.getDeviceChannel())
                        .device(
                                DeviceResponse.builder()
                                        .id(entity.getDevice().getId())
                                        .uuid(entity.getDevice().getUuid())
                                        .deviceName(entity.getDevice().getDeviceName())
                                        .lastCommunication(entity.getDevice().getLastCommunication())
                                        .createdAt(entity.getDevice().getCreatedAt())
                                        .updatedAt(entity.getDevice().getUpdatedAt())
                                        .build()
                        )
                        .build())
                .sorted(Comparator.comparing(ControlDeviceResponse::getDeviceChannel))
                .toList();
    }

    /**
     * Returns control channels and control status for each defined device channel
     *
     * @param deviceUuid of device
     * @return channel status map of {"1":0,"2":0,...}
     */
    public Map<Integer, Integer> getControlsForDevice(
            String deviceUuid
    ) {
        DeviceEntity device = deviceRepository.findByUuid(UUID.fromString(deviceUuid))
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + deviceUuid));

        Instant nowUtc = Instant.now(); // current UTC time
        device.setLastCommunication(nowUtc);

        deviceRepository.save(device);

        List<ControlDeviceEntity> controlDevices = controlDeviceRepository.findByDevice(device);
        Map<Integer, Integer> channelMap = new HashMap<>();

        for (ControlDeviceEntity cd : controlDevices) {
            // check for power limit first and skip later logic if power limit active
            int channel = cd.getDeviceChannel();

            if (isPowerLimitActiveForDeviceChannel(device, channel)) {
                channelMap.put(channel, 0);
                continue;
            }

            Integer productionOverride = getProductionOverride(device, channel);
            if (productionOverride != null) {
                channelMap.put(channel, productionOverride);
                continue;
            }

            ControlEntity control = cd.getControl();
            ZoneId controlZone = ZoneId.of(control.getTimezone());
            ControlMode mode = control.getMode();

            if (mode.equals(ControlMode.MANUAL)) {
                channelMap.put(cd.getDeviceChannel(), control.isManualOn() ? 1 : 0);
            } else if (mode.equals(ControlMode.CHEAPEST_HOURS) || mode.equals(ControlMode.BELOW_MAX_PRICE)) {
                ZonedDateTime nowInControlZone = nowUtc.atZone(controlZone);

                boolean active = controlTableRepository.findByControlIdAndStartTimeAfterOrderByStartTimeAsc(
                                control.getId(), nowUtc.minusSeconds(30 * 60)) // last 30 minutes
                        .stream()
                        .anyMatch(ct -> {
                            ZonedDateTime start = ct.getStartTime().atZone(controlZone);
                            ZonedDateTime end = ct.getEndTime().atZone(controlZone);
                            return !nowInControlZone.isBefore(start) && !nowInControlZone.isAfter(end);
                        });

                channelMap.put(cd.getDeviceChannel(), active ? 1 : 0);
            } else {
                // default off
                channelMap.put(cd.getDeviceChannel(), 0);
            }

        }

        return channelMap;
    }


    private boolean isPowerLimitActiveForDeviceChannel(
            DeviceEntity device, int deviceChannel
    ) {
        List<PowerLimitDeviceEntity> mappings = powerLimitDeviceRepository
                .findByDeviceAndDeviceChannel(device, deviceChannel);
        if (mappings.isEmpty()) {
            return false;
        }
        for (PowerLimitDeviceEntity m : mappings) {
            PowerLimitEntity limit = m.getPowerLimit();
            if (!limit.isEnabled()) {
                continue;
            }
            Optional<BigDecimal> intervalSum =
                    powerLimitService.getCurrentIntervalSum(
                            limit.getAccount().getId(),
                            limit.getId()
                    );
            if (intervalSum.isPresent() && intervalSum.get().compareTo(limit.getLimitKw()) > 0) {
                return true;
            }
        }
        return false;
    }


    private Integer getProductionOverride(
            DeviceEntity device, int channel
    ) {
        List<ProductionSourceDeviceEntity> rules = productionSourceDeviceRepository.findAllByDevice(device);
        for (ProductionSourceDeviceEntity r : rules) {
            if (!r.getDeviceChannel().equals(channel)) continue;
            BigDecimal currentKw = r.getProductionSource().getCurrentKw();
            BigDecimal trigger = r.getTriggerKw();
            int cmp = currentKw.compareTo(trigger);
            boolean match = (r.getComparisonType() == ComparisonType.GREATER_THAN && cmp > 0) ||
                    (r.getComparisonType() == ComparisonType.LESS_THAN && cmp < 0);
            if (match) {
                return r.getAction() == ControlAction.TURN_ON ? 1 : 0;
            }
        }
        return null;
    }


    public TimeTableListResponse getTimetableForDevice(
            String deviceUuid
    ) {
        DeviceEntity device = deviceRepository.findByUuid(UUID.fromString(deviceUuid))
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + deviceUuid));

        Instant nowUtc = Instant.now();
        device.setLastCommunication(nowUtc);
        deviceRepository.save(device);

        List<ControlDeviceEntity> controlDevices = controlDeviceRepository.findByDevice(device);
        List<TimeTableResponse> schedule = new ArrayList<>();

        for (ControlDeviceEntity cd : controlDevices) {
            ControlEntity control = cd.getControl();
            ZoneId controlZone = ZoneId.of(control.getTimezone());
            ZonedDateTime nowInZone = nowUtc.atZone(controlZone);

            Instant fromUtc = nowUtc.minusSeconds(30 * 60);
            ZonedDateTime endOfNextDay = nowInZone.plusDays(1)
                    .withHour(23).withMinute(59).withSecond(59).withNano(0);
            Instant toUtc = endOfNextDay.toInstant();

            List<ControlTableEntity> controlTables =
                    controlTableRepository.findByControlIdAndStartTimeBetweenOrderByStartTimeAsc(
                            control.getId(), fromUtc, toUtc);

            for (ControlTableEntity ct : controlTables) {
                ZonedDateTime start = ct.getStartTime().atZone(controlZone);
                ZonedDateTime end = ct.getEndTime().atZone(controlZone);

                schedule.add(TimeTableResponse.builder()
                        .time(start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .action(1)
                        .build());

                schedule.add(TimeTableResponse.builder()
                        .time(end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .action(0)
                        .build());
            }
        }

        schedule.sort(Comparator.comparing(TimeTableResponse::getTime));

        List<TimeTableResponse> merged = new ArrayList<>();
        String lastTime = null;
        Integer lastAction = null;

        for (TimeTableResponse entry : schedule) {
            if (entry.getTime().equals(lastTime)) {
                if (entry.getAction() == 1 || lastAction == null || lastAction == 0) {
                    merged.set(merged.size() - 1, entry);
                    lastAction = entry.getAction();
                }
            } else {
                merged.add(entry);
                lastTime = entry.getTime();
                lastAction = entry.getAction();
            }
        }

        return TimeTableListResponse.builder()
                .timezone(device.getTimezone())
                .schedule(merged)
                .build();
    }

}
