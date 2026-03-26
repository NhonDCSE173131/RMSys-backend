package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "machine_telemetry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineTelemetryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "connection_status", length = 20)
    private String connectionStatus;

    @Column(name = "machine_state", length = 30)
    private String machineState;

    @Column(name = "operation_mode", length = 30)
    private String operationMode;

    @Column(name = "alarm_active")
    private Boolean alarmActive;

    @Column(name = "program_name", length = 200)
    private String programName;

    @Column(name = "cycle_running")
    private Boolean cycleRunning;

    @Column(name = "current_job", length = 200)
    private String currentJob;

    @Column(name = "power_kw")
    private Double powerKw;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "vibration_mm_s")
    private Double vibrationMmS;

    @Column(name = "runtime_hours")
    private Double runtimeHours;

    @Column(name = "cycle_time_sec")
    private Double cycleTimeSec;

    @Column(name = "output_count")
    private Integer outputCount;

    @Column(name = "good_count")
    private Integer goodCount;

    @Column(name = "reject_count")
    private Integer rejectCount;

    @Column(name = "spindle_speed_rpm")
    private Double spindleSpeedRpm;

    @Column(name = "feed_rate_mm_min")
    private Double feedRateMmMin;

    @Column(name = "axis_load_pct")
    private Double axisLoadPct;

    @Column(name = "metadata_json", length = 4000)
    private String metadataJson;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "is_late_arrival")
    private Boolean isLateArrival;

    @Column(name = "source_sequence")
    private Long sourceSequence;
}
