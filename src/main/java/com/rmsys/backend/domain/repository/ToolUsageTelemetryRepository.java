package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.ToolUsageTelemetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ToolUsageTelemetryRepository extends JpaRepository<ToolUsageTelemetryEntity, Long> {
    Optional<ToolUsageTelemetryEntity> findFirstByMachineIdAndToolCodeOrderByTsDesc(UUID machineId, String toolCode);
    List<ToolUsageTelemetryEntity> findByMachineIdOrderByTsDesc(UUID machineId);
}

