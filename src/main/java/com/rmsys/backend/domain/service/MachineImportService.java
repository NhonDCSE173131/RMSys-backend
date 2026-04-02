package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.MachineImportResultResponse;
import com.rmsys.backend.common.enumtype.ImportMode;
import org.springframework.web.multipart.MultipartFile;

public interface MachineImportService {
    MachineImportResultResponse importMachines(MultipartFile file, ImportMode mode);
    MachineImportResultResponse importProfiles(MultipartFile file);
    MachineImportResultResponse importMappings(MultipartFile file);
    MachineImportResultResponse validateFile(MultipartFile file, String importType);
}

