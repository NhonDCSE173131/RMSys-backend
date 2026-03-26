package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "tool_usage_telemetry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ToolUsageTelemetryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "tool_code", length = 50) private String toolCode;
    @Column(name = "tool_number") private Integer toolNumber;
    @Column(name = "usage_minutes") private Double usageMinutes;
    @Column(name = "usage_cycles") private Integer usageCycles;
    @Column(name = "spindle_load_pct") private Double spindleLoadPct;
    @Column(name = "tool_temperature_c") private Double toolTemperatureC;
    @Column(name = "remaining_life_pct") private Double remainingLifePct;
    @Column(name = "wear_level", length = 20) private String wearLevel;
}

