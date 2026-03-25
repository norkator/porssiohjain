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

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToshibaLoginService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String LOGIN_URL = "https://mobileapi.toshibahomeaccontrols.com/api/Consumer/Login";
    private final DeviceAcDataRepository deviceAcDataRepository;

    @Data
    @Builder
    private static class LoginRequest {
        private String Username;
        private String Password;
    }

    public boolean login(
            DeviceAcDataEntity acData
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            LoginRequest loginRequest = LoginRequest.builder()
                    .Username(acData.getAcUsername())
                    .Password(acData.getAcPassword())
                    .build();

            HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);

            ResponseEntity<ToshibaLoginResponse> responseEntity = restTemplate.postForEntity(
                    LOGIN_URL,
                    request,
                    ToshibaLoginResponse.class
            );

            ToshibaLoginResponse response = responseEntity.getBody();
            if (response != null && response.isSuccess() && response.getResObj() != null) {
                ToshibaLoginResponse.ResObj resObj = response.getResObj();
                acData.setAcAccessToken(resObj.getAccess_token());
                acData.setAcConsumerId(resObj.getConsumerId());
                // Returns weird expiration values
                acData.setAcTokenExpiresAt(Instant.now().plusSeconds(resObj.getExpires_in()));
                deviceAcDataRepository.save(acData);
                return true;
            } else {
                log.error("Toshiba login failed: {}", response != null ? response.getMessage() : "Empty response");
            }
        } catch (Exception e) {
            log.error("Error during Toshiba login", e);
        }
        return false;
    }

}
