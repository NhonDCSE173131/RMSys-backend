package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "alarm_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlarmEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "alarm_code", length = 50) private String alarmCode;
    @Column(name = "alarm_type", length = 50) private String alarmType;
    @Column(nullable = false, length = 20) private String severity;
    @Lob @Column private String message;

    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;

    @Column(name = "is_active", nullable = false) @Builder.Default private Boolean isActive = true;
    @Column(nullable = false) @Builder.Default private Boolean acknowledged = false;
    @Column(name = "acknowledged_by", length = 100) private String acknowledgedBy;
    @Column(name = "acknowledged_at") private Instant acknowledgedAt;
    @Column(name = "raw_payload_json", length = 4000)
    private String rawPayloadJson;
}
