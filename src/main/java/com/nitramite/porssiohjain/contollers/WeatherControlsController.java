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
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import com.nitramite.porssiohjain.services.WeatherControlService;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlDeviceResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlHeatPumpResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/weather-controls")
@RequiredArgsConstructor
@RequireAuth
public class WeatherControlsController {

    private final AuthContext authContext;
    private final WeatherControlService weatherControlService;

    @GetMapping
    public List<WeatherControlResponse> listWeatherControls() {
        return weatherControlService.getAllWeatherControls(authContext.getAccountId());
    }

    @PostMapping
    public WeatherControlResponse createWeatherControl(@RequestBody WeatherControlRequest request) {
        Long accountId = authContext.getAccountId();
        Long id = weatherControlService.createWeatherControl(accountId, request.name(), request.siteId()).getId();
        return weatherControlService.getWeatherControl(accountId, id);
    }

    @GetMapping("/{weatherControlId}")
    public WeatherControlResponse getWeatherControl(@PathVariable Long weatherControlId) {
        return weatherControlService.getWeatherControl(authContext.getAccountId(), weatherControlId);
    }

    @GetMapping("/{weatherControlId}/weather")
    public SiteWeatherForecastResponse getWeatherForecast(
            @PathVariable Long weatherControlId,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false) Instant end
    ) {
        return weatherControlService.getStoredWeatherForecast(authContext.getAccountId(), weatherControlId, start, end);
    }

    @PutMapping("/{weatherControlId}")
    public WeatherControlResponse updateWeatherControl(
            @PathVariable Long weatherControlId,
            @RequestBody WeatherControlRequest request
    ) {
        return weatherControlService.updateWeatherControl(
                authContext.getAccountId(),
                weatherControlId,
                request.name(),
                request.siteId()
        );
    }

    @GetMapping("/{weatherControlId}/devices")
    public List<WeatherControlDeviceResponse> getDevices(@PathVariable Long weatherControlId) {
        return weatherControlService.getWeatherControlDevices(authContext.getAccountId(), weatherControlId);
    }

    @PostMapping("/{weatherControlId}/devices")
    public WeatherControlDeviceResponse addDevice(
            @PathVariable Long weatherControlId,
            @RequestBody WeatherControlDeviceRequest request
    ) {
        return weatherControlService.addDeviceToWeatherControl(
                authContext.getAccountId(),
                weatherControlId,
                request.deviceId(),
                request.deviceChannel(),
                request.weatherMetric(),
                request.comparisonType(),
                request.thresholdValue(),
                request.controlAction(),
                request.priorityRule()
        );
    }

    @PutMapping("/devices/{linkId}")
    public WeatherControlDeviceResponse updateDevice(
            @PathVariable Long linkId,
            @RequestBody WeatherControlDeviceRequest request
    ) {
        return weatherControlService.updateWeatherControlDevice(
                authContext.getAccountId(),
                linkId,
                request.deviceId(),
                request.deviceChannel(),
                request.weatherMetric(),
                request.comparisonType(),
                request.thresholdValue(),
                request.controlAction(),
                request.priorityRule()
        );
    }

    @DeleteMapping("/devices/{linkId}")
    public void deleteDevice(@PathVariable Long linkId) {
        weatherControlService.deleteWeatherControlDevice(authContext.getAccountId(), linkId);
    }

    @GetMapping("/{weatherControlId}/heat-pumps")
    public List<WeatherControlHeatPumpResponse> getHeatPumps(@PathVariable Long weatherControlId) {
        return weatherControlService.getWeatherControlHeatPumps(authContext.getAccountId(), weatherControlId);
    }

    @PostMapping("/{weatherControlId}/heat-pumps")
    public WeatherControlHeatPumpResponse addHeatPump(
            @PathVariable Long weatherControlId,
            @RequestBody WeatherControlHeatPumpRequest request
    ) {
        return weatherControlService.addHeatPumpToWeatherControl(
                authContext.getAccountId(),
                weatherControlId,
                request.deviceId(),
                request.stateHex(),
                request.weatherMetric(),
                request.comparisonType(),
                request.thresholdValue()
        );
    }

    @PutMapping("/heat-pumps/{linkId}")
    public WeatherControlHeatPumpResponse updateHeatPump(
            @PathVariable Long linkId,
            @RequestBody WeatherControlHeatPumpRequest request
    ) {
        return weatherControlService.updateWeatherControlHeatPump(
                authContext.getAccountId(),
                linkId,
                request.deviceId(),
                request.stateHex(),
                request.weatherMetric(),
                request.comparisonType(),
                request.thresholdValue()
        );
    }

    @DeleteMapping("/heat-pumps/{linkId}")
    public void deleteHeatPump(@PathVariable Long linkId) {
        weatherControlService.deleteWeatherControlHeatPump(authContext.getAccountId(), linkId);
    }

    public record WeatherControlRequest(String name, Long siteId) {
    }

    public record WeatherControlDeviceRequest(
            Long deviceId,
            Integer deviceChannel,
            WeatherMetricType weatherMetric,
            ComparisonType comparisonType,
            BigDecimal thresholdValue,
            ControlAction controlAction,
            boolean priorityRule
    ) {
    }

    public record WeatherControlHeatPumpRequest(
            Long deviceId,
            String stateHex,
            WeatherMetricType weatherMetric,
            ComparisonType comparisonType,
            BigDecimal thresholdValue
    ) {
    }
}
