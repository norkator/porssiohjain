package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.WindNotificationRuleType;
import lombok.Data;
import java.time.LocalTime;

@Data
public class WindNotificationRequest {
    private String name;
    private String description;
    private WindNotificationRuleType ruleType;
    private Double threshold;
    private String timezone;
    private LocalTime notificationTime;
    private Boolean enabled;
}
