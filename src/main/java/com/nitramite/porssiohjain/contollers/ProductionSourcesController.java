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
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.ProductionApiType;
import com.nitramite.porssiohjain.services.ProductionSourceService;
import com.nitramite.porssiohjain.services.models.ProductionSourceDeviceResponse;
import com.nitramite.porssiohjain.services.models.ProductionSourceHeatPumpResponse;
import com.nitramite.porssiohjain.services.models.ProductionHistoryResponse;
import com.nitramite.porssiohjain.services.models.ProductionSourceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/production-sources")
@RequiredArgsConstructor
@RequireAuth
public class ProductionSourcesController {

    private final AuthContext authContext;
    private final ProductionSourceService productionSourceService;

    @GetMapping
    public List<ProductionSourceResponse> listSources() {
        return productionSourceService.getAllSources(authContext.getAccountId());
    }

    @PostMapping
    public void createSource(@RequestBody ProductionSourceRequest request) {
        productionSourceService.createSource(
                authContext.getAccountId(),
                request.name(),
                request.apiType(),
                request.appId(),
                request.appSecret(),
                request.email(),
                request.password(),
                request.stationId(),
                request.enabled()
        );
    }

    @GetMapping("/{sourceId}")
    public ProductionSourceResponse getSource(@PathVariable Long sourceId) {
        return productionSourceService.getSource(authContext.getAccountId(), sourceId);
    }

    @GetMapping("/{sourceId}/history")
    public List<ProductionHistoryResponse> getHistory(
            @PathVariable Long sourceId,
            @RequestParam(defaultValue = "24") int hours
    ) {
        return productionSourceService.getProductionHistory(authContext.getAccountId(), sourceId, hours);
    }

    @PutMapping("/{sourceId}")
    public ProductionSourceResponse updateSource(
            @PathVariable Long sourceId,
            @RequestBody ProductionSourceRequest request
    ) {
        Long accountId = authContext.getAccountId();
        productionSourceService.updateSource(
                accountId,
                sourceId,
                request.name(),
                request.enabled(),
                request.timezone(),
                request.appId(),
                request.appSecret(),
                request.email(),
                request.password(),
                request.stationId(),
                request.siteId()
        );
        return productionSourceService.getSource(accountId, sourceId);
    }

    @DeleteMapping("/{sourceId}")
    public void deleteSource(@PathVariable Long sourceId) {
        productionSourceService.deleteProductionSource(authContext.getAccountId(), sourceId);
    }

    @GetMapping("/{sourceId}/devices")
    public List<ProductionSourceDeviceResponse> getDevices(@PathVariable Long sourceId) {
        return productionSourceService.getSourceDevices(authContext.getAccountId(), sourceId);
    }

    @PostMapping("/{sourceId}/devices")
    public void addDevice(
            @PathVariable Long sourceId,
            @RequestBody ProductionSourceDeviceRequest request
    ) {
        productionSourceService.addDevice(
                authContext.getAccountId(),
                sourceId,
                request.deviceId(),
                request.deviceChannel(),
                request.triggerKw(),
                request.comparisonType(),
                request.action()
        );
    }

    @DeleteMapping("/{sourceId}/devices/{linkId}")
    public void deleteDevice(@PathVariable Long sourceId, @PathVariable Long linkId) {
        productionSourceService.removeDevice(authContext.getAccountId(), sourceId, linkId);
    }

    @GetMapping("/{sourceId}/heat-pumps")
    public List<ProductionSourceHeatPumpResponse> getHeatPumps(@PathVariable Long sourceId) {
        return productionSourceService.getSourceHeatPumps(authContext.getAccountId(), sourceId);
    }

    @PostMapping("/{sourceId}/heat-pumps")
    public ProductionSourceHeatPumpResponse addHeatPump(
            @PathVariable Long sourceId,
            @RequestBody ProductionSourceHeatPumpRequest request
    ) {
        return productionSourceService.addHeatPump(
                authContext.getAccountId(),
                sourceId,
                request.deviceId(),
                request.stateHex(),
                request.controlAction(),
                request.comparisonType(),
                request.triggerKw()
        );
    }

    @PutMapping("/heat-pumps/{linkId}")
    public ProductionSourceHeatPumpResponse updateHeatPump(
            @PathVariable Long linkId,
            @RequestBody ProductionSourceHeatPumpUpdateRequest request
    ) {
        return productionSourceService.updateHeatPump(
                authContext.getAccountId(),
                request.sourceId(),
                linkId,
                request.deviceId(),
                request.stateHex(),
                request.controlAction(),
                request.comparisonType(),
                request.triggerKw()
        );
    }

    @DeleteMapping("/{sourceId}/heat-pumps/{linkId}")
    public void deleteHeatPump(@PathVariable Long sourceId, @PathVariable Long linkId) {
        productionSourceService.removeHeatPump(authContext.getAccountId(), sourceId, linkId);
    }

    public record ProductionSourceRequest(
            String name,
            ProductionApiType apiType,
            String appId,
            String appSecret,
            String email,
            String password,
            String stationId,
            boolean enabled,
            String timezone,
            Long siteId
    ) {
    }

    public record ProductionSourceDeviceRequest(
            Long deviceId,
            Integer deviceChannel,
            BigDecimal triggerKw,
            ComparisonType comparisonType,
            ControlAction action
    ) {
    }

    public record ProductionSourceHeatPumpRequest(
            Long deviceId,
            String stateHex,
            ControlAction controlAction,
            ComparisonType comparisonType,
            BigDecimal triggerKw
    ) {
    }

    public record ProductionSourceHeatPumpUpdateRequest(
            Long sourceId,
            Long deviceId,
            String stateHex,
            ControlAction controlAction,
            ComparisonType comparisonType,
            BigDecimal triggerKw
    ) {
    }
}
