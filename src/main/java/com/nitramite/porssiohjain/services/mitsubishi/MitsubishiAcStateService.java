/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiAcStateService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GET_DEVICE_URL = "https://app.melcloud.com/Mitsubishi.Wifi.Client/Device/Get";

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

            return responseEntity.getBody();
        } catch (Exception e) {
            log.error("Error fetching Mitsubishi AC state for device id: {}", acData.getAcDeviceId(), e);
        }
        return null;
    }

}
