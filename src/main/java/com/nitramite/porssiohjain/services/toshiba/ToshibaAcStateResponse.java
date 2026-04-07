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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class ToshibaAcStateResponse {

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

        @JsonProperty("Id")
        private String id;

        @JsonProperty("ACId")
        private String acId;

        @JsonProperty("ACDeviceUniqueId")
        private String acDeviceUniqueId;

        @JsonProperty("ACStateData")
        private String acStateData;

        @JsonProperty("OnOff")
        private String onOff;

        @JsonProperty("FirmwareVersion")
        private String firmwareVersion;

        @JsonProperty("FirmwareUpgradeStatus")
        private String firmwareUpgradeStatus;

        @JsonProperty("UpdatedDate")
        private Instant updatedDate;

        private ToshibaAcStateDecodedResponse decodedAcState;
    }

}
