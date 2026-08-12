package com.nitramite.porssiohjain.services.models;

import lombok.Data;
import java.time.Instant;
import java.util.*;

@Data
public class ZigbeeGatewayBackup {
    private int backupVersion = 1;
    private long revision;
    private UUID gatewayId;
    private String coordinatorIeee;
    private int panId;
    private String extendedPanId;
    private int channel;
    private List<Map<String, Object>> devices;
    private Instant updatedAt;
}
