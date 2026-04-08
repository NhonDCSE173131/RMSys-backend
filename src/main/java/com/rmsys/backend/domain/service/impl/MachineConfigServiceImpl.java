package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.request.MachineCreateRequest;
import com.rmsys.backend.api.request.MachineUpdateRequest;
import com.rmsys.backend.api.response.MachineConfigResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineImportBatchEntity;
import com.rmsys.backend.domain.entity.MachineProfileEntity;
import com.rmsys.backend.domain.repository.MachineImportBatchRepository;
import com.rmsys.backend.domain.repository.MachineProfileRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.MachineConfigService;
import com.rmsys.backend.domain.service.PlcConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MachineConfigServiceImpl implements MachineConfigService {

    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of(
            "modbus-tcp", "opc-ua", "simulator"
    );

    private final MachineRepository machineRepo;
    private final MachineProfileRepository profileRepo;
    private final MachineImportBatchRepository batchRepo;
    private final PlcConnectionManager plcConnectionManager;

    @Override
    @Transactional
    public MachineConfigResponse createMachine(MachineCreateRequest req) {
        // Validate code uniqueness
        if (machineRepo.existsByCode(req.getCode())) {
            throw new AppException("MACHINE_CODE_DUPLICATE",
                    "Machine code already exists: " + req.getCode());
        }

        String protocol = normalizeProtocol(req.getProtocol());

        // Validate profile
        String profileCode = null;
        if (req.getProfileId() == null) {
            throw new AppException("PROFILE_REQUIRED", "Profile is required");
        }
        MachineProfileEntity profile = profileRepo.findById(req.getProfileId())
                .orElseThrow(() -> AppException.notFound("MachineProfile", req.getProfileId()));
        profileCode = profile.getProfileCode();

        validateMappingFileSelection(req.getProfileId(), req.getMappingFileId());

        validateNetworkFieldsForProtocol(protocol, req.getHost(), req.getPort(), req.getUnitId());

        // Default port for modbus-tcp
        Integer port = req.getPort();
        if (port == null && "modbus-tcp".equalsIgnoreCase(protocol)) {
            port = 502;
        }

        MachineEntity machine = MachineEntity.builder()
                .code(req.getCode())
                .name(req.getName())
                .type(req.getType() != null ? req.getType() : "CNC_MACHINE")
                .vendor(req.getVendor() != null ? req.getVendor() : "UNKNOWN")
                .model(req.getModel())
                .lineId(req.getLineId())
                .plantId(req.getPlantId())
                .status("OFFLINE")
                .protocol(protocol)
                .host(req.getHost())
                .port(port)
                .unitId(req.getUnitId() != null ? req.getUnitId() : 1)
                .pollIntervalMs(req.getPollIntervalMs() != null ? req.getPollIntervalMs() : 1000)
                .autoConnect(req.getAutoConnect() != null && req.getAutoConnect())
                .profileId(req.getProfileId())
                .mappingFileId(req.getMappingFileId())
                .connectionMode("MANUAL")
                .build();

        machine = machineRepo.save(machine);
        log.info("Created machine config: {} ({})", machine.getCode(), machine.getId());

        // Auto-connect only when machine is explicitly eligible.
        if (Boolean.TRUE.equals(machine.getIsEnabled()) && Boolean.TRUE.equals(machine.getAutoConnect())) {
            try {
                plcConnectionManager.startConnection(machine.getId());
            } catch (Exception e) {
                log.warn("Immediate connect failed for machine {}: {}", machine.getCode(), e.getMessage());
            }
        }

        return toResponse(machine, profileCode);
    }

    @Override
    @Transactional
    public MachineConfigResponse updateMachine(UUID machineId, MachineUpdateRequest req) {
        MachineEntity machine = machineRepo.findById(machineId)
                .orElseThrow(() -> AppException.notFound("Machine", machineId));

        if (req.getName() != null) machine.setName(req.getName());
        if (req.getType() != null) machine.setType(req.getType());
        if (req.getVendor() != null) machine.setVendor(req.getVendor());
        if (req.getModel() != null) machine.setModel(req.getModel());
        if (req.getProtocol() != null) {
            machine.setProtocol(normalizeProtocol(req.getProtocol()));
        }
        if (req.getHost() != null) machine.setHost(req.getHost());
        if (req.getPort() != null) machine.setPort(req.getPort());
        if (req.getUnitId() != null) machine.setUnitId(req.getUnitId());
        if (req.getPollIntervalMs() != null) machine.setPollIntervalMs(req.getPollIntervalMs());
        if (req.getAutoConnect() != null) machine.setAutoConnect(req.getAutoConnect());
        if (req.getProfileId() != null) {
            profileRepo.findById(req.getProfileId())
                    .orElseThrow(() -> AppException.notFound("MachineProfile", req.getProfileId()));
            machine.setProfileId(req.getProfileId());
        }
        if (req.getMappingFileId() != null) {
            UUID profileIdForValidation = machine.getProfileId();
            validateMappingFileSelection(profileIdForValidation, req.getMappingFileId());
            machine.setMappingFileId(req.getMappingFileId());
        }
        if (req.getLineId() != null) machine.setLineId(req.getLineId());
        if (req.getPlantId() != null) machine.setPlantId(req.getPlantId());

        if ("modbus-tcp".equalsIgnoreCase(machine.getProtocol())) {
            if (machine.getPort() == null) {
                machine.setPort(502);
            }
            if (machine.getUnitId() == null) {
                machine.setUnitId(1);
            }
        }

        if (machine.getProfileId() == null) {
            throw new AppException("PROFILE_REQUIRED", "Profile is required");
        }
        validateNetworkFieldsForProtocol(machine.getProtocol(), machine.getHost(), machine.getPort(), machine.getUnitId());

        machine = machineRepo.save(machine);
        log.info("Updated machine config: {} ({})", machine.getCode(), machine.getId());

        String profileCode = resolveProfileCode(machine.getProfileId());
        return toResponse(machine, profileCode);
    }

    @Override
    @Transactional(readOnly = true)
    public MachineConfigResponse getMachineConfig(UUID machineId) {
        MachineEntity machine = machineRepo.findById(machineId)
                .orElseThrow(() -> AppException.notFound("Machine", machineId));
        String profileCode = resolveProfileCode(machine.getProfileId());
        return toResponse(machine, profileCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MachineConfigResponse> getAllMachineConfigs() {
        return machineRepo.findAll().stream()
                .map(m -> toResponse(m, resolveProfileCode(m.getProfileId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void disableMachine(UUID machineId) {
        MachineEntity machine = machineRepo.findById(machineId)
                .orElseThrow(() -> AppException.notFound("Machine", machineId));
        machine.setIsEnabled(false);
        machine.setLastConnectionStatus("DISABLED");
        machineRepo.save(machine);
        // Stop runtime connection
        plcConnectionManager.stopConnection(machineId);
        log.info("Disabled machine: {} ({})", machine.getCode(), machineId);
    }

    @Override
    @Transactional
    public void enableMachine(UUID machineId) {
        MachineEntity machine = machineRepo.findById(machineId)
                .orElseThrow(() -> AppException.notFound("Machine", machineId));
        machine.setIsEnabled(true);
        machineRepo.save(machine);
        log.info("Enabled machine: {} ({})", machine.getCode(), machineId);
    }

    // ---- helpers ----

    private String normalizeProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            throw new AppException("PROTOCOL_REQUIRED", "Protocol is required");
        }
        String normalized = protocol.trim().toLowerCase();
        // Protocol taxonomy aliases for backward compatibility.
        if ("simulator".equals(normalized)) {
            normalized = "simulator-internal";
        }
        if (!SUPPORTED_PROTOCOLS.contains(normalized)) {
            throw new AppException("UNSUPPORTED_PROTOCOL", "Protocol not supported: " + protocol);
        }
        return normalized;
    }

    private void validateNetworkFieldsForProtocol(String protocol, String host, Integer port, Integer unitId) {
        if (!"modbus-tcp".equalsIgnoreCase(protocol)) {
            return;
        }
        if (host == null || host.isBlank()) {
            throw new AppException("HOST_REQUIRED", "Host is required for modbus-tcp machines");
        }
        if (port != null && port <= 0) {
            throw new AppException("INVALID_PORT", "Port must be greater than 0");
        }
        if (unitId != null && unitId <= 0) {
            throw new AppException("INVALID_UNIT_ID", "Unit ID must be greater than 0");
        }
    }

    private String resolveProfileCode(UUID profileId) {
        if (profileId == null) return null;
        return profileRepo.findById(profileId)
                .map(MachineProfileEntity::getProfileCode)
                .orElse(null);
    }

    private void validateMappingFileSelection(UUID profileId, UUID mappingFileId) {
        if (mappingFileId == null) {
            return;
        }
        MachineImportBatchEntity batch = batchRepo.findById(mappingFileId)
                .orElseThrow(() -> new AppException("IMPORT_FILE_NOT_FOUND", "Mapping file not found: " + mappingFileId));

        String type = batch.getImportType() == null ? "" : batch.getImportType().toUpperCase();
        if (!"MAPPING".equals(type) && !"MAPPINGS".equals(type)) {
            throw new AppException("INVALID_IMPORT_FILE_TYPE", "Selected file is not a mapping file");
        }

        String status = batch.getStatus() == null ? "" : batch.getStatus().toUpperCase();
        if (!"COMPLETED".equals(status) && !"COMPLETED_WITH_ERRORS".equals(status)) {
            throw new AppException("IMPORT_FILE_NOT_READY", "Mapping file is not ready: " + batch.getStatus());
        }

        // Intentionally do not enforce profile-mapping coupling.
        // UI can select profile and mapping independently.
    }

    private MachineConfigResponse toResponse(MachineEntity m, String profileCode) {
        return MachineConfigResponse.builder()
                .id(m.getId())
                .code(m.getCode())
                .name(m.getName())
                .type(m.getType())
                .vendor(m.getVendor())
                .model(m.getModel())
                .protocol(m.getProtocol())
                .host(m.getHost())
                .port(m.getPort())
                .unitId(m.getUnitId())
                .pollIntervalMs(m.getPollIntervalMs())
                .connectionMode(m.getConnectionMode())
                .autoConnect(m.getAutoConnect())
                .profileId(m.getProfileId())
                .mappingFileId(m.getMappingFileId())
                .profileCode(profileCode)
                .lineId(m.getLineId())
                .plantId(m.getPlantId())
                .status(m.getStatus())
                .isEnabled(m.getIsEnabled())
                .lastConnectionStatus(m.getLastConnectionStatus())
                .lastConnectedAt(m.getLastConnectedAt())
                .lastDisconnectedAt(m.getLastDisconnectedAt())
                .lastDataAt(m.getLastDataAt())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}

