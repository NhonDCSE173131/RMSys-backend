package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.AlarmEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlarmEventRepository extends JpaRepository<AlarmEventEntity, UUID> {
    List<AlarmEventEntity> findByIsActiveTrueOrderByStartedAtDesc();
    List<AlarmEventEntity> findByMachineIdAndIsActiveTrueOrderByStartedAtDesc(UUID machineId);
    long countByIsActiveTrueAndSeverity(String severity);
    long countByIsActiveTrue();
    Page<AlarmEventEntity> findByMachineIdOrderByStartedAtDesc(UUID machineId, Pageable pageable);
    Page<AlarmEventEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
    List<AlarmEventEntity> findByMachineIdAndStartedAtBetween(UUID machineId, Instant from, Instant to);
}

