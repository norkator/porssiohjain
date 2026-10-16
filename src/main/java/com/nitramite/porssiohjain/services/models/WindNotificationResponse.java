package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.WindNotificationRuleType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Data @Builder
public class WindNotificationResponse {
    private Long id;
    private String name;
    private String description;
    private WindNotificationRuleType ruleType;
    private BigDecimal threshold;
    private String timezone;
    private LocalTime notificationTime;
    private boolean enabled;
    private Instant lastSentAt;
    private Instant createdAt;
    private Instant updatedAt;
}
