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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToshibaLoginService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String LOGIN_URL = "https://mobileapi.toshibahomeaccontrols.com/api/Consumer/Login";
    private static final Duration TOKEN_REUSE_SKEW = Duration.ofMinutes(5);
    private static final Duration DEFAULT_RATE_LIMIT_COOLDOWN = Duration.ofSeconds(65);
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final Map<String, LoginState> loginStates = new ConcurrentHashMap<>();

    @Data
    @Builder
    private static class LoginRequest {
        private String Username;
        private String Password;
    }

    @Transactional
    public AcLoginResponse login(
            DeviceAcDataEntity acData
    ) {
        return loginInternal(acData, false);
    }

    @Transactional
    public AcLoginResponse refreshLogin(
            DeviceAcDataEntity acData
    ) {
        return loginInternal(acData, true);
    }

    private AcLoginResponse loginInternal(
            DeviceAcDataEntity acData,
            boolean forceRefresh
    ) {
        String loginKey = loginKey(acData);
        LoginState loginState = loginStates.computeIfAbsent(loginKey, ignored -> new LoginState());
        synchronized (loginState) {
            if (!forceRefresh && hasReusableToken(acData)) {
                return success(acData.getAcAccessToken());
            }
            if (hasReusableCachedToken(acData, loginState, forceRefresh)) {
                acData.setAcAccessToken(loginState.accessToken);
                acData.setAcConsumerId(loginState.consumerId);
                acData.setAcTokenExpiresAt(loginState.expiresAt);
                return success(loginState.accessToken);
            }
            if (loginState.rateLimitedUntil != null && Instant.now().isBefore(loginState.rateLimitedUntil)) {
                log.warn("Skipping Toshiba login because previous login was rate-limited until {}", loginState.rateLimitedUntil);
                return failed();
            }
            return performLogin(acData, loginState);
        }
    }

    private AcLoginResponse performLogin(
            DeviceAcDataEntity acData,
            LoginState loginState
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
                loginState.accessToken = resObj.getAccess_token();
                loginState.consumerId = resObj.getConsumerId();
                loginState.expiresAt = acData.getAcTokenExpiresAt();
                loginState.rateLimitedUntil = null;
                if (acData.getId() != null) {
                    deviceAcDataRepository.save(acData);
                }

                return success(resObj.getAccess_token());
            } else {
                log.error("Toshiba login failed: {}", response != null ? response.getMessage() : "Empty response");
            }
        } catch (HttpClientErrorException.TooManyRequests e) {
            loginState.rateLimitedUntil = Instant.now().plus(DEFAULT_RATE_LIMIT_COOLDOWN);
            log.warn("Toshiba login rate-limited, suppressing new login attempts until {}", loginState.rateLimitedUntil);
        } catch (Exception e) {
            log.error("Error during Toshiba login", e);
        }
        return failed();
    }

    private boolean hasReusableToken(DeviceAcDataEntity acData) {
        return acData.getAcAccessToken() != null
                && !acData.getAcAccessToken().isBlank()
                && isUsableExpiry(acData.getAcTokenExpiresAt());
    }

    private boolean hasReusableCachedToken(
            DeviceAcDataEntity acData,
            LoginState loginState,
            boolean forceRefresh
    ) {
        if (loginState.accessToken == null || loginState.accessToken.isBlank() || !isUsableExpiry(loginState.expiresAt)) {
            return false;
        }
        return !forceRefresh || !loginState.accessToken.equals(acData.getAcAccessToken());
    }

    private boolean isUsableExpiry(Instant expiresAt) {
        return expiresAt != null && expiresAt.isAfter(Instant.now().plus(TOKEN_REUSE_SKEW));
    }

    private String loginKey(DeviceAcDataEntity acData) {
        if (acData.getAcUsername() == null || acData.getAcUsername().isBlank()) {
            return String.valueOf(acData.getId());
        }
        return acData.getAcUsername().trim().toLowerCase();
    }

    private AcLoginResponse success(String accessToken) {
        return AcLoginResponse.builder()
                .success(true)
                .accessToken(accessToken)
                .build();
    }

    private AcLoginResponse failed() {
        return AcLoginResponse.builder()
                .success(false)
                .accessToken(null)
                .build();
    }

    private static class LoginState {
        private String accessToken;
        private String consumerId;
        private Instant expiresAt;
        private Instant rateLimitedUntil;
    }

}
