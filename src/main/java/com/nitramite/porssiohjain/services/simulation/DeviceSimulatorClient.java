/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.simulation;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.services.DeviceAcCommandLogService;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** Private transport for simulated Toshiba devices; deliberately independent of cloud/proxy transport. */
@Service
@Slf4j
public class DeviceSimulatorClient {
    private final DeviceSimulatorProperties properties;
    private final DeviceAcDataRepository acRepository;
    private final DeviceAcCommandLogService commandLog;
    private final RestTemplate restTemplate;

    public DeviceSimulatorClient(DeviceSimulatorProperties properties, DeviceAcDataRepository acRepository,
                                 DeviceAcCommandLogService commandLog) {
        this.properties = properties;
        this.acRepository = acRepository;
        this.commandLog = commandLog;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        restTemplate = new RestTemplate(factory);
    }

    public State getState(DeviceAcDataEntity acData) {
        var response = restTemplate.exchange(url(acData, "/state"), HttpMethod.GET,
                new HttpEntity<>(headers()), State.class).getBody();
        if (response == null || response.observedAt() == null || response.stateHex() == null
                || !response.stateHex().matches("[0-9a-fA-F]{38}")) {
            throw new RestClientException("Invalid simulator state response");
        }
        return response;
    }

    public void sendCommand(DeviceAcDataEntity acData, String stateHex) {
        if (stateHex == null || !stateHex.matches("[0-9a-fA-F]{38}")) {
            throw new IllegalArgumentException("Simulated Toshiba state must contain exactly 19 hex bytes");
        }
        String normalized = stateHex.toUpperCase(Locale.ROOT);
        var response = restTemplate.exchange(url(acData, "/commands"), HttpMethod.POST,
                new HttpEntity<>(Map.of("stateHex", normalized), headers()), Acceptance.class).getBody();
        if (response == null || !response.accepted()) {
            throw new RestClientException("Simulator did not accept the command");
        }
        // Accepted command and subsequently observed state remain separate, as with the real integration.
        acData.setLastSentStateHex(normalized);
        acRepository.save(acData);
        try {
            commandLog.logSentCommand(acData.getDevice().getId(), "SIMULATED Toshiba stateHex=" + normalized);
        } catch (RuntimeException exception) {
            log.warn("Failed to log accepted simulated command. deviceId={}", acData.getDevice().getId());
        }
    }

    private URI url(DeviceAcDataEntity acData, String suffix) {
        var device = acData.getDevice();
        if (device == null || !device.isSimulated() || !device.isEnabled() || device.getUuid() == null
                || device.getAccount() == null || !device.getAccount().isDemo() || acData.getAcType() != AcType.TOSHIBA) {
            throw new IllegalArgumentException("Device is not an enabled demo Toshiba simulation");
        }
        if (properties.getBaseUrl().isBlank() || properties.getToken().isBlank()) {
            throw new RestClientException("Device simulator is not configured");
        }
        return URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/heat-pumps/" + device.getUuid() + suffix);
    }

    private HttpHeaders headers() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(properties.getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public record State(String stateHex, Instant observedAt) {}
    public record Acceptance(boolean accepted) {}
}
