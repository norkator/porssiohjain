package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import lombok.Data;

@Data
public class CreateOtaReleaseRequest {
    private DevicePlatform platform;
    private String productModel;
    private String version;
    private String binaryUrl;
    private String checksumSha256;
    private Boolean active;
    private String notes;
}
