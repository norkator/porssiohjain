package com.nitramite.porssiohjain.services.toshiba;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ToshibaAcAmqpRegistrationResponse {

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
        @JsonProperty("DeviceId")
        private String deviceId;

        @JsonProperty("HostName")
        private String hostName;

        @JsonProperty("PrimaryKey")
        private String primaryKey;

        @JsonProperty("SecondaryKey")
        private String secondaryKey;

        @JsonProperty("SasToken")
        private String sasToken;

        @JsonProperty("RegisterDate")
        private String registerDate;
    }

}
