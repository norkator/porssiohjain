package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.FactoryTestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "factory_test_step_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactoryTestStepResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factory_test_run_id", nullable = false)
    private FactoryTestRunEntity factoryTestRun;

    @Column(name = "step_key", nullable = false, length = 128)
    private String stepKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FactoryTestStatus status;

    @Column(name = "expected_value", columnDefinition = "TEXT")
    private String expectedValue;

    @Column(name = "actual_value", columnDefinition = "TEXT")
    private String actualValue;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
