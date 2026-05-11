package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.enums.MqttCapability;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MqttProfileService {

    public List<MqttCapability> getCapabilities(MqttDeviceProfile profile) {
        if (profile == null) {
            return List.of();
        }
        return switch (profile) {
            case GENERIC_RELAY -> List.of(MqttCapability.RELAY_SWITCH, MqttCapability.TELEMETRY, MqttCapability.OTA_HTTP);
            case OPENBEKEN_RELAY -> List.of(MqttCapability.RELAY_SWITCH, MqttCapability.TELEMETRY, MqttCapability.OTA_HTTP);
            case TASMOTA_RELAY -> List.of(MqttCapability.RELAY_SWITCH, MqttCapability.TELEMETRY, MqttCapability.OTA_HTTP);
            case ESPHOME_RELAY -> List.of(MqttCapability.RELAY_SWITCH, MqttCapability.TELEMETRY, MqttCapability.OTA_HTTP);
            case GENERIC_THERMOSTAT -> List.of(MqttCapability.THERMOSTAT_SETPOINT, MqttCapability.TELEMETRY, MqttCapability.OTA_HTTP);
        };
    }

    public String buildDefaultOtaPayload(MqttDeviceProfile profile, String binaryUrl, String version, String checksumSha256) {
        String checksum = checksumSha256 == null ? "" : checksumSha256;
        return switch (profile) {
            case OPENBEKEN_RELAY -> """
                    {"command":"ota_http","url":"%s","version":"%s","checksumSha256":"%s"}
                    """.formatted(binaryUrl, version, checksum);
            case TASMOTA_RELAY -> """
                    {"command":"upgrade","url":"%s","version":"%s","checksumSha256":"%s"}
                    """.formatted(binaryUrl, version, checksum);
            case ESPHOME_RELAY -> """
                    {"command":"ota_update","url":"%s","version":"%s","checksumSha256":"%s"}
                    """.formatted(binaryUrl, version, checksum);
            case GENERIC_RELAY, GENERIC_THERMOSTAT -> """
                    {"command":"ota_install","url":"%s","version":"%s","checksumSha256":"%s"}
                    """.formatted(binaryUrl, version, checksum);
        };
    }
}
