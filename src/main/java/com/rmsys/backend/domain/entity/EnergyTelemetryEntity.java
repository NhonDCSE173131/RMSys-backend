package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "energy_telemetry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EnergyTelemetryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "voltage_v") private Double voltageV;
    @Column(name = "current_a") private Double currentA;
    @Column(name = "power_kw") private Double powerKw;
    @Column(name = "power_factor") private Double powerFactor;
    @Column(name = "frequency_hz") private Double frequencyHz;
    @Column(name = "energy_kwh_shift") private Double energyKwhShift;
    @Column(name = "energy_kwh_day") private Double energyKwhDay;
    @Column(name = "energy_kwh_month") private Double energyKwhMonth;
}

