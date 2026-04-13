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

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.mqtt.MqttService;
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

    private static final long CONTROL_LOOKBACK_SECONDS = 30L * 60L;

    private final ControlRepository controlRepository;
    private final ControlDeviceRepository controlDeviceRepository;
    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final ControlTableRepository controlTableRepository;
    private final PowerLimitDeviceRepository powerLimitDeviceRepository;
    private final ElectricityContractRepository electricityContractRepository;
    private final PowerLimitService powerLimitService;
    private final ProductionSourceDeviceRepository productionSourceDeviceRepository;
    private final WeatherControlDeviceRepository weatherControlDeviceRepository;
    private final LoadSheddingNodeRepository loadSheddingNodeRepository;
    private final LoadSheddingLinkRepository loadSheddingLinkRepository;
    private final SiteWeatherRepository siteWeatherRepository;
    private final SiteRepository siteRepository;
    private final ResourceSharingRepository resourceSharingRepository;
    private final ControlHeatPumpRepository controlHeatPumpRepository;
    private final MqttService mqttService;
    private final AccountLimitService accountLimitService;

    public ControlEntity createControl(
            Long accountId, String name, String timezone,
            BigDecimal maxPriceSnt, BigDecimal minPriceSnt, Integer dailyOnMinutes,
            BigDecimal taxPercent, ControlMode mode, Boolean manualOn,
            Boolean alwaysOnBelowMinPrice
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        if (account.getId().equals(accountId)) {
            accountLimitService.assertCanCreateControl(accountId);
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
            SiteEntity site = siteId == null
                    ? null
                    : siteRepository.findByIdAndAccountId(siteId, accountId)
                    .orElseThrow(() -> new EntityNotFoundException("Site not found or does not belong to account"));
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
            control.setSite(site);
            return controlRepository.save(control);
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public void deleteControl(
            Long accountId, Long controlId
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        ControlEntity entity = controlRepository
                .findByIdAndAccountId(controlId, account.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Control not found for account " + accountId + " and id " + controlId
                ));
        controlRepository.delete(entity);
    }

    public List<ControlResponse> getAllControls(
            Long accountId
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        List<ControlEntity> ownControls = controlRepository.findAllByAccountOrderByIdAsc(account);

        List<ResourceSharingEntity> sharedResources =
                resourceSharingRepository.findByReceiverAccountIdAndResourceTypeAndEnabledTrue(
                        accountId,
                        ResourceType.CONTROL
                );
        List<Long> sharedControlIds = sharedResources.stream()
                .map(ResourceSharingEntity::getControlId)
                .filter(Objects::nonNull)
                .toList();
        List<ControlEntity> sharedControls = sharedControlIds.isEmpty()
                ? List.of()
                : controlRepository.findAllById(sharedControlIds);
        List<ControlResponse> responses = new ArrayList<>();

        ownControls.forEach(entity -> responses.add(ControlResponse.builder()
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
                .shared(false)
                .build()));

        sharedControls.forEach(entity -> responses.add(ControlResponse.builder()
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
                .shared(true)
                .build()));

        return responses;
    }

    public ControlResponse getControl(
            Long accountId, Long controlId
    ) {
        Optional<ControlEntity> ownControl = controlRepository.findByIdAndAccountId(controlId, accountId);
        if (ownControl.isPresent()) {
            ControlEntity entity = ownControl.get();
            return ControlResponse.builder()
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
                    .shared(false)
                    .build();
        }
        boolean hasSharedAccess =
                resourceSharingRepository.existsByReceiverAccountIdAndResourceTypeAndControlIdAndEnabledTrue(
                        accountId,
                        ResourceType.CONTROL,
                        controlId
                );
        if (hasSharedAccess) {
            ControlEntity entity = controlRepository.findById(controlId)
                    .orElseThrow(() -> new IllegalArgumentException("Shared control not found with ID: " + controlId));
            return ControlResponse.builder()
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
                    .shared(true)
                    .build();
        }
        throw new IllegalArgumentException("Control not found for your account with ID: " + controlId);
    }

    public ControlDeviceResponse addDeviceToControl(
            Long accountId, Long controlId, Long deviceId, Integer deviceChannel, BigDecimal estimatedPowerKw
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));
        if (control.getAccount().getId().equals(accountId)) {
            DeviceEntity device = getOwnedDevice(accountId, deviceId);

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
                    .estimatedPowerKw(estimatedPowerKw)
                    .build();

            ControlDeviceEntity saved = controlDeviceRepository.save(controlDevice);

            return ControlDeviceResponse.builder()
                    .id(saved.getId())
                    .controlId(saved.getControl().getId())
                    .deviceId(saved.getDevice().getId())
                    .deviceChannel(saved.getDeviceChannel())
                    .estimatedPowerKw(saved.getEstimatedPowerKw())
                    .build();
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public ControlDeviceResponse updateControlDevice(
            Long accountId, Long controlDeviceId, Long deviceId, Integer deviceChannel, BigDecimal estimatedPowerKw
    ) {
        ControlDeviceEntity controlDevice = controlDeviceRepository.findById(controlDeviceId)
                .orElseThrow(() -> new EntityNotFoundException("ControlDevice not found with id: " + controlDeviceId));

        if (controlDevice.getControl().getAccount().getId().equals(accountId)) {
            if (deviceId != null) {
                DeviceEntity device = getOwnedDevice(accountId, deviceId);
                controlDevice.setDevice(device);
            }

            if (deviceChannel != null) {
                controlDevice.setDeviceChannel(deviceChannel);
            }
            if (estimatedPowerKw != null) {
                controlDevice.setEstimatedPowerKw(estimatedPowerKw);
            }

            ControlDeviceEntity updated = controlDeviceRepository.save(controlDevice);

            return ControlDeviceResponse.builder()
                    .id(updated.getId())
                    .controlId(updated.getControl().getId())
                    .deviceId(updated.getDevice().getId())
                    .deviceChannel(updated.getDeviceChannel())
                    .estimatedPowerKw(updated.getEstimatedPowerKw())
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
            Long accountId, Long controlId
    ) {
        ControlEntity control = getOwnedControl(accountId, controlId);
        Set<ControlDeviceEntity> controlDeviceEntities = control.getControlDevices();
        return controlDeviceEntities.stream()
                .map(entity -> ControlDeviceResponse.builder()
                        .id(entity.getId())
                        .controlId(control.getId())
                        .deviceId(entity.getDevice().getId())
                        .deviceChannel(entity.getDeviceChannel())
                        .estimatedPowerKw(entity.getEstimatedPowerKw())
                        .device(
                                DeviceResponse.builder()
                                        .id(entity.getDevice().getId())
                                        .uuid(entity.getDevice().getUuid())
                                        .deviceType(entity.getDevice().getDeviceType())
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

    public List<ControlDeviceResponse> getControlDeviceLinks(
            Long accountId, Long controlId
    ) {
        getControl(accountId, controlId);
        return getControlDevices(accountId, controlId);
    }

    public ControlHeatPumpResponse addHeatPumpToControl(
            Long accountId, Long controlId, Long deviceId, String stateHex, ControlAction controlAction,
            ComparisonType comparisonType, BigDecimal priceLimit, BigDecimal estimatedPowerKw
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));
        if (control.getAccount().getId().equals(accountId)) {
            DeviceEntity device = getOwnedDevice(accountId, deviceId);

            ControlHeatPumpEntity entity = ControlHeatPumpEntity.builder()
                    .control(control)
                    .device(device)
                    .stateHex(stateHex)
                    .controlAction(controlAction)
                    .comparisonType(comparisonType)
                    .priceLimit(priceLimit)
                    .estimatedPowerKw(estimatedPowerKw)
                    .build();

            ControlHeatPumpEntity saved = controlHeatPumpRepository.save(entity);

            return ControlHeatPumpResponse.builder()
                    .id(saved.getId())
                    .controlId(saved.getControl().getId())
                    .deviceId(saved.getDevice().getId())
                    .stateHex(saved.getStateHex())
                    .controlAction(saved.getControlAction())
                    .comparisonType(saved.getComparisonType())
                    .priceLimit(saved.getPriceLimit())
                    .estimatedPowerKw(saved.getEstimatedPowerKw())
                    .build();
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public void deleteControlHeatPump(
            Long accountId, Long controlHeatPumpId
    ) {
        ControlHeatPumpEntity entity = controlHeatPumpRepository.findById(controlHeatPumpId)
                .orElseThrow(() -> new EntityNotFoundException("ControlHeatPump not found with id: " + controlHeatPumpId));
        if (entity.getControl().getAccount().getId().equals(accountId)) {
            controlHeatPumpRepository.deleteById(controlHeatPumpId);
        } else {
            throw new IllegalStateException("Forbidden!");
        }
    }

    public List<ControlHeatPumpResponse> getControlHeatPumps(
            Long accountId, Long controlId
    ) {
        ControlEntity control = getOwnedControl(accountId, controlId);
        Set<ControlHeatPumpEntity> entities = control.getControlHeatPumps();
        return entities.stream()
                .map(entity -> ControlHeatPumpResponse.builder()
                        .id(entity.getId())
                        .controlId(control.getId())
                        .deviceId(entity.getDevice().getId())
                        .stateHex(entity.getStateHex())
                        .controlAction(entity.getControlAction())
                        .comparisonType(entity.getComparisonType())
                        .priceLimit(entity.getPriceLimit())
                        .estimatedPowerKw(entity.getEstimatedPowerKw())
                        .device(
                                DeviceResponse.builder()
                                        .id(entity.getDevice().getId())
                                        .uuid(entity.getDevice().getUuid())
                                        .deviceType(entity.getDevice().getDeviceType())
                                        .deviceName(entity.getDevice().getDeviceName())
                                        .lastCommunication(entity.getDevice().getLastCommunication())
                                        .createdAt(entity.getDevice().getCreatedAt())
                                        .updatedAt(entity.getDevice().getUpdatedAt())
                                        .build()
                        )
                        .build())
                .sorted(Comparator.comparing(ControlHeatPumpResponse::getId))
                .toList();
    }

    private ControlEntity getOwnedControl(Long accountId, Long controlId) {
        return controlRepository.findByIdAndAccountId(controlId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found for account with id: " + controlId));
    }

    private DeviceEntity getOwnedDevice(Long accountId, Long deviceId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        return deviceRepository.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new EntityNotFoundException("Device not found for account with id: " + deviceId));
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
        device.setApiOnline(true);
        deviceRepository.save(device);

        Map<Integer, Integer> channelMap = new HashMap<>();
        if (!device.isEnabled()) {
            return channelMap;
        }

        channelMap.putAll(getBaseControlsForDevice(device, nowUtc));
        applyLoadSheddingOverrides(device, nowUtc, channelMap);
        enforcePowerLimitPriority(device, channelMap);

        return channelMap;
    }

    private Map<Integer, Integer> getBaseControlsForDevice(
            DeviceEntity device,
            Instant nowUtc
    ) {
        Map<Integer, Integer> channelMap = new HashMap<>();

        List<PowerLimitDeviceEntity> powerLimitDevices = powerLimitDeviceRepository.findByDevice(device);
        for (PowerLimitDeviceEntity pld : powerLimitDevices) {
            int channel = pld.getDeviceChannel();
            if (isPowerLimitActiveForDeviceChannel(device, channel)) {
                channelMap.put(channel, 0);
            }
        }

        Map<Integer, Integer> priorityWeatherOverrides = getWeatherOverrides(device, nowUtc, true);
        for (Map.Entry<Integer, Integer> entry : priorityWeatherOverrides.entrySet()) {
            channelMap.putIfAbsent(entry.getKey(), entry.getValue());
        }

        List<ProductionSourceDeviceEntity> prodDevices = productionSourceDeviceRepository.findByDevice(device);
        for (ProductionSourceDeviceEntity psd : prodDevices) {
            int channel = psd.getDeviceChannel();
            if (channelMap.containsKey(channel)) continue;
            Integer productionOverride = getProductionOverride(device, channel);
            if (productionOverride != null) {
                channelMap.put(channel, productionOverride);
            }
        }

        Map<Integer, Integer> weatherOverrides = getWeatherOverrides(device, nowUtc, false);
        for (Map.Entry<Integer, Integer> entry : weatherOverrides.entrySet()) {
            channelMap.putIfAbsent(entry.getKey(), entry.getValue());
        }

        List<ControlDeviceEntity> controlDevices = controlDeviceRepository.findByDevice(device);
        for (ControlDeviceEntity cd : controlDevices) {
            int channel = cd.getDeviceChannel();
            if (channelMap.containsKey(channel)) continue;

            ControlEntity control = cd.getControl();
            ZoneId controlZone = ZoneId.of(control.getTimezone());
            ControlMode mode = control.getMode();

            if (mode.equals(ControlMode.MANUAL)) {
                channelMap.put(cd.getDeviceChannel(), control.isManualOn() ? 1 : 0);
            } else if (mode.equals(ControlMode.CHEAPEST_HOURS) || mode.equals(ControlMode.BELOW_MAX_PRICE)) {
                ZonedDateTime nowInControlZone = nowUtc.atZone(controlZone);

                boolean active = controlTableRepository.findByControlIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                                control.getId(), Status.FINAL, nowUtc.minusSeconds(CONTROL_LOOKBACK_SECONDS))
                        .stream()
                        .anyMatch(ct -> {
                            ZonedDateTime start = ct.getStartTime().atZone(controlZone);
                            ZonedDateTime end = ct.getEndTime().atZone(controlZone);
                            return !nowInControlZone.isBefore(start) && !nowInControlZone.isAfter(end);
                        });

                channelMap.put(cd.getDeviceChannel(), active ? 1 : 0);
            } else {
                channelMap.put(cd.getDeviceChannel(), 0);
            }
        }

        return channelMap;
    }

    private void applyLoadSheddingOverrides(
            DeviceEntity targetDevice,
            Instant nowUtc,
            Map<Integer, Integer> channelMap
    ) {
        if (targetDevice.getDeviceType() != DeviceType.STANDARD) {
            return;
        }

        Long accountId = targetDevice.getAccount().getId();
        List<LoadSheddingNodeEntity> nodes = loadSheddingNodeRepository.findByAccountIdOrderByIdAsc(accountId);
        if (nodes.isEmpty()) {
            return;
        }

        List<LoadSheddingLinkEntity> links = loadSheddingLinkRepository.findByAccountIdOrderByIdAsc(accountId);
        if (links.isEmpty()) {
            return;
        }

        Map<Long, Map<Integer, Integer>> baseStatesByDeviceId = new HashMap<>();
        Map<Long, Integer> stateByNodeId = new HashMap<>();

        for (LoadSheddingNodeEntity node : nodes) {
            Map<Integer, Integer> deviceBaseStates = baseStatesByDeviceId.computeIfAbsent(
                    node.getDevice().getId(),
                    ignored -> getBaseControlsForDevice(node.getDevice(), nowUtc)
            );
            stateByNodeId.put(node.getId(), deviceBaseStates.getOrDefault(node.getDeviceChannel(), 0));
        }

        int maxIterations = Math.max(links.size() * 4, 8);
        for (int i = 0; i < maxIterations; i++) {
            boolean changed = false;
            for (LoadSheddingLinkEntity link : links) {
                Integer sourceState = stateByNodeId.get(link.getSourceNode().getId());
                if (sourceState == null || !matchesLoadSheddingTrigger(sourceState, link.getTriggerState())) {
                    continue;
                }

                int nextTargetState = link.getTargetAction() == ControlAction.TURN_ON ? 1 : 0;
                Long targetNodeId = link.getTargetNode().getId();
                if (!Objects.equals(stateByNodeId.get(targetNodeId), nextTargetState)) {
                    stateByNodeId.put(targetNodeId, nextTargetState);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        for (LoadSheddingNodeEntity node : nodes) {
            if (node.getDevice().getId().equals(targetDevice.getId())) {
                channelMap.put(node.getDeviceChannel(), stateByNodeId.getOrDefault(node.getId(), 0));
            }
        }
    }

    private boolean matchesLoadSheddingTrigger(
            Integer sourceState,
            LoadSheddingTriggerState triggerState
    ) {
        if (sourceState == null || triggerState == null) {
            return false;
        }
        return switch (triggerState) {
            case TURNED_ON -> sourceState == 1;
            case TURNED_OFF -> sourceState == 0;
        };
    }

    private void enforcePowerLimitPriority(
            DeviceEntity device,
            Map<Integer, Integer> channelMap
    ) {
        for (PowerLimitDeviceEntity pld : powerLimitDeviceRepository.findByDevice(device)) {
            int channel = pld.getDeviceChannel();
            if (isPowerLimitActiveForDeviceChannel(device, channel)) {
                channelMap.put(channel, 0);
            }
        }
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

    private Map<Integer, Integer> getWeatherOverrides(
            DeviceEntity device,
            Instant now,
            boolean priorityRule
    ) {
        Map<Integer, Integer> overrides = new HashMap<>();
        List<WeatherControlDeviceEntity> rules = weatherControlDeviceRepository.findByDevice(device);
        for (WeatherControlDeviceEntity rule : rules) {
            if (rule.isPriorityRule() != priorityRule) {
                continue;
            }
            Integer weatherOverride = getWeatherOverride(rule, now);
            if (weatherOverride != null) {
                overrides.putIfAbsent(rule.getDeviceChannel(), weatherOverride);
            }
        }
        return overrides;
    }

    private Integer getWeatherOverride(
            WeatherControlDeviceEntity rule,
            Instant now
    ) {
        if (rule.getWeatherControl() == null || rule.getWeatherControl().getSite() == null) {
            return null;
        }
        Optional<BigDecimal> metricValue = getCurrentWeatherMetricValue(
                rule.getWeatherControl().getSite(),
                rule.getWeatherMetric(),
                now
        );
        if (metricValue.isEmpty() || !matches(metricValue.get(), rule.getComparisonType(), rule.getThresholdValue())) {
            return null;
        }
        return rule.getControlAction() == ControlAction.TURN_OFF ? 0 : 1;
    }

    private Optional<BigDecimal> getCurrentWeatherMetricValue(
            SiteEntity site,
            WeatherMetricType metricType,
            Instant now
    ) {
        Optional<SiteWeatherEntity> weather = siteWeatherRepository
                .findFirstBySiteAndForecastTimeLessThanEqualOrderByForecastTimeDesc(site, now)
                .or(() -> siteWeatherRepository.findFirstBySiteAndForecastTimeGreaterThanEqualOrderByForecastTimeAsc(site, now));

        return weather.map(entity -> switch (metricType) {
            case TEMPERATURE -> entity.getTemperature();
            case HUMIDITY -> entity.getHumidity();
        });
    }

    private boolean matches(
            BigDecimal actualValue,
            ComparisonType comparisonType,
            BigDecimal thresholdValue
    ) {
        if (actualValue == null || comparisonType == null || thresholdValue == null) {
            return false;
        }
        int comparison = actualValue.compareTo(thresholdValue);
        return switch (comparisonType) {
            case GREATER_THAN -> comparison > 0;
            case LESS_THAN -> comparison < 0;
        };
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
                    controlTableRepository.findByControlIdAndStatusAndStartTimeBetweenOrderByStartTimeAsc(
                            control.getId(), Status.FINAL, fromUtc, toUtc);

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

    public void mqttDeviceControls() {
        List<DeviceEntity> mqttDevices = deviceRepository.findByMqttOnlineTrue();
        List<MqttControlCommand> commands = new ArrayList<>();
        for (DeviceEntity device : mqttDevices) {
            String uuid = device.getUuid().toString();
            Map<Integer, Integer> controls = getControlsForDevice(uuid);
            for (Map.Entry<Integer, Integer> entry : controls.entrySet()) {
                Integer channel = entry.getKey();
                boolean on = entry.getValue() != null && entry.getValue() == 1;
                commands.add(new MqttControlCommand(uuid, channel, on));
            }
        }
        commands.stream()
                .sorted(Comparator
                        .comparing(MqttControlCommand::on)
                        .thenComparing(MqttControlCommand::uuid)
                        .thenComparing(MqttControlCommand::channel))
                .forEach(command -> mqttService.switchControl(command.uuid(), command.channel(), command.on()));
    }

    private record MqttControlCommand(String uuid, Integer channel, boolean on) {
    }

    public void sendDebugMqttRelayCommand(Long accountId, Long deviceId, int channel, boolean on) {
        if (channel < 0 || channel > 3) {
            throw new IllegalArgumentException("Unsupported relay channel: " + channel);
        }

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        DeviceEntity device = deviceRepository.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (device.getDeviceType() != DeviceType.STANDARD) {
            throw new IllegalArgumentException("Debug relay command is only supported for standard devices");
        }
        if (!device.isMqttOnline()) {
            throw new IllegalArgumentException("Device is not connected with MQTT");
        }

        mqttService.switchControl(device.getUuid().toString(), channel, on);
    }

}
