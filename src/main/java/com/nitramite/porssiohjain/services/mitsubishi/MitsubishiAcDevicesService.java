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

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiAcDevicesService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String LIST_DEVICES_URL = "https://app.melcloud.com/Mitsubishi.Wifi.Client/User/ListDevices";

    public List<MitsubishiAcDevicesResponse.Device> getAcDevices(
            DeviceAcDataEntity acData
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MitsContextKey", acData.getAcAccessToken());
            headers.set("Accept", "application/json, text/javascript, */*; q=0.01");
            headers.set("X-Requested-With", "XMLHttpRequest");
            headers.set("Cookie", "policyaccepted=true");
            headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:73.0) Gecko/20100101 Firefox/73.0");

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<MitsubishiAcDevicesResponse.Building[]> responseEntity = restTemplate.exchange(
                    LIST_DEVICES_URL,
                    HttpMethod.GET,
                    request,
                    MitsubishiAcDevicesResponse.Building[].class
            );

            MitsubishiAcDevicesResponse.Building[] buildings = responseEntity.getBody();
            if (buildings != null) {
                List<MitsubishiAcDevicesResponse.Device> allDevices = new ArrayList<>();
                for (MitsubishiAcDevicesResponse.Building building : buildings) {
                    if (building.getStructure() != null && building.getStructure().getDevices() != null) {
                        allDevices.addAll(building.getStructure().getDevices());
                    }
                }
                return allDevices;
            } else {
                log.error("Failed to fetch Mitsubishi AC mapping: Empty response");
            }
        } catch (Exception e) {
            log.error("Error during Mitsubishi AC mapping fetch", e);
        }
        return List.of();
    }

}
