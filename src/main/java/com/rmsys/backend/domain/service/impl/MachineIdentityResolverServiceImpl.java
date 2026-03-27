package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.MachineIdentityResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MachineIdentityResolverServiceImpl implements MachineIdentityResolverService {

    private final MachineRepository machineRepository;

    @Override
    public MachineEntity resolveRequired(String machineIdentifier) {
        if (machineIdentifier == null || machineIdentifier.isBlank()) {
            throw new AppException("VALIDATION_ERROR", "machineId or machineCode is required");
        }

        String normalized = machineIdentifier.trim();
        try {
            UUID machineId = UUID.fromString(normalized);
            return machineRepository.findById(machineId)
                    .orElseThrow(() -> AppException.notFound("Machine", normalized));
        } catch (IllegalArgumentException ignored) {
            return machineRepository.findByCodeIgnoreCase(normalized)
                    .orElseThrow(() -> AppException.notFound("Machine", normalized));
        }
    }

    @Override
    public MachineEntity resolveRequired(UUID machineId, String machineCode) {
        if (machineId == null && (machineCode == null || machineCode.isBlank())) {
            throw new AppException("VALIDATION_ERROR", "machineId or machineCode is required");
        }

        if (machineId != null && machineCode != null && !machineCode.isBlank()) {
            MachineEntity machine = machineRepository.findById(machineId)
                    .orElseThrow(() -> AppException.notFound("Machine", machineId));
            if (!machine.getCode().equalsIgnoreCase(machineCode.trim())) {
                throw new AppException("VALIDATION_ERROR", "machineId does not match machineCode");
            }
            return machine;
        }

        if (machineId != null) {
            return machineRepository.findById(machineId)
                    .orElseThrow(() -> AppException.notFound("Machine", machineId));
        }

        return machineRepository.findByCodeIgnoreCase(machineCode.trim())
                .orElseThrow(() -> AppException.notFound("Machine", machineCode.trim()));
    }
}

