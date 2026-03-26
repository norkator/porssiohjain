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
    private static final String REGISTER_CLIENT_URL = "https://mobileapi.toshibahomeaccontrols.com/api/Consumer/RegisterClient";
    private final DeviceAcDataRepository deviceAcDataRepository;

    @Data
    @Builder
    private static class RegisterClientRequest {
        private String DeviceId;
    }

    public boolean registerClient(DeviceAcDataEntity acData) {
        try {
            String deviceId = acData.getAcUsername() + "_xxxxxxxxxxxxx";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(acData.getAcAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            RegisterClientRequest registerRequest = RegisterClientRequest.builder()
                    .DeviceId(deviceId)
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
                return true;
            } else {
                log.error("Toshiba client registration failed: {}", response != null ? response.getMessage() : "Empty response");
            }
        } catch (Exception e) {
            log.error("Error during Toshiba client registration", e);
        }
        return false;
    }

}
