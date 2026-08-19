package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.WindNotificationRuleType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wind_notification")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WindNotificationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 40)
    private WindNotificationRuleType ruleType;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal threshold;
    @Column(nullable = false) private String timezone;
    @Builder.Default @Column(nullable = false) private boolean enabled = true;
    @Column(name = "last_sent_at") private Instant lastSentAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void create() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}
