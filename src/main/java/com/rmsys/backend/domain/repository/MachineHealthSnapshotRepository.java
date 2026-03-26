package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineHealthSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MachineHealthSnapshotRepository extends JpaRepository<MachineHealthSnapshotEntity, UUID> {
    Optional<MachineHealthSnapshotEntity> findFirstByMachineIdOrderByBucketStartDesc(UUID machineId);
}

