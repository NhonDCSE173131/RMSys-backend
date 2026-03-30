package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "machines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 50)
    private String vendor;

    @Column(length = 200)
    private String model;

    @Column(name = "line_id", length = 50)
    private String lineId;

    @Column(name = "plant_id", length = 50)
    private String plantId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "connection_state", nullable = false, length = 20)
    @Builder.Default
    private String connectionState = "OFFLINE";

    @Column(name = "connection_unstable", nullable = false)
    @Builder.Default
    private Boolean connectionUnstable = false;

    @Column(name = "connection_reason", length = 80)
    private String connectionReason;

    @Column(name = "connection_scope", length = 30)
    private String connectionScope;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_telemetry_source_ts")
    private Instant lastTelemetrySourceTs;

    @Column(name = "last_telemetry_received_at")
    private Instant lastTelemetryReceivedAt;

    @Column(name = "latest_accepted_source_ts")
    private Instant latestAcceptedSourceTs;

    @Column(name = "last_payload_fingerprint", length = 500)
    private String lastPayloadFingerprint;

    @Column(name = "last_connection_changed_at")
    private Instant lastConnectionChangedAt;

    @Column(name = "connection_flap_count", nullable = false)
    @Builder.Default
    private Integer connectionFlapCount = 0;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

