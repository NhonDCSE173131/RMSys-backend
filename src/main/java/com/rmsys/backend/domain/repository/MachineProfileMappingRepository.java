package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MachineProfileMappingRepository extends JpaRepository<MachineProfileMappingEntity, UUID> {
    List<MachineProfileMappingEntity> findByProfileId(UUID profileId);
    List<MachineProfileMappingEntity> findByProfileIdOrderByAddressStartAsc(UUID profileId);
    List<MachineProfileMappingEntity> findByProfileIdAndMappingFileIdOrderByAddressStartAsc(UUID profileId, UUID mappingFileId);
    List<MachineProfileMappingEntity> findByMappingFileIdOrderByAddressStartAsc(UUID mappingFileId);
    void deleteByProfileId(UUID profileId);
    void deleteByProfileIdAndMappingFileId(UUID profileId, UUID mappingFileId);
    boolean existsByProfileIdAndLogicalKey(UUID profileId, String logicalKey);
    boolean existsByProfileIdAndMappingFileIdAndLogicalKey(UUID profileId, UUID mappingFileId, String logicalKey);
}

