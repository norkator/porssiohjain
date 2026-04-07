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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
public class MitsubishiSetAcStateService {

    private static final String SET_ATA_URL = "https://app.melcloud.com/Mitsubishi.Wifi.Client/Device/SetAta";

    private final RestTemplate restTemplate = new RestTemplate();

    public MitsubishiSetAcStateResponse setAcState(DeviceAcDataEntity acData, MitsubishiAcStateResponse state) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-MitsContextKey", acData.getAcAccessToken());
            headers.setAccept(MediaType.parseMediaTypes("application/json, text/javascript, */*; q=0.01"));
            headers.set("X-Requested-With", "XMLHttpRequest");
            headers.set("Cookie", "policyaccepted=true");
            headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:73.0) Gecko/20100101 Firefox/73.0");

            SetAtaRequest payload = SetAtaRequest.builder()
                    .deviceID(state.getDeviceId())
                    .buildingID(parseBuildingId(acData.getBuildingId()))
                    .power(state.getPower())
                    .operationMode(state.getOperationMode())
                    .setTemperature(state.getSetTemperature())
                    .setFanSpeed(state.getSetFanSpeed())
                    .vaneVertical(state.getVaneVertical())
                    .vaneHorizontal(state.getVaneHorizontal())
                    .effectiveFlags(state.getEffectiveFlags())
                    .hasPendingCommand(Boolean.TRUE.equals(state.getHasPendingCommand()))
                    .build();

            HttpEntity<SetAtaRequest> request = new HttpEntity<>(payload, headers);
            ResponseEntity<MitsubishiSetAcStateResponse> responseEntity = restTemplate.postForEntity(
                    SET_ATA_URL,
                    request,
                    MitsubishiSetAcStateResponse.class
            );

            return responseEntity.getBody();
        } catch (Exception e) {
            log.error("Error setting Mitsubishi AC state for device id: {}", acData.getAcDeviceId(), e);
            return null;
        }
    }

    private Integer parseBuildingId(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            throw new IllegalArgumentException("Mitsubishi buildingId is required");
        }
        return Integer.valueOf(buildingId);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SetAtaRequest {
        @JsonProperty("DeviceID")
        private Long deviceID;

        @JsonProperty("BuildingID")
        private Integer buildingID;

        @JsonProperty("Power")
        private Boolean power;

        @JsonProperty("OperationMode")
        private Integer operationMode;

        @JsonProperty("SetTemperature")
        private Double setTemperature;

        @JsonProperty("SetFanSpeed")
        private Integer setFanSpeed;

        @JsonProperty("VaneVertical")
        private Integer vaneVertical;

        @JsonProperty("VaneHorizontal")
        private Integer vaneHorizontal;

        @JsonProperty("EffectiveFlags")
        private Long effectiveFlags;

        @JsonProperty("HasPendingCommand")
        private Boolean hasPendingCommand;
    }
}
