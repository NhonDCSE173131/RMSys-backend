package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "machine_health_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineHealthSnapshotEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "machine_id", nullable = false) private UUID machineId;
    @Column(name = "bucket_start", nullable = false) private Instant bucketStart;
    @Column(name = "health_score") private Double healthScore;
    @Column(name = "risk_level", length = 20) private String riskLevel;
    @Column(name = "main_reason", length = 200) private String mainReason;
    @Column(name = "temperature_score") private Double temperatureScore;
    @Column(name = "vibration_score") private Double vibrationScore;
    @Column(name = "alarm_score") private Double alarmScore;
    @Column(name = "runtime_score") private Double runtimeScore;
}

