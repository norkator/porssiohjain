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
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToshibaRegisterControllerService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String REGISTER_CLIENT_URL = "https://mobileapi.toshibahomeaccontrols.com/api/Consumer/RegisterMobileDevice";
    private final DeviceAcDataRepository deviceAcDataRepository;

    @Data
    @Builder
    private static class RegisterClientRequest {
        private String DeviceId;
        @Builder.Default
        private String DeviceType = "1";
        private String Username;
    }

    public String registerClient(DeviceAcDataEntity acData) {
        try {
            if (acData.getAcUsername() == null || acData.getAcUsername().isBlank()) {
                throw new IllegalArgumentException("AC username missing for Toshiba client registration");
            }
            if (acData.getAcClientDeviceSuffix() == null || acData.getAcClientDeviceSuffix().isBlank()) {
                throw new IllegalArgumentException("AC client device suffix missing for Toshiba client registration");
            }

            String deviceId = acData.getAcUsername() + "_" + acData.getAcClientDeviceSuffix();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(acData.getAcAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            RegisterClientRequest registerRequest = RegisterClientRequest.builder()
                    .DeviceId(deviceId)
                    .DeviceType("1")
                    .Username(acData.getAcUsername())
                    .build();

            HttpEntity<RegisterClientRequest> request = new HttpEntity<>(registerRequest, headers);

            log.info("Registering Toshiba client for device ID: {}", deviceId);
            ResponseEntity<ToshibaAcAmqpRegistrationResponse> responseEntity = restTemplate.postForEntity(
                    REGISTER_CLIENT_URL,
                    request,
                    ToshibaAcAmqpRegistrationResponse.class
            );

            ToshibaAcAmqpRegistrationResponse response = responseEntity.getBody();
            if (response != null && response.isSuccess() && response.getResObj() != null) {
                acData.setSasToken(response.getResObj().getSasToken());
                deviceAcDataRepository.save(acData);
                log.info("Toshiba client registration successful, SAS token obtained");
                return response.getResObj().getSasToken();
            } else {
                log.error("Toshiba client registration failed: {}", response != null ? response.getMessage() : "Empty response");
            }
        } catch (Exception e) {
            log.error("Error during Toshiba client registration", e);
        }
        return null;
    }

}
