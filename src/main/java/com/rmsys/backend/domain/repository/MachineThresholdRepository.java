package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineThresholdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MachineThresholdRepository extends JpaRepository<MachineThresholdEntity, UUID> {
    List<MachineThresholdEntity> findByMachineId(UUID machineId);
    Optional<MachineThresholdEntity> findByMachineIdAndMetricCode(UUID machineId, String metricCode);
}

