package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.EnergyTelemetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnergyTelemetryRepository extends JpaRepository<EnergyTelemetryEntity, Long> {
    Optional<EnergyTelemetryEntity> findFirstByMachineIdOrderByTsDesc(UUID machineId);
    List<EnergyTelemetryEntity> findByMachineIdAndTsBetweenOrderByTsAsc(UUID machineId, Instant from, Instant to);
    List<EnergyTelemetryEntity> findByTsBetweenOrderByTsAsc(Instant from, Instant to);
}

