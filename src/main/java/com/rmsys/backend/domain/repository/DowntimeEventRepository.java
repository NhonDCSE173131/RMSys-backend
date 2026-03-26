package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.DowntimeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DowntimeEventRepository extends JpaRepository<DowntimeEventEntity, UUID> {
    List<DowntimeEventEntity> findByMachineIdAndStartedAtBetween(UUID machineId, Instant from, Instant to);
    long countByAbnormalStopTrueAndStartedAtAfter(Instant since);
    List<DowntimeEventEntity> findByMachineIdOrderByStartedAtDesc(UUID machineId);
}

