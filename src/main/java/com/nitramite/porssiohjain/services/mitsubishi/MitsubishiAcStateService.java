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

package com.nitramite.porssiohjain.services.mitsubishi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.models.AcLoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiAcStateService {

    private static final long RETRY_DELAY_MS = 1000L;
    private static final Duration ONLINE_THRESHOLD = Duration.ofHours(4);
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GET_DEVICE_URL = "https://app.melcloud.com/Mitsubishi.Wifi.Client/Device/Get";
    private final MitsubishiLoginService mitsubishiLoginService;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MitsubishiAcStateResponse getAcState(DeviceAcDataEntity acData) {
        return getAcStateInternal(acData, true);
    }

    private MitsubishiAcStateResponse getAcStateInternal(DeviceAcDataEntity acData, boolean retryOnAuthorizationFailure) {
        try {
            if (retryOnAuthorizationFailure && shouldRefreshAccessToken(acData)) {
                log.info("Mitsubishi AC state query requires fresh access token, attempting login");
                AcLoginResponse acLoginResponse = mitsubishiLoginService.login(acData);
                if (!acLoginResponse.isSuccess()) {
                    log.error("Mitsubishi AC state query failed because re-login did not return an access token");
                    return null;
                }
                acData.setAcAccessToken(acLoginResponse.getAccessToken());
                waitBeforeRetry();
                return getAcStateInternal(acData, false);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MitsContextKey", acData.getAcAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json, text/javascript, */*; q=0.01");
            headers.set("X-Requested-With", "XMLHttpRequest");
            headers.set("Cookie", "policyaccepted=true");
            headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:73.0) Gecko/20100101 Firefox/73.0");

            String urlWithParams = UriComponentsBuilder.fromUriString(GET_DEVICE_URL)
                    .queryParam("id", acData.getAcDeviceId())
                    .queryParam("buildingID", acData.getBuildingId())
                    .build(true)
                    .toUriString();
            
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    urlWithParams,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            String rawBody = responseEntity.getBody();
            log.info(
                    "Mitsubishi AC state response. status={}, body={}",
                    responseEntity.getStatusCode(),
                    rawBody
            );

            MitsubishiAcStateResponse body = rawBody != null && !rawBody.isBlank()
                    ? objectMapper.readValue(rawBody, MitsubishiAcStateResponse.class)
                    : null;
            if (body != null) {
                if (body.getDeviceId() != null) {
                    acData.setAcDeviceId(String.valueOf(body.getDeviceId()));
                }
                acData.setLastPolledStateHex(formatStateJson(body));
                deviceAcDataRepository.save(acData);
                updateDeviceConnectivity(acData, body);
            }
            return body;
        } catch (HttpClientErrorException.Unauthorized e) {
            MitsubishiAcStateResponse response = retryAfterAuthorizationFailure(acData, retryOnAuthorizationFailure, 401);
            if (response != null) {
                return response;
            }
            throw e;
        } catch (HttpClientErrorException.Forbidden e) {
            MitsubishiAcStateResponse response = retryAfterAuthorizationFailure(acData, retryOnAuthorizationFailure, 403);
            if (response != null) {
                return response;
            }
            throw e;
        } catch (Exception e) {
            log.error("Error fetching Mitsubishi AC state for device id: {}", acData.getAcDeviceId(), e);
        }
        return null;
    }

    private MitsubishiAcStateResponse retryAfterAuthorizationFailure(
            DeviceAcDataEntity acData,
            boolean retryOnAuthorizationFailure,
            int statusCode
    ) {
        if (retryOnAuthorizationFailure) {
            log.info("Mitsubishi AC state query returned {}, attempting re-login", statusCode);
            AcLoginResponse acLoginResponse = mitsubishiLoginService.login(acData);
            if (acLoginResponse.isSuccess()) {
                acData.setAcAccessToken(acLoginResponse.getAccessToken());
                waitBeforeRetry();
                return getAcStateInternal(acData, false);
            }
        }
        log.error("Mitsubishi AC state query failed with {} even after re-login attempt", statusCode);
        return null;
    }

    private boolean shouldRefreshAccessToken(DeviceAcDataEntity acData) {
        String accessToken = acData.getAcAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            return true;
        }
        Instant expiresAt = acData.getAcTokenExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting before Mitsubishi AC state retry", e);
        }
    }

    private String formatStateJson(MitsubishiAcStateResponse state) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
    }

    private void updateDeviceConnectivity(DeviceAcDataEntity acData, MitsubishiAcStateResponse response) {
        DeviceEntity device = acData.getDevice();
        if (device == null || device.getId() == null) {
            return;
        }
        DeviceEntity managedDevice = deviceRepository.findById(device.getId()).orElse(null);
        if (managedDevice == null) {
            return;
        }
        Instant lastCommunication = parseLastCommunication(response.getLastCommunication());
        if (lastCommunication != null) {
            managedDevice.setLastCommunication(lastCommunication);
        }
        managedDevice.setApiOnline(isApiOnlineFromLastCommunication(lastCommunication));
        deviceRepository.save(managedDevice);
    }

    public static Instant parseLastCommunication(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse Mitsubishi LastCommunication={}", value, e);
            return null;
        }
    }

    public static boolean isApiOnlineFromLastCommunication(Instant lastCommunication) {
        return lastCommunication != null
                && Duration.between(lastCommunication, Instant.now()).compareTo(ONLINE_THRESHOLD) < 0;
    }

}
