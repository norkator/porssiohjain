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
package com.nitramite.porssiohjain.services.mitsubishihome;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.services.models.AcLoginResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiMelCloudHomeLoginService {

    private static final Duration TOKEN_EXPIRY_BUFFER = Duration.ofMinutes(1);

    private final MitsubishiMelCloudHomeApiClient apiClient;
    private final DeviceAcDataRepository deviceAcDataRepository;

    @Transactional
    public AcLoginResponse login(DeviceAcDataEntity acData) {
        if (hasUsableAccessToken(acData)) {
            return success(acData.getAcAccessToken());
        }

        try {
            MitsubishiMelCloudHomeApiClient.TokenResponse tokens = refreshOrAuthenticate(acData);
            acData.setAcAccessToken(tokens.accessToken());
            if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
                acData.setAcRefreshToken(tokens.refreshToken());
            }
            acData.setAcTokenExpiresAt(Instant.now().plusSeconds(Math.max(tokens.expiresInSeconds(), 0L)));
            if (acData.getId() != null) {
                deviceAcDataRepository.save(acData);
            }
            return success(tokens.accessToken());
        } catch (MitsubishiMelCloudHomeException refreshFailure) {
            if (hasRefreshToken(acData)) {
                log.info("MELCloud Home token refresh failed; trying a full login");
                acData.setAcRefreshToken(null);
                try {
                    MitsubishiMelCloudHomeApiClient.TokenResponse tokens = apiClient.authenticate(
                            acData.getAcUsername(),
                            acData.getAcPassword()
                    );
                    acData.setAcAccessToken(tokens.accessToken());
                    acData.setAcRefreshToken(tokens.refreshToken());
                    acData.setAcTokenExpiresAt(Instant.now().plusSeconds(Math.max(tokens.expiresInSeconds(), 0L)));
                    if (acData.getId() != null) {
                        deviceAcDataRepository.save(acData);
                    }
                    return success(tokens.accessToken());
                } catch (MitsubishiMelCloudHomeException loginFailure) {
                    log.warn("MELCloud Home login failed: {}", loginFailure.getMessage());
                    return failure();
                }
            }
            log.warn("MELCloud Home login failed: {}", refreshFailure.getMessage());
            return failure();
        }
    }

    private MitsubishiMelCloudHomeApiClient.TokenResponse refreshOrAuthenticate(DeviceAcDataEntity acData) {
        if (hasRefreshToken(acData)) {
            return apiClient.refreshAccessToken(acData.getAcRefreshToken());
        }
        return apiClient.authenticate(acData.getAcUsername(), acData.getAcPassword());
    }

    private boolean hasUsableAccessToken(DeviceAcDataEntity acData) {
        return acData.getAcAccessToken() != null
                && !acData.getAcAccessToken().isBlank()
                && acData.getAcTokenExpiresAt() != null
                && acData.getAcTokenExpiresAt().isAfter(Instant.now().plus(TOKEN_EXPIRY_BUFFER));
    }

    private boolean hasRefreshToken(DeviceAcDataEntity acData) {
        return acData.getAcRefreshToken() != null && !acData.getAcRefreshToken().isBlank();
    }

    private AcLoginResponse success(String accessToken) {
        return AcLoginResponse.builder()
                .success(true)
                .accessToken(accessToken)
                .build();
    }

    private AcLoginResponse failure() {
        return AcLoginResponse.builder()
                .success(false)
                .accessToken(null)
                .build();
    }
}
