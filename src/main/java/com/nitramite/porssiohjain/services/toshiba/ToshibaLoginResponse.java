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

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ToshibaLoginResponse {

    @JsonProperty("ResObj")
    private ResObj resObj;

    @JsonProperty("IsSuccess")
    private boolean isSuccess;

    @JsonProperty("Message")
    private String message;

    @JsonProperty("StatusCode")
    private String statusCode;

    @Data
    public static class ResObj {
        private String access_token;
        private String token_type;
        private long expires_in;
        private String consumerId;
        private String consumerMasterId;
        private int countryId;
        private boolean isHeatQuantityActivated;
    }

}