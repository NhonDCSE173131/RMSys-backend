package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "tool_catalogs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ToolCatalogEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "tool_code", nullable = false, length = 50)
    private String toolCode;

    @Column(name = "tool_name", nullable = false, length = 200)
    private String toolName;

    @Column(name = "tool_type", length = 50)
    private String toolType;

    @Column(name = "life_limit_minutes")
    private Integer lifeLimitMinutes;

    @Column(name = "life_limit_cycles")
    private Integer lifeLimitCycles;

    @Column(name = "warning_threshold_pct")
    private Double warningThresholdPct;

    @Column(name = "critical_threshold_pct")
    private Double criticalThresholdPct;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private Instant updatedAt;
}

