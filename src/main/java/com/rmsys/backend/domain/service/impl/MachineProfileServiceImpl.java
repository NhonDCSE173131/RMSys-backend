package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.request.MachineMappingItemRequest;
import com.rmsys.backend.api.request.MachineProfileCreateRequest;
import com.rmsys.backend.api.response.MachineMappingResponse;
import com.rmsys.backend.api.response.MachineProfileResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineProfileEntity;
import com.rmsys.backend.domain.entity.MachineImportBatchEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.rmsys.backend.domain.repository.MachineImportBatchRepository;
import com.rmsys.backend.domain.repository.MachineProfileMappingRepository;
import com.rmsys.backend.domain.repository.MachineProfileRepository;
import com.rmsys.backend.domain.service.MachineProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MachineProfileServiceImpl implements MachineProfileService {

    private final MachineProfileRepository profileRepo;
    private final MachineProfileMappingRepository mappingRepo;
    private final MachineImportBatchRepository batchRepo;

    @Override
    @Transactional
    public MachineProfileResponse createProfile(MachineProfileCreateRequest req) {
        if (profileRepo.existsByProfileCode(req.getProfileCode())) {
            throw new AppException("PROFILE_CODE_DUPLICATE",
                    "Profile code already exists: " + req.getProfileCode());
        }

        MachineProfileEntity profile = MachineProfileEntity.builder()
                .profileCode(req.getProfileCode())
                .profileName(req.getProfileName())
                .protocol(req.getProtocol())
                .vendor(req.getVendor())
                .model(req.getModel())
                .description(req.getDescription())
                .build();

        profile = profileRepo.save(profile);
        log.info("Created profile: {} ({})", profile.getProfileCode(), profile.getId());
        return toResponse(profile, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public MachineProfileResponse getProfile(UUID profileId) {
        MachineProfileEntity profile = profileRepo.findById(profileId)
                .orElseThrow(() -> AppException.notFound("MachineProfile", profileId));
        List<MachineProfileMappingEntity> mappings = resolveLatestMappings(profileId);
        return toResponse(profile, mappings);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MachineProfileResponse> getAllProfiles() {
        return profileRepo.findAll().stream()
                .map(p -> {
                    List<MachineProfileMappingEntity> mappings = resolveLatestMappings(p.getId());
                    return toResponse(p, mappings);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MachineProfileResponse updateMappings(UUID profileId, List<MachineMappingItemRequest> mappings) {
        MachineProfileEntity profile = profileRepo.findById(profileId)
                .orElseThrow(() -> AppException.notFound("MachineProfile", profileId));

        Set<String> seen = new LinkedHashSet<>();
        for (MachineMappingItemRequest m : mappings) {
            String logicalKey = m.getLogicalKey();
            String normalized = logicalKey == null ? null : logicalKey.trim().toLowerCase(Locale.ROOT);
            if (normalized == null || normalized.isBlank()) {
                throw new AppException("INVALID_MAPPING", "logicalKey is required");
            }
            if (!seen.add(normalized)) {
                throw new AppException("INVALID_MAPPING", "Duplicate logicalKey in request: " + logicalKey);
            }
        }

        MachineImportBatchEntity batch = MachineImportBatchEntity.builder()
                .fileName("manual-profile-update")
                .importType("MAPPINGS")
                .status("PENDING")
                .totalRows(mappings.size())
                .profileCode(profile.getProfileCode())
                .profileId(profileId)
                .uploadedBy("manual")
                .build();
        batch = batchRepo.save(batch);
        final UUID mappingFileId = batch.getId();

        List<MachineProfileMappingEntity> entities = mappings.stream()
                .map(m -> MachineProfileMappingEntity.builder()
                        .profileId(profileId)
                        .mappingFileId(mappingFileId)
                        .logicalKey(m.getLogicalKey())
                        .area(m.getArea())
                        .addressStart(m.getAddressStart())
                        .addressEnd(m.getAddressEnd())
                        .dataType(m.getDataType())
                        .scaleFactor(m.getScaleFactor() != null ? m.getScaleFactor() : 1.0)
                        .unit(m.getUnit())
                        .byteOrder(m.getByteOrder() != null ? m.getByteOrder() : "BIG")
                        .wordOrder(m.getWordOrder() != null ? m.getWordOrder() : "BIG")
                        .isRequired(m.getIsRequired() != null ? m.getIsRequired() : true)
                        .description(m.getDescription())
                        .build())
                .collect(Collectors.toList());

        entities = mappingRepo.saveAll(entities);
        batch.setStatus("COMPLETED");
        batch.setSuccessRows(entities.size());
        batch.setFailedRows(0);
        batchRepo.save(batch);

        log.info("Created mapping version {} with {} mappings for profile {}",
                batch.getId(), entities.size(), profile.getProfileCode());
        return toResponse(profile, entities);
    }

    @Override
    @Transactional
    public void deleteProfile(UUID profileId) {
        if (!profileRepo.existsById(profileId)) {
            throw AppException.notFound("MachineProfile", profileId);
        }
        mappingRepo.deleteByProfileId(profileId);
        profileRepo.deleteById(profileId);
        log.info("Deleted profile: {}", profileId);
    }

    // ---- helpers ----

    private MachineProfileResponse toResponse(MachineProfileEntity p, List<MachineProfileMappingEntity> mappings) {
        return MachineProfileResponse.builder()
                .id(p.getId())
                .profileCode(p.getProfileCode())
                .profileName(p.getProfileName())
                .protocol(p.getProtocol())
                .vendor(p.getVendor())
                .model(p.getModel())
                .description(p.getDescription())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .mappings(mappings.stream().map(this::toMappingResponse).collect(Collectors.toList()))
                .build();
    }

    private MachineMappingResponse toMappingResponse(MachineProfileMappingEntity m) {
        return MachineMappingResponse.builder()
                .id(m.getId())
                .profileId(m.getProfileId())
                .mappingFileId(m.getMappingFileId())
                .logicalKey(m.getLogicalKey())
                .area(m.getArea())
                .addressStart(m.getAddressStart())
                .addressEnd(m.getAddressEnd())
                .dataType(m.getDataType())
                .scaleFactor(m.getScaleFactor())
                .unit(m.getUnit())
                .byteOrder(m.getByteOrder())
                .wordOrder(m.getWordOrder())
                .isRequired(m.getIsRequired())
                .description(m.getDescription())
                .build();
    }

    private List<MachineProfileMappingEntity> resolveLatestMappings(UUID profileId) {
        List<MachineImportBatchEntity> completed = batchRepo
                .findByProfileIdAndImportTypeAndStatusOrderByCreatedAtDesc(profileId, "MAPPINGS", "COMPLETED");
        if (!completed.isEmpty()) {
            UUID latestMappingFileId = completed.get(0).getId();
            List<MachineProfileMappingEntity> mapped =
                    mappingRepo.findByProfileIdAndMappingFileIdOrderByAddressStartAsc(profileId, latestMappingFileId);
            if (!mapped.isEmpty()) {
                return mapped;
            }
        }
        // Legacy fallback for old rows that have no mapping_file_id.
        return mappingRepo.findByProfileIdOrderByAddressStartAsc(profileId);
    }
}

