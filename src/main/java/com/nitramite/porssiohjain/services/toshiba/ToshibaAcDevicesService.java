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
import java.util.regex.Pattern;

@Slf4j
@Service
public class ToshibaAcDevicesService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String MAPPING_URL = "https://mobileapi.toshibahomeaccontrols.com/api/AC/GetConsumerACMapping";
    private static final Pattern CONSUMER_ID_PATTERN = Pattern.compile("^[a-fA-F0-9\\-]{1,64}$");

    public List<ToshibaAcMappingResponse.AcDevice> getAcDevices(
            DeviceAcDataEntity acData
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(acData.getAcAccessToken());
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            String consumerId = acData.getAcConsumerId();
            if (consumerId == null || !CONSUMER_ID_PATTERN.matcher(consumerId).matches()) {
                log.error("Invalid Toshiba consumerId format");
                return List.of();
            }

            String urlWithParams = UriComponentsBuilder.fromUriString(MAPPING_URL)
                    .queryParam("consumerId", consumerId)
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
