package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.ControlDeviceResponse;
import com.nitramite.porssiohjain.services.models.ControlResponse;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    public ControlEntity createControl(
            Long accountId, String name, String timezone,
            BigDecimal maxPriceSnt, Integer dailyOnMinutes,
            BigDecimal taxPercent, ControlMode mode, Boolean manualOn
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        ControlEntity control = ControlEntity.builder()
                .account(account)
                .name(name)
                .timezone(timezone)
                .maxPriceSnt(maxPriceSnt)
                .dailyOnMinutes(dailyOnMinutes)
                .taxPercent(taxPercent)
                .mode(mode != null ? mode : ControlMode.BELOW_MAX_PRICE)
                .manualOn(manualOn != null ? manualOn : false)
                .build();

        return controlRepository.save(control);
    }

    public ControlEntity updateControl(
            Long controlId, String name, BigDecimal maxPriceSnt, Integer dailyOnMinutes,
            BigDecimal taxPercent, ControlMode mode, Boolean manualOn
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));

        control.setName(name);
        control.setMaxPriceSnt(maxPriceSnt);
        control.setDailyOnMinutes(dailyOnMinutes);
        control.setTaxPercent(taxPercent);
        control.setMode(mode);
        control.setManualOn(manualOn);
        return controlRepository.save(control);
    }

    public void deleteControl(
            Long controlId
    ) {
        if (!controlRepository.existsById(controlId)) {
            throw new EntityNotFoundException("Control not found with id: " + controlId);
        }
        controlRepository.deleteById(controlId);
    }

    public List<ControlResponse> getAllControls() {
        List<ControlEntity> controlEntities = controlRepository.findAll();

        return controlEntities.stream()
                .map(entity -> ControlResponse.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .timezone(entity.getTimezone())
                        .maxPriceSnt(entity.getMaxPriceSnt())
                        .dailyOnMinutes(entity.getDailyOnMinutes())
                        .taxPercent(entity.getTaxPercent())
                        .mode(entity.getMode())
                        .manualOn(entity.isManualOn())
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build())
                .toList();
    }

    public ControlResponse getControl(Long controlId) {
        return controlRepository.findById(controlId)
                .map(entity -> ControlResponse.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .maxPriceSnt(entity.getMaxPriceSnt())
                        .dailyOnMinutes(entity.getDailyOnMinutes())
                        .taxPercent(entity.getTaxPercent())
                        .mode(entity.getMode())
                        .manualOn(entity.isManualOn())
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build())
                .orElseThrow(() -> new IllegalArgumentException("Control not found with ID: " + controlId));
    }

    public ControlDeviceResponse addDeviceToControl(
            Long controlId, Long deviceId, Integer deviceChannel
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));

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
    }

    public ControlDeviceResponse updateControlDevice(
            Long controlDeviceId, Long deviceId, Integer deviceChannel
    ) {
        ControlDeviceEntity controlDevice = controlDeviceRepository.findById(controlDeviceId)
                .orElseThrow(() -> new EntityNotFoundException("ControlDevice not found with id: " + controlDeviceId));

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
    }

    public void deleteControlDevice(
            Long controlDeviceId
    ) {
        if (!controlDeviceRepository.existsById(controlDeviceId)) {
            throw new EntityNotFoundException("ControlDevice not found with id: " + controlDeviceId);
        }
        controlDeviceRepository.deleteById(controlDeviceId);
    }

    public List<ControlDeviceEntity> getDevicesByControl(
            Long controlId
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));

        return control.getControlDevices().stream().toList();
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
            ControlEntity control = cd.getControl();
            ZoneId controlZone = ZoneId.of(control.getTimezone());
            ControlMode mode = control.getMode();

            if (mode.equals(ControlMode.MANUAL)) {
                channelMap.put(cd.getDeviceChannel(), control.isManualOn() ? 1 : 0);
            } else if (mode.equals(ControlMode.BELOW_MAX_PRICE)) {
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

}
