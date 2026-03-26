package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.ToolPredictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ToolPredictionRepository extends JpaRepository<ToolPredictionEntity, UUID> {
    List<ToolPredictionEntity> findByMachineIdOrderByTsDesc(UUID machineId);
}

