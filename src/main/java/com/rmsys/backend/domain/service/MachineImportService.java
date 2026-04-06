package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.request.MachineConfigImportRequest;
import com.rmsys.backend.api.request.MachineMappingImportRequest;
import com.rmsys.backend.api.request.MachineProfileImportRequest;
import com.rmsys.backend.api.response.ImportFileResponse;
import com.rmsys.backend.api.response.MachineImportResultResponse;
import com.rmsys.backend.api.response.ProfileMappingValidationResponse;
import com.rmsys.backend.common.enumtype.ImportMode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface MachineImportService {
    // CSV import (existing)
    MachineImportResultResponse importMachines(MultipartFile file, ImportMode mode);
    MachineImportResultResponse importProfiles(MultipartFile file);
    MachineImportResultResponse importMappings(MultipartFile file);
    MachineImportResultResponse validateFile(MultipartFile file, String importType);

    // JSON import (new)
    MachineImportResultResponse importMachinesJson(MachineConfigImportRequest request, ImportMode mode);
    MachineImportResultResponse importProfilesJson(MachineProfileImportRequest request);
    MachineImportResultResponse importMappingsJson(MachineMappingImportRequest request);

    // File listing and validation (new)
    List<ImportFileResponse> listImportFiles(String importType, String profileCode);
    ImportFileResponse getImportFileDetail(UUID fileId);
    ProfileMappingValidationResponse validateProfileMapping(UUID profileId, UUID mappingFileId);
}

