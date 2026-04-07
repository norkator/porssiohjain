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
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.services.models.AcLoginResponse;
import jakarta.transaction.Transactional;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiLoginService {

    private static final String LOGIN_URL = "https://app.melcloud.com/Mitsubishi.Wifi.Client/Login/ClientLogin";

    private final RestTemplate restTemplate = new RestTemplate();
    private final DeviceAcDataRepository deviceAcDataRepository;

    @Data
    @Builder
    private static class LoginRequest {
        private String Email;
        private String Password;
        private Integer Language;
        private String AppVersion;
        private Boolean Persist;
        private String CaptchaResponse;
    }

    @Transactional
    public AcLoginResponse login(
            DeviceAcDataEntity acData
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(MediaType.parseMediaTypes("application/json, text/javascript, */*; q=0.01"));
            headers.set("X-Requested-With", "XMLHttpRequest");
            headers.set("Cookie", "policyaccepted=true");
            headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:73.0) Gecko/20100101 Firefox/73.0");

            LoginRequest loginRequest = LoginRequest.builder()
                    .Email(acData.getAcUsername())
                    .Password(acData.getAcPassword())
                    .Language(0)
                    .AppVersion("1.38.0.1")
                    .Persist(true)
                    .CaptchaResponse(null)
                    .build();

            HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);

            ResponseEntity<MitsubishiLoginResponse> responseEntity = restTemplate.postForEntity(
                    LOGIN_URL,
                    request,
                    MitsubishiLoginResponse.class
            );

            MitsubishiLoginResponse response = responseEntity.getBody();
            MitsubishiLoginResponse.LoginData loginData = response != null ? response.getLoginData() : null;
            String contextKey = loginData != null ? loginData.getContextKey() : null;

            if (contextKey != null && !contextKey.isBlank()) {
                acData.setAcAccessToken(contextKey);

                Instant expiresAt = parseExpiry(loginData.getExpiry());
                if (expiresAt != null) {
                    acData.setAcTokenExpiresAt(expiresAt);
                }

                if (acData.getId() != null) {
                    deviceAcDataRepository.save(acData);
                }

                return AcLoginResponse.builder()
                        .success(true)
                        .accessToken(contextKey)
                        .build();
            } else {
                log.error(
                        "Mitsubishi login failed: errorId={}, errorMessage={}, loginStatus={}",
                        response != null ? response.getErrorId() : null,
                        response != null ? response.getErrorMessage() : "Empty response",
                        response != null ? response.getLoginStatus() : null
                );
            }
        } catch (Exception e) {
            log.error("Error during Mitsubishi login", e);
        }

        return AcLoginResponse.builder()
                .success(false)
                .accessToken(null)
                .build();
    }

    private Instant parseExpiry(String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(expiry).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            log.warn("Unable to parse Mitsubishi login expiry '{}'", expiry);
            return null;
        }
    }

}
