package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "oee_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OeeSnapshotEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "machine_id", nullable = false) private UUID machineId;
    @Column(name = "bucket_start", nullable = false) private Instant bucketStart;
    @Column(name = "bucket_type", nullable = false, length = 20) private String bucketType;

    private Double availability;
    private Double performance;
    private Double quality;
    private Double oee;

    @Column(name = "runtime_sec") private Integer runtimeSec;
    @Column(name = "stop_sec") private Integer stopSec;
    @Column(name = "good_count") private Integer goodCount;
    @Column(name = "reject_count") private Integer rejectCount;
    @Column(name = "actual_cycle_time_sec") private Double actualCycleTimeSec;
    @Column(name = "ideal_cycle_time_sec") private Double idealCycleTimeSec;
}

