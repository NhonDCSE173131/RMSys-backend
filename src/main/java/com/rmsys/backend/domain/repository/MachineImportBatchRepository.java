package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineImportBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MachineImportBatchRepository extends JpaRepository<MachineImportBatchEntity, UUID> {
}

