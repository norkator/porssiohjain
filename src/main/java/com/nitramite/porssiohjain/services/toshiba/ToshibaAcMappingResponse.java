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

import java.util.List;

@Data
public class ToshibaAcMappingResponse {

    @JsonProperty("ResObj")
    private List<Group> resObj;

    @JsonProperty("IsSuccess")
    private boolean isSuccess;

    @JsonProperty("Message")
    private String message;

    @JsonProperty("StatusCode")
    private String statusCode;

    @Data
    public static class Group {

        @JsonProperty("GroupId")
        private String groupId;

        @JsonProperty("GroupName")
        private String groupName;

        @JsonProperty("ConsumerId")
        private String consumerId;

        @JsonProperty("ACList")
        private List<AcDevice> acList;
    }

    @Data
    public static class AcDevice {

        @JsonProperty("Id")
        private String id;

        @JsonProperty("DeviceUniqueId")
        private String deviceUniqueId;

        @JsonProperty("Name")
        private String name;

        @JsonProperty("ACStateData")
        private String acStateData;
    }

}