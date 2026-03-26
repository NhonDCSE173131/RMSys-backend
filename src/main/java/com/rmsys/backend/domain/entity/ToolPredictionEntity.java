package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "tool_predictions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ToolPredictionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "machine_id", nullable = false) private UUID machineId;
    @Column(name = "tool_code", length = 50) private String toolCode;
    @Column(nullable = false) private Instant ts;
    @Column(name = "remaining_minutes") private Double remainingMinutes;
    @Column(name = "remaining_cycles") private Integer remainingCycles;
    @Column(name = "risk_level", length = 20) private String riskLevel;
    @Column(name = "confidence_score") private Double confidenceScore;
    @Lob @Column(name = "recommended_action") private String recommendedAction;
}

