package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MaintenancePredictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenancePredictionRepository extends JpaRepository<MaintenancePredictionEntity, UUID> {
    Optional<MaintenancePredictionEntity> findFirstByMachineIdOrderByTsDesc(UUID machineId);
    List<MaintenancePredictionEntity> findByMachineIdOrderByTsDesc(UUID machineId);
}

