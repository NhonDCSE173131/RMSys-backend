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

    // ---- PLC connection config (V2) ----

    @Column(name = "protocol", length = 50)
    private String protocol;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "unit_id")
    @Builder.Default
    private Integer unitId = 1;

    @Column(name = "poll_interval_ms")
    @Builder.Default
    private Integer pollIntervalMs = 1000;

    @Column(name = "connection_mode", length = 20)
    @Builder.Default
    private String connectionMode = "MANUAL";

    @Column(name = "auto_connect")
    @Builder.Default
    private Boolean autoConnect = false;

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "mapping_file_id")
    private UUID mappingFileId;

    @Column(name = "last_connection_status", length = 30)
    private String lastConnectionStatus;

    @Column(name = "last_connection_reason", length = 500)
    private String lastConnectionReasonDetail;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    @Column(name = "last_disconnected_at")
    private Instant lastDisconnectedAt;

    @Column(name = "last_data_at")
    private Instant lastDataAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
