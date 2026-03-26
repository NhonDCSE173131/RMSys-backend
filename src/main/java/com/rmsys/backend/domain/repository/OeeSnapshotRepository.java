package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.OeeSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OeeSnapshotRepository extends JpaRepository<OeeSnapshotEntity, UUID> {
    Optional<OeeSnapshotEntity> findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(UUID machineId, String bucketType);
    List<OeeSnapshotEntity> findByMachineIdAndBucketTypeAndBucketStartBetweenOrderByBucketStartAsc(
            UUID machineId, String bucketType, Instant from, Instant to);
    List<OeeSnapshotEntity> findByBucketTypeAndBucketStartAfterOrderByBucketStartDesc(String bucketType, Instant since);
}

