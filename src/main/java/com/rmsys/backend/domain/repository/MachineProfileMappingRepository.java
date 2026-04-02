package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MachineProfileMappingRepository extends JpaRepository<MachineProfileMappingEntity, UUID> {
    List<MachineProfileMappingEntity> findByProfileId(UUID profileId);
    List<MachineProfileMappingEntity> findByProfileIdOrderByAddressStartAsc(UUID profileId);
    void deleteByProfileId(UUID profileId);
    boolean existsByProfileIdAndLogicalKey(UUID profileId, String logicalKey);
}

