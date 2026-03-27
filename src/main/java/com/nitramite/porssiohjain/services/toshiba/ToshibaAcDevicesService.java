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

package com.nitramite.porssiohjain.services.toshiba;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ToshibaAcDevicesService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String MAPPING_URL = "https://mobileapi.toshibahomeaccontrols.com/api/AC/GetConsumerACMapping";

    public List<ToshibaAcMappingResponse.AcDevice> getAcDevices(
            DeviceAcDataEntity acData
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(acData.getAcAccessToken());
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            String urlWithParams = UriComponentsBuilder.fromUriString(MAPPING_URL)
                    .queryParam("consumerId", acData.getAcConsumerId())
                    .build(true)
                    .toUriString();

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<ToshibaAcMappingResponse> responseEntity = restTemplate.exchange(
                    urlWithParams,
                    HttpMethod.GET,
                    request,
                    ToshibaAcMappingResponse.class
            );

            ToshibaAcMappingResponse response = responseEntity.getBody();
            if (response != null && response.isSuccess() && response.getResObj() != null) {
                List<ToshibaAcMappingResponse.AcDevice> allDevices = new ArrayList<>();
                for (ToshibaAcMappingResponse.Group group : response.getResObj()) {
                    if (group.getAcList() != null) {
                        allDevices.addAll(group.getAcList());
                    }
                }
                return allDevices;
            } else {
                log.error("Failed to fetch Toshiba AC mapping: {}", response != null ? response.getMessage() : "Empty response");
            }
        } catch (Exception e) {
            log.error("Error during Toshiba AC mapping fetch", e);
        }
        return List.of();
    }

}
