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

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerPlanEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerPlanPointEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.enums.ContractType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanPointRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerActiveControlService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerAutomationService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/heating-planner")
@RequiredArgsConstructor
@RequireAuth
public class HeatingPlannerController {

    private final AuthContext authContext;
    private final SiteRepository siteRepository;
    private final DeviceRepository deviceRepository;
    private final ElectricityContractRepository contractRepository;
    private final HeatingPlannerConfigurationService configurationService;
    private final HeatingPlannerActiveControlService activeControlService;
    private final HeatingPlannerAutomationService automationService;
    private final HeatingPlannerPlanRepository planRepository;
    private final HeatingPlannerPlanPointRepository pointRepository;

    @GetMapping
    public HeatingPlannerResponse getHeatingPlanner(@RequestParam(required = false) Long siteId) {
        Long accountId = authContext.getAccountId();
        List<SiteChoiceResponse> sites = siteRepository.findByAccountId(accountId).stream()
                .map(site -> new SiteChoiceResponse(site.getId(), site.getName(), site.getTimezone(), site.getWeatherPlace()))
                .toList();
        Long selectedSiteId = siteId != null ? siteId : configurationService.preferredSiteId(accountId)
                .or(() -> sites.stream().findFirst().map(SiteChoiceResponse::id))
                .orElse(null);
        HeatingPlannerConfigurationService.Configuration configuration = selectedSiteId == null
                ? null : configurationService.configuration(accountId, selectedSiteId);
        HeatingPlannerActiveControlService.Readiness readiness = selectedSiteId == null
                ? new HeatingPlannerActiveControlService.Readiness(false, false,
                List.of("Create a site before configuring Heating Planner"), null, null, null, null)
                : activeControlService.readiness(accountId, selectedSiteId, Instant.now());

        return new HeatingPlannerResponse(
                selectedSiteId,
                sites,
                selectableDevices(accountId),
                transferContracts(accountId),
                configuration == null ? defaultConfiguration() : configurationResponse(configuration),
                readinessResponse(readiness),
                selectedSiteId == null ? null : latestPlan(accountId, selectedSiteId)
        );
    }

    @PutMapping("/{siteId}")
    public HeatingPlannerResponse saveHeatingPlanner(@PathVariable Long siteId,
                                                     @RequestBody HeatingPlannerSaveRequest request) {
        Long accountId = authContext.getAccountId();
        configurationService.save(accountId, siteId,
                new HeatingPlannerConfigurationService.SettingsConfiguration(
                        request.configuration().enabled(),
                        request.configuration().plannerActiveBelowTemperature(),
                        request.configuration().woodRecommendationBelowTemperature(),
                        request.configuration().taxPercent(),
                        request.configuration().transferContractId(),
                        request.configuration().stoveLoaded(),
                        request.configuration().stoveAvailableFrom(),
                        request.configuration().stoveAvailableTo(),
                        request.configuration().woodAmount(),
                        request.configuration().woodReleaseDelayMinutes(),
                        request.configuration().woodReleaseDurationMinutes(),
                        request.configuration().cheapPriceThreshold(),
                        request.configuration().expensivePriceThreshold(),
                        request.configuration().cheapPricePercentile(),
                        request.configuration().expensivePricePercentile()
                ),
                request.configuration().rooms().stream()
                        .map(room -> new HeatingPlannerConfigurationService.RoomConfiguration(
                                room.name(),
                                room.sourceType(),
                                room.targetRoomTemperature(),
                                room.normalFloorTemperature(),
                                room.maximumPreheatFloorTemperature(),
                                room.absoluteMaximumFloorTemperature(),
                                room.dischargeFloorSetpoint(),
                                room.controllingDeviceId(),
                                room.roomSensorDeviceId(),
                                room.floorSensorDeviceId()
                        ))
                        .toList());
        return getHeatingPlanner(siteId);
    }

    @PostMapping("/{siteId}/active-control")
    public HeatingPlannerActiveControlResponse enableActiveControl(@PathVariable Long siteId) {
        Long accountId = authContext.getAccountId();
        activeControlService.activate(accountId, siteId, Instant.now());
        return readinessResponse(activeControlService.readiness(accountId, siteId, Instant.now()));
    }

    @PostMapping("/{siteId}/recalculate")
    public HeatingPlannerResponse recalculate(@PathVariable Long siteId) {
        Long accountId = authContext.getAccountId();
        automationService.generateForSite(accountId, siteId, Instant.now());
        return getHeatingPlanner(siteId);
    }

    @DeleteMapping("/{siteId}/active-control")
    public ResponseEntity<HeatingPlannerActiveControlResponse> disableActiveControl(@PathVariable Long siteId) {
        Long accountId = authContext.getAccountId();
        activeControlService.disable(accountId, siteId, Instant.now());
        return ResponseEntity.ok(readinessResponse(activeControlService.readiness(accountId, siteId, Instant.now())));
    }

    private List<DeviceChoiceResponse> selectableDevices(Long accountId) {
        return deviceRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.THERMOSTAT
                        || device.getDeviceType() == DeviceType.TEMPERATURE_SENSOR)
                .map(device -> new DeviceChoiceResponse(device.getId(), device.getDeviceName(), device.getDeviceType()))
                .toList();
    }

    private List<TransferContractChoiceResponse> transferContracts(Long accountId) {
        return contractRepository.findByAccountId(accountId).stream()
                .filter(contract -> contract.getType() == ContractType.TRANSFER)
                .map(contract -> new TransferContractChoiceResponse(contract.getId(), contract.getName()))
                .toList();
    }

    private HeatingPlannerPlanResponse latestPlan(Long accountId, Long siteId) {
        HeatingPlannerPlanEntity plan = planRepository.findByAccountIdAndSiteIdOrderByCreatedAtDesc(accountId, siteId)
                .stream()
                .filter(candidate -> candidate.getStatus() == HeatingPlannerPlanStatus.ACTIVE
                        || candidate.getStatus() == HeatingPlannerPlanStatus.SIMULATED)
                .findFirst()
                .orElse(null);
        if (plan == null) {
            return null;
        }
        List<HeatingPlannerPlanPointResponse> points = pointRepository.findByPlanVersion(plan.getPlanVersion())
                .stream()
                .sorted(Comparator.comparing(HeatingPlannerPlanPointEntity::getPlannedTime)
                        .thenComparing(point -> point.getRoom().getSortOrder())
                        .thenComparing(point -> point.getRoom().getName()))
                .map(point -> new HeatingPlannerPlanPointResponse(
                        point.getRoom().getName(),
                        point.getPlannedTime(),
                        point.getPriceCentsPerKwh(),
                        point.getOutdoorTemperature(),
                        point.getPredictedFloorTemperature(),
                        point.getPredictedRoomTemperature(),
                        point.getPlannedFloorSetpoint(),
                        point.getPredictedWoodHeatRate(),
                        point.isHeating(),
                        point.getOperatingMode().name(),
                        point.getReason()
                ))
                .toList();
        return new HeatingPlannerPlanResponse(plan.getPlanVersion().toString(), plan.getStatus().name(),
                plan.getCreatedAt(), plan.getHorizonStart(), plan.getHorizonEnd(), plan.getTriggerReason(), points);
    }

    private HeatingPlannerConfigurationResponse configurationResponse(
            HeatingPlannerConfigurationService.Configuration configuration) {
        return new HeatingPlannerConfigurationResponse(
                configuration.enabled(),
                configuration.plannerActiveBelowTemperature(),
                configuration.woodRecommendationBelowTemperature(),
                configuration.taxPercent(),
                configuration.transferContractId(),
                configuration.stoveLoaded(),
                configuration.stoveAvailableFrom(),
                configuration.stoveAvailableTo(),
                configuration.woodAmount(),
                configuration.woodReleaseDelayMinutes(),
                configuration.woodReleaseDurationMinutes(),
                configuration.cheapPriceThreshold(),
                configuration.expensivePriceThreshold(),
                configuration.cheapPricePercentile(),
                configuration.expensivePricePercentile(),
                configuration.rooms().stream()
                        .map(room -> new HeatingPlannerRoomResponse(
                                room.name(),
                                room.sourceType(),
                                room.targetRoomTemperature(),
                                room.normalFloorTemperature(),
                                room.maximumPreheatFloorTemperature(),
                                room.absoluteMaximumFloorTemperature(),
                                room.dischargeFloorSetpoint(),
                                room.controllingDeviceId(),
                                room.roomSensorDeviceId(),
                                room.floorSensorDeviceId()
                        ))
                        .toList()
        );
    }

    private HeatingPlannerConfigurationResponse defaultConfiguration() {
        return configurationResponse(new HeatingPlannerConfigurationService.Configuration(false,
                new BigDecimal("5.00"), new BigDecimal("0.00"), new BigDecimal("25.50"), null,
                false, LocalTime.of(6, 0), LocalTime.of(22, 0), new BigDecimal("8.00"),
                45, 360, new BigDecimal("5.0000"), new BigDecimal("20.0000"),
                new BigDecimal("0.2500"), new BigDecimal("0.7500"), List.of()));
    }

    private HeatingPlannerActiveControlResponse readinessResponse(HeatingPlannerActiveControlService.Readiness readiness) {
        return new HeatingPlannerActiveControlResponse(readiness.ready(), readiness.active(), readiness.issues(),
                readiness.candidatePlanVersion(), readiness.lastAutomaticPlanAt(),
                readiness.lastAutomaticActivationAt(), readiness.lastAutomationError());
    }

    public record HeatingPlannerResponse(
            Long selectedSiteId,
            List<SiteChoiceResponse> sites,
            List<DeviceChoiceResponse> devices,
            List<TransferContractChoiceResponse> transferContracts,
            HeatingPlannerConfigurationResponse configuration,
            HeatingPlannerActiveControlResponse activeControl,
            HeatingPlannerPlanResponse latestPlan
    ) {
    }

    public record HeatingPlannerSaveRequest(HeatingPlannerConfigurationResponse configuration) {
    }

    public record SiteChoiceResponse(Long id, String name, String timezone, String weatherPlace) {
    }

    public record DeviceChoiceResponse(Long id, String name, DeviceType type) {
    }

    public record TransferContractChoiceResponse(Long id, String name) {
    }

    public record HeatingPlannerConfigurationResponse(
            boolean enabled,
            BigDecimal plannerActiveBelowTemperature,
            BigDecimal woodRecommendationBelowTemperature,
            BigDecimal taxPercent,
            Long transferContractId,
            boolean stoveLoaded,
            LocalTime stoveAvailableFrom,
            LocalTime stoveAvailableTo,
            BigDecimal woodAmount,
            Integer woodReleaseDelayMinutes,
            Integer woodReleaseDurationMinutes,
            BigDecimal cheapPriceThreshold,
            BigDecimal expensivePriceThreshold,
            BigDecimal cheapPricePercentile,
            BigDecimal expensivePricePercentile,
            List<HeatingPlannerRoomResponse> rooms
    ) {
    }

    public record HeatingPlannerRoomResponse(
            String name,
            HeatingPlannerHeatSourceType sourceType,
            BigDecimal targetRoomTemperature,
            BigDecimal normalFloorTemperature,
            BigDecimal maximumPreheatFloorTemperature,
            BigDecimal absoluteMaximumFloorTemperature,
            BigDecimal dischargeFloorSetpoint,
            Long controllingDeviceId,
            Long roomSensorDeviceId,
            Long floorSensorDeviceId
    ) {
    }

    public record HeatingPlannerActiveControlResponse(
            boolean ready,
            boolean active,
            List<String> issues,
            String candidatePlanVersion,
            Instant lastAutomaticPlanAt,
            Instant lastAutomaticActivationAt,
            String lastAutomationError
    ) {
    }

    public record HeatingPlannerPlanResponse(
            String planVersion,
            String status,
            Instant createdAt,
            Instant horizonStart,
            Instant horizonEnd,
            String triggerReason,
            List<HeatingPlannerPlanPointResponse> points
    ) {
    }

    public record HeatingPlannerPlanPointResponse(
            String room,
            Instant plannedTime,
            BigDecimal priceCentsPerKwh,
            BigDecimal outdoorTemperature,
            BigDecimal predictedFloorTemperature,
            BigDecimal predictedRoomTemperature,
            BigDecimal plannedFloorSetpoint,
            BigDecimal predictedWoodHeatRate,
            boolean heating,
            String operatingMode,
            String reason
    ) {
    }
}
