package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "maintenance_telemetry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaintenanceTelemetryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "motor_temperature_c") private Double motorTemperatureC;
    @Column(name = "bearing_temperature_c") private Double bearingTemperatureC;
    @Column(name = "cabinet_temperature_c") private Double cabinetTemperatureC;
    @Column(name = "vibration_mm_s") private Double vibrationMmS;
    @Column(name = "runtime_hours") private Double runtimeHours;
    @Column(name = "servo_on_hours") private Double servoOnHours;
    @Column(name = "start_stop_count") private Integer startStopCount;
    @Column(name = "lubrication_level_pct") private Double lubricationLevelPct;
    @Column(name = "battery_low") private Boolean batteryLow;
}

