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
import com.nitramite.porssiohjain.services.models.AcLoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToshibaAcStateService {

    private static final long RETRY_DELAY_MS = 1000L;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ToshibaLoginService toshibaLoginService;
    private final ToshibaAcStateHexDecoderService toshibaAcStateHexDecoderService;
    private static final String STATE_URL = "https://mobileapi.toshibahomeaccontrols.com/api/AC/GetCurrentACState?ACId=";

    public ToshibaAcStateResponse getAcState(DeviceAcDataEntity acData) {
        return getAcStateInternal(acData, true);
    }

    private ToshibaAcStateResponse getAcStateInternal(
            DeviceAcDataEntity acData,
            boolean retryOn403
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(acData.getAcAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<ToshibaAcStateResponse> response = restTemplate.exchange(
                    STATE_URL + acData.getAcDeviceId(),
                    HttpMethod.GET,
                    request,
                    ToshibaAcStateResponse.class
            );

            ToshibaAcStateResponse body = response.getBody();
            if (body != null && body.getResObj() != null) {
                body.getResObj().setDecodedAcState(
                        toshibaAcStateHexDecoderService.decode(body.getResObj().getAcStateData())
                );
            }
            return body;
        } catch (HttpClientErrorException.Forbidden e) {
            if (retryOn403) {
                log.info("Toshiba AC state query returned 403, attempting re-login");
                AcLoginResponse acLoginResponse = toshibaLoginService.login(acData);
                if (acLoginResponse.isSuccess()) {
                    acData.setAcAccessToken(acLoginResponse.getAccessToken());
                    waitBeforeRetry();
                    return getAcStateInternal(acData, false);
                }
            }
            log.error("Toshiba AC state query failed with 403 even after re-login attempt");
            throw e;
        } catch (Exception e) {
            log.error("Error fetching Toshiba AC state", e);
            throw e;
        }
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting before Toshiba AC state retry", e);
        }
    }

}
