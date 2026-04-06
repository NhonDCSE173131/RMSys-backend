package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "machine_import_batches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineImportBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "import_type", nullable = false, length = 50)
    private String importType;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "success_rows", nullable = false)
    @Builder.Default
    private Integer successRows = 0;

    @Column(name = "failed_rows", nullable = false)
    @Builder.Default
    private Integer failedRows = 0;

    @Column(name = "error_summary", columnDefinition = "NVARCHAR(MAX)")
    private String errorSummary;

    @Column(name = "profile_code", length = 100)
    private String profileCode;

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "uploaded_by", length = 100)
    @Builder.Default
    private String uploadedBy = "system";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

