package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.FactoryDeviceStatus;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import lombok.Data;

@Data
public class UpdateFactoryDeviceRequest {
    private String hardwareMac;
    private String chipId;
    private String firmwareVersion;
    private MqttDeviceProfile mqttDeviceProfile;
    private String claimCode;
    private String metadataJson;
    private FactoryDeviceStatus status;
}
