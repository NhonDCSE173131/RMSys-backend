package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.request.MachineProfileCreateRequest;
import com.rmsys.backend.api.request.MachineMappingItemRequest;
import com.rmsys.backend.api.response.MachineProfileResponse;

import java.util.List;
import java.util.UUID;

public interface MachineProfileService {
    MachineProfileResponse createProfile(MachineProfileCreateRequest request);
    MachineProfileResponse getProfile(UUID profileId);
    List<MachineProfileResponse> getAllProfiles();
    MachineProfileResponse updateMappings(UUID profileId, List<MachineMappingItemRequest> mappings);
    void deleteProfile(UUID profileId);
}

