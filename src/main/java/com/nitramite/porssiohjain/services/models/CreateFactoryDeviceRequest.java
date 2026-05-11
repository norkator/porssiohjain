package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import lombok.Data;

@Data
public class CreateFactoryDeviceRequest {
    private String serialNumber;
    private String hardwareMac;
    private String chipId;
    private DevicePlatform platform;
    private String productModel;
    private String firmwareVersion;
    private String mqttTopicRoot;
    private String mqttUsername;
    private String mqttPassword;
    private MqttDeviceProfile mqttDeviceProfile;
    private String claimCode;
    private String metadataJson;
}
