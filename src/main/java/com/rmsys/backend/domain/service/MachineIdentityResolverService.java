package com.rmsys.backend.domain.service;

import com.rmsys.backend.domain.entity.MachineEntity;

import java.util.UUID;

public interface MachineIdentityResolverService {
    MachineEntity resolveRequired(String machineIdentifier);

    MachineEntity resolveRequired(UUID machineId, String machineCode);

    default UUID resolveRequiredId(String machineIdentifier) {
        return resolveRequired(machineIdentifier).getId();
    }

    default UUID resolveRequiredId(UUID machineId, String machineCode) {
        return resolveRequired(machineId, machineCode).getId();
    }
}

