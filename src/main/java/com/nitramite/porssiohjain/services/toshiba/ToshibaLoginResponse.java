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