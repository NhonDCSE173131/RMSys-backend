package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "downtime_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DowntimeEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "reason_code", length = 50) private String reasonCode;
    @Column(name = "reason_group", length = 50) private String reasonGroup;

    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "duration_sec") private Integer durationSec;
    @Column(name = "planned_stop") @Builder.Default private Boolean plannedStop = false;
    @Column(name = "abnormal_stop") @Builder.Default private Boolean abnormalStop = false;
    @Lob @Column private String notes;
}

