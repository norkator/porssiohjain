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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiAcStateService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GET_DEVICE_URL = "https://app.melcloud.com/Mitsubishi.Wifi.Client/Device/Get";
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MitsubishiAcStateResponse getAcState(DeviceAcDataEntity acData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MitsContextKey", acData.getAcAccessToken());
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

            ResponseEntity<MitsubishiAcStateResponse> responseEntity = restTemplate.exchange(
                    urlWithParams,
                    HttpMethod.GET,
                    request,
                    MitsubishiAcStateResponse.class
            );

            MitsubishiAcStateResponse body = responseEntity.getBody();
            if (body != null) {
                if (body.getDeviceId() != null) {
                    acData.setAcDeviceId(String.valueOf(body.getDeviceId()));
                }
                acData.setLastPolledStateHex(formatStateJson(body));
                deviceAcDataRepository.save(acData);
                markDeviceReachable(acData);
            }
            return body;
        } catch (Exception e) {
            log.error("Error fetching Mitsubishi AC state for device id: {}", acData.getAcDeviceId(), e);
        }
        return null;
    }

    private String formatStateJson(MitsubishiAcStateResponse state) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
    }

    private void markDeviceReachable(DeviceAcDataEntity acData) {
        DeviceEntity device = acData.getDevice();
        if (device == null || device.getId() == null) {
            return;
        }
        DeviceEntity managedDevice = deviceRepository.findById(device.getId()).orElse(null);
        if (managedDevice == null) {
            return;
        }
        managedDevice.setLastCommunication(Instant.now());
        managedDevice.setApiOnline(true);
        deviceRepository.save(managedDevice);
    }

}
