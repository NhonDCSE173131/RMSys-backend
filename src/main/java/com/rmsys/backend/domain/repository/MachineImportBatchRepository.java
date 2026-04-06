package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.MachineImportBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MachineImportBatchRepository extends JpaRepository<MachineImportBatchEntity, UUID> {
    List<MachineImportBatchEntity> findByImportType(String importType);
    List<MachineImportBatchEntity> findByImportTypeAndStatus(String importType, String status);
    List<MachineImportBatchEntity> findByProfileCodeAndImportTypeAndStatusOrderByCreatedAtDesc(
            String profileCode,
            String importType,
            String status
    );
    List<MachineImportBatchEntity> findByProfileIdAndImportTypeAndStatusOrderByCreatedAtDesc(
            UUID profileId,
            String importType,
            String status
    );
    @Query("SELECT b FROM MachineImportBatchEntity b WHERE b.importType = :importType ORDER BY b.createdAt DESC")
    List<MachineImportBatchEntity> findByImportTypeOrderByCreatedDesc(@Param("importType") String importType);
}
