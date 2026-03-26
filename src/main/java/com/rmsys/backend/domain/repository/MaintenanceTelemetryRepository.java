package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MaintenanceTelemetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceTelemetryRepository extends JpaRepository<MaintenanceTelemetryEntity, Long> {
    Optional<MaintenanceTelemetryEntity> findFirstByMachineIdOrderByTsDesc(UUID machineId);
}

