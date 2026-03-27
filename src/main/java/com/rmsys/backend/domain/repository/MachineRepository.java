package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MachineRepository extends JpaRepository<MachineEntity, UUID> {
    Optional<MachineEntity> findByCode(String code);
    Optional<MachineEntity> findByCodeIgnoreCase(String code);
    long countByStatus(String status);
    long countByIsEnabledTrue();
}

