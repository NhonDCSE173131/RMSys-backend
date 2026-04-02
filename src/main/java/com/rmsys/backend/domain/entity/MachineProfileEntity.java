package com.rmsys.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "machine_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_code", nullable = false, unique = true, length = 100)
    private String profileCode;

    @Column(name = "profile_name", nullable = false, length = 200)
    private String profileName;

    @Column(nullable = false, length = 50)
    private String protocol;

    @Column(length = 50)
    private String vendor;

    @Column(length = 100)
    private String model;

    @Column(length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

