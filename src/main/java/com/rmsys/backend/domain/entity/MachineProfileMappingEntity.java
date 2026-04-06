package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "machine_profile_mappings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"profile_id", "mapping_file_id", "logical_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineProfileMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "mapping_file_id")
    private UUID mappingFileId;

    @Column(name = "logical_key", nullable = false, length = 100)
    private String logicalKey;

    @Column(nullable = false, length = 50)
    private String area;

    @Column(name = "address_start", nullable = false)
    private Integer addressStart;

    @Column(name = "address_end")
    private Integer addressEnd;

    @Column(name = "data_type", nullable = false, length = 30)
    private String dataType;

    @Column(name = "scale_factor", nullable = false)
    @Builder.Default
    private Double scaleFactor = 1.0;

    @Column(length = 20)
    private String unit;

    @Column(name = "byte_order", nullable = false, length = 10)
    @Builder.Default
    private String byteOrder = "BIG";

    @Column(name = "word_order", nullable = false, length = 10)
    @Builder.Default
    private String wordOrder = "BIG";

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = true;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

