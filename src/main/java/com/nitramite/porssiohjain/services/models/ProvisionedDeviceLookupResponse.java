package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.MqttCapability;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvisionedDeviceLookupResponse {
    private Long factoryDeviceId;
    private String claimCode;
    private String serialNumber;
    private String productModel;
    private DevicePlatform platform;
    private MqttDeviceProfile mqttDeviceProfile;
    private List<MqttCapability> mqttCapabilities;
    private String firmwareVersion;
    private Instant lastSeenAt;
    private boolean claimable;
}
