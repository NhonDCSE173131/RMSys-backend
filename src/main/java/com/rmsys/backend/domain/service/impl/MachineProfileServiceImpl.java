package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.request.MachineMappingItemRequest;
import com.rmsys.backend.api.request.MachineProfileCreateRequest;
import com.rmsys.backend.api.response.MachineMappingResponse;
import com.rmsys.backend.api.response.MachineProfileResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineProfileEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.rmsys.backend.domain.repository.MachineProfileMappingRepository;
import com.rmsys.backend.domain.repository.MachineProfileRepository;
import com.rmsys.backend.domain.service.MachineProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MachineProfileServiceImpl implements MachineProfileService {

    private final MachineProfileRepository profileRepo;
    private final MachineProfileMappingRepository mappingRepo;

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
        List<MachineProfileMappingEntity> mappings = mappingRepo.findByProfileIdOrderByAddressStartAsc(profileId);
        return toResponse(profile, mappings);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MachineProfileResponse> getAllProfiles() {
        return profileRepo.findAll().stream()
                .map(p -> {
                    List<MachineProfileMappingEntity> mappings =
                            mappingRepo.findByProfileIdOrderByAddressStartAsc(p.getId());
                    return toResponse(p, mappings);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MachineProfileResponse updateMappings(UUID profileId, List<MachineMappingItemRequest> mappings) {
        MachineProfileEntity profile = profileRepo.findById(profileId)
                .orElseThrow(() -> AppException.notFound("MachineProfile", profileId));

        // Replace all existing mappings
        mappingRepo.deleteByProfileId(profileId);
        mappingRepo.flush();

        List<MachineProfileMappingEntity> entities = mappings.stream()
                .map(m -> MachineProfileMappingEntity.builder()
                        .profileId(profileId)
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
        log.info("Updated {} mappings for profile {}", entities.size(), profile.getProfileCode());
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
}

