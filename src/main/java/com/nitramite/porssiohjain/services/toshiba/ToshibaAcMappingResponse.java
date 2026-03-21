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
    }

}