package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineTelemetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MachineTelemetryRepository extends JpaRepository<MachineTelemetryEntity, Long> {

    Optional<MachineTelemetryEntity> findFirstByMachineIdOrderByTsDesc(UUID machineId);

    List<MachineTelemetryEntity> findByMachineIdAndTsBetweenOrderByTsDesc(UUID machineId, Instant from, Instant to);

    @Query("SELECT t FROM MachineTelemetryEntity t WHERE t.ts = " +
           "(SELECT MAX(t2.ts) FROM MachineTelemetryEntity t2 WHERE t2.machineId = t.machineId)")
    List<MachineTelemetryEntity> findLatestForAllMachines();

    @Query("SELECT t FROM MachineTelemetryEntity t WHERE t.machineId = :machineId AND t.ts >= :since ORDER BY t.ts ASC")
    List<MachineTelemetryEntity> findRecentByMachineId(UUID machineId, Instant since);
}

