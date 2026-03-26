package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "maintenance_predictions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaintenancePredictionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "machine_id", nullable = false) private UUID machineId;
    @Column(nullable = false) private Instant ts;
    @Column(name = "remaining_hours_to_service") private Double remainingHoursToService;
    @Column(name = "predicted_failure_risk") private Double predictedFailureRisk;
    @Column(name = "risk_level", length = 20) private String riskLevel;
    @Lob @Column(name = "recommended_action") private String recommendedAction;
    @Column(name = "next_maintenance_date") private Instant nextMaintenanceDate;
}

