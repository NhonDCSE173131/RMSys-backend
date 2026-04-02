package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MachineProfileRepository extends JpaRepository<MachineProfileEntity, UUID> {
    Optional<MachineProfileEntity> findByProfileCode(String profileCode);
    Optional<MachineProfileEntity> findByProfileCodeIgnoreCase(String profileCode);
    boolean existsByProfileCode(String profileCode);
    List<MachineProfileEntity> findByIsActiveTrue();
}

