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
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MitsubishiSetAcStateResponse {

    @JsonProperty("ErrorMessage")
    private String errorMessage;

    @JsonProperty("ErrorCode")
    private Integer errorCode;

    @JsonProperty("HasPendingCommand")
    private Boolean hasPendingCommand;

    public boolean isSuccess() {
        return errorCode == null || errorCode == 0;
    }

}
