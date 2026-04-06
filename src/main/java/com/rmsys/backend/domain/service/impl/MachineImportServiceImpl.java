package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.request.MachineConfigImportRequest;
import com.rmsys.backend.api.request.MachineMappingImportRequest;
import com.rmsys.backend.api.request.MachineProfileImportRequest;
import com.rmsys.backend.api.response.MachineImportResultResponse;
import com.rmsys.backend.api.response.MachineImportResultResponse.ImportRowError;
import com.rmsys.backend.api.response.ImportFileResponse;
import com.rmsys.backend.api.response.ProfileMappingValidationResponse;
import com.rmsys.backend.common.enumtype.ImportMode;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineImportBatchEntity;
import com.rmsys.backend.domain.entity.MachineProfileEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.rmsys.backend.domain.repository.MachineImportBatchRepository;
import com.rmsys.backend.domain.repository.MachineProfileMappingRepository;
import com.rmsys.backend.domain.repository.MachineProfileRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.MachineImportService;
import com.rmsys.backend.domain.service.PlcConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MachineImportServiceImpl implements MachineImportService {

    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of("modbus-tcp", "opc-ua", "simulator");
    private static final Set<String> REQUIRED_MACHINE_HEADERS = Set.of(
            "machine_code", "machine_name", "protocol", "host", "port", "unit_id", "profile_code", "poll_interval_ms", "auto_connect", "description"
    );
    private static final Set<String> REQUIRED_PROFILE_HEADERS = Set.of("profile_code", "profile_name", "protocol", "description");
    private static final Set<String> REQUIRED_MAPPING_BASE_HEADERS = Set.of(
            "profile_code", "logical_key", "area", "bit_index", "data_type", "unit", "byte_order", "word_order"
    );

    private final MachineRepository machineRepo;
    private final MachineProfileRepository profileRepo;
    private final MachineProfileMappingRepository mappingRepo;
    private final MachineImportBatchRepository batchRepo;
    private final PlcConnectionManager plcConnectionManager;

    @Override
    @Transactional
    public MachineImportResultResponse importMachines(MultipartFile file, ImportMode mode) {
        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;
        Set<UUID> machineIdsToConnect = new LinkedHashSet<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            requireHeaders(parser.getHeaderMap(), REQUIRED_MACHINE_HEADERS, "machines");

            Set<String> seenCodes = new HashSet<>();
            List<CSVRecord> records = parser.getRecords();
            totalRows = records.size();

            for (CSVRecord record : records) {
                int rowNum = (int) record.getRecordNumber() + 1;
                try {
                    String code = getField(record, "machine_code");
                    if (code == null || code.isBlank()) {
                        errors.add(error(rowNum, "machine_code", "Machine code is required"));
                        continue;
                    }
                    if (seenCodes.contains(code)) {
                        errors.add(error(rowNum, "machine_code", "Duplicate code in file: " + code));
                        continue;
                    }
                    seenCodes.add(code);

                    String name = getField(record, "machine_name");
                    if (name == null || name.isBlank()) {
                        errors.add(error(rowNum, "machine_name", "Machine name is required"));
                        continue;
                    }

                    String protocol = getField(record, "protocol");
                    if (protocol != null && !protocol.isBlank() && !SUPPORTED_PROTOCOLS.contains(protocol.toLowerCase())) {
                        errors.add(error(rowNum, "protocol", "Unsupported protocol: " + protocol));
                        continue;
                    }

                    String profileCode = getField(record, "profile_code");
                    if (profileCode == null || profileCode.isBlank()) {
                        errors.add(error(rowNum, "profile_code", "Profile code is required"));
                        continue;
                    }
                    UUID profileId = null;
                    Optional<MachineProfileEntity> profileOpt = profileRepo.findByProfileCode(profileCode);
                    if (profileOpt.isEmpty()) {
                        errors.add(error(rowNum, "profile_code", "Profile not found: " + profileCode));
                        continue;
                    }
                    profileId = profileOpt.get().getId();

                    String host = getField(record, "host");
                    Integer port = parseIntField(record, "port");
                    if (port == null && "modbus-tcp".equalsIgnoreCase(protocol)) {
                        port = 502;
                    }
                    Integer unitId = parseIntField(record, "unit_id");
                    if (unitId == null && "modbus-tcp".equalsIgnoreCase(protocol)) {
                        unitId = 1;
                    }
                    Integer pollIntervalMs = parseIntField(record, "poll_interval_ms");
                    Boolean autoConnect = parseBoolField(record, "auto_connect");

                    if ("modbus-tcp".equalsIgnoreCase(protocol)) {
                        if (host == null || host.isBlank()) {
                            errors.add(error(rowNum, "host", "Host is required for modbus-tcp"));
                            continue;
                        }
                        if (port == null || port <= 0) {
                            errors.add(error(rowNum, "port", "Port must be greater than 0"));
                            continue;
                        }
                        if (unitId == null || unitId <= 0) {
                            errors.add(error(rowNum, "unit_id", "Unit ID must be greater than 0"));
                            continue;
                        }
                    }

                    Optional<MachineEntity> existingOpt = machineRepo.findByCode(code);
                    if (existingOpt.isPresent()) {
                        if (mode == ImportMode.CREATE_ONLY) {
                            errors.add(error(rowNum, "machine_code", "Machine already exists: " + code));
                            continue;
                        }
                        // UPSERT - update existing
                        MachineEntity existing = existingOpt.get();
                        existing.setName(name);
                        if (getField(record, "type") != null) existing.setType(getField(record, "type"));
                        if (getField(record, "vendor") != null) existing.setVendor(getField(record, "vendor"));
                        if (getField(record, "model") != null) existing.setModel(getField(record, "model"));
                        if (protocol != null) existing.setProtocol(protocol);
                        if (host != null) existing.setHost(host);
                        if (port != null) existing.setPort(port);
                        if (unitId != null) existing.setUnitId(unitId);
                        if (pollIntervalMs != null) existing.setPollIntervalMs(pollIntervalMs);
                        if (autoConnect != null) existing.setAutoConnect(autoConnect);
                        if (profileId != null) existing.setProfileId(profileId);
                        if (getField(record, "line_id") != null) existing.setLineId(getField(record, "line_id"));
                        if (getField(record, "plant_id") != null) existing.setPlantId(getField(record, "plant_id"));
                        machineRepo.save(existing);
                        if (Boolean.TRUE.equals(existing.getIsEnabled()) && Boolean.TRUE.equals(existing.getAutoConnect())) {
                            machineIdsToConnect.add(existing.getId());
                        }
                    } else {
                        MachineEntity machine = MachineEntity.builder()
                                .code(code)
                                .name(name)
                                .type(getField(record, "type") != null ? getField(record, "type") : "CNC_MACHINE")
                                .vendor(getField(record, "vendor") != null ? getField(record, "vendor") : "UNKNOWN")
                                .model(getField(record, "model"))
                                .lineId(getField(record, "line_id"))
                                .plantId(getField(record, "plant_id"))
                                .status("OFFLINE")
                                .protocol(protocol)
                                .host(host)
                                .port(port)
                                .unitId(unitId != null ? unitId : 1)
                                .pollIntervalMs(pollIntervalMs != null ? pollIntervalMs : 1000)
                                .isEnabled(true)
                                .autoConnect(autoConnect != null && autoConnect)
                                .profileId(profileId)
                                .connectionMode("MANUAL")
                                .build();
                        machine = machineRepo.save(machine);
                        if (Boolean.TRUE.equals(machine.getIsEnabled()) && Boolean.TRUE.equals(machine.getAutoConnect())) {
                            machineIdsToConnect.add(machine.getId());
                        }
                    }
                    successRows++;
                } catch (Exception e) {
                    errors.add(error(rowNum, "general", "Unexpected error: " + e.getMessage()));
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("IMPORT_PARSE_ERROR", "Failed to parse CSV file: " + e.getMessage());
        }

        // Save batch record
        MachineImportBatchEntity batch = MachineImportBatchEntity.builder()
                .fileName(file.getOriginalFilename())
                .importType("MACHINES")
                .status(errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_ERRORS")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errorSummary(errors.isEmpty() ? null : errors.stream()
                        .map(e -> "Row " + e.getRow() + ": " + e.getMessage())
                        .collect(Collectors.joining("; ")))
                .build();
        batch = batchRepo.save(batch);

        scheduleConnectAfterCommit(machineIdsToConnect);

        return MachineImportResultResponse.builder()
                .batchId(batch.getId())
                .importType("MACHINES")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    private void scheduleConnectAfterCommit(Set<UUID> machineIdsToConnect) {
        if (machineIdsToConnect.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            machineIdsToConnect.forEach(this::connectMachineSafely);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                machineIdsToConnect.forEach(MachineImportServiceImpl.this::connectMachineSafely);
            }
        });
    }

    private void connectMachineSafely(UUID machineId) {
        try {
            plcConnectionManager.startConnection(machineId);
        } catch (Exception e) {
            log.warn("Immediate connect after import failed for machine {}: {}", machineId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public MachineImportResultResponse importProfiles(MultipartFile file) {
        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            requireHeaders(parser.getHeaderMap(), REQUIRED_PROFILE_HEADERS, "profiles");

            List<CSVRecord> records = parser.getRecords();
            totalRows = records.size();

            for (CSVRecord record : records) {
                int rowNum = (int) record.getRecordNumber() + 1;
                try {
                    String profileCode = getField(record, "profile_code");
                    if (profileCode == null || profileCode.isBlank()) {
                        errors.add(error(rowNum, "profile_code", "Profile code is required"));
                        continue;
                    }
                    String profileName = getField(record, "profile_name");
                    if (profileName == null || profileName.isBlank()) {
                        errors.add(error(rowNum, "profile_name", "Profile name is required"));
                        continue;
                    }
                    String protocol = getField(record, "protocol");
                    if (protocol == null || protocol.isBlank()) {
                        errors.add(error(rowNum, "protocol", "Protocol is required"));
                        continue;
                    }

                    // Upsert by profile_code
                    Optional<MachineProfileEntity> existing = profileRepo.findByProfileCode(profileCode);
                    MachineProfileEntity profile;
                    if (existing.isPresent()) {
                        profile = existing.get();
                        profile.setProfileName(profileName);
                        profile.setProtocol(protocol);
                        profile.setVendor(getField(record, "vendor"));
                        profile.setModel(getField(record, "model"));
                        profile.setDescription(getField(record, "description"));
                    } else {
                        profile = MachineProfileEntity.builder()
                                .profileCode(profileCode)
                                .profileName(profileName)
                                .protocol(protocol)
                                .vendor(getField(record, "vendor"))
                                .model(getField(record, "model"))
                                .description(getField(record, "description"))
                                .build();
                    }
                    profileRepo.save(profile);
                    successRows++;
                } catch (Exception e) {
                    errors.add(error(rowNum, "general", "Unexpected error: " + e.getMessage()));
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("IMPORT_PARSE_ERROR", "Failed to parse CSV file: " + e.getMessage());
        }

        MachineImportBatchEntity batch = MachineImportBatchEntity.builder()
                .fileName(file.getOriginalFilename())
                .importType("PROFILES")
                .status(errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_ERRORS")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errorSummary(errors.isEmpty() ? null : errors.stream()
                        .map(e -> "Row " + e.getRow() + ": " + e.getMessage())
                        .collect(Collectors.joining("; ")))
                .build();
        batch = batchRepo.save(batch);

        return MachineImportResultResponse.builder()
                .batchId(batch.getId())
                .importType("PROFILES")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    @Override
    @Transactional
    public MachineImportResultResponse importMappings(MultipartFile file) {
        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;
        MachineImportBatchEntity batch;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            requireMappingHeaders(parser.getHeaderMap());

            List<CSVRecord> records = parser.getRecords();
            totalRows = records.size();

            Set<String> profileCodesInFile = records.stream()
                    .map(r -> getField(r, "profile_code"))
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (profileCodesInFile.size() != 1) {
                throw new AppException("MAPPING_FILE_PROFILE_REQUIRED",
                        "Mappings file must contain exactly one profile_code");
            }

            String fileProfileCode = profileCodesInFile.iterator().next();
            MachineProfileEntity profile = profileRepo.findByProfileCode(fileProfileCode)
                    .orElseThrow(() -> new AppException("PROFILE_NOT_FOUND", "Profile not found: " + fileProfileCode));

            batch = MachineImportBatchEntity.builder()
                    .fileName(file.getOriginalFilename())
                    .importType("MAPPINGS")
                    .status("PENDING")
                    .totalRows(totalRows)
                    .successRows(0)
                    .failedRows(0)
                    .profileCode(fileProfileCode)
                    .profileId(profile.getId())
                    .uploadedBy("system")
                    .build();
            batch = batchRepo.save(batch);

            Set<String> seenKeys = new HashSet<>();
            for (CSVRecord record : records) {
                int rowNum = (int) record.getRecordNumber() + 1;
                try {
                    String profileCode = getField(record, "profile_code");
                    if (profileCode == null || profileCode.isBlank()) {
                        errors.add(error(rowNum, "profile_code", "Profile code is required"));
                        continue;
                    }
                    if (!fileProfileCode.equalsIgnoreCase(profileCode)) {
                        errors.add(error(rowNum, "profile_code", "All rows must use one profile_code: " + fileProfileCode));
                        continue;
                    }

                    String logicalKey = getField(record, "logical_key");
                    if (logicalKey == null || logicalKey.isBlank()) {
                        errors.add(error(rowNum, "logical_key", "Logical key is required"));
                        continue;
                    }
                    String normalizedLogicalKey = normalizeLogicalKey(logicalKey);
                    if (seenKeys.contains(normalizedLogicalKey)) {
                        errors.add(error(rowNum, "logical_key", "Duplicate key in same file: " + logicalKey));
                        continue;
                    }
                    seenKeys.add(normalizedLogicalKey);

                    String area = getField(record, "area");
                    Integer addressStart = parseIntFieldAny(record, "address", "address_start");
                    String dataType = getField(record, "data_type");

                    if (area == null || area.isBlank()) {
                        errors.add(error(rowNum, "area", "Area is required"));
                        continue;
                    }
                    if (addressStart == null) {
                        errors.add(error(rowNum, "address", "Address is required"));
                        continue;
                    }
                    if (dataType == null || dataType.isBlank()) {
                        errors.add(error(rowNum, "data_type", "Data type is required"));
                        continue;
                    }

                    MachineProfileMappingEntity mapping = MachineProfileMappingEntity.builder()
                            .profileId(profile.getId())
                            .mappingFileId(batch.getId())
                            .logicalKey(logicalKey)
                            .area(area)
                            .addressStart(addressStart)
                            .addressEnd(parseIntFieldAny(record, "address_end"))
                            .dataType(dataType)
                            .scaleFactor(parseDoubleFieldAny(record, 1.0, "scale", "scale_factor"))
                            .unit(getField(record, "unit"))
                            .byteOrder(getFieldOrDefault(record, "byte_order", "BIG"))
                            .wordOrder(getFieldOrDefault(record, "word_order", "BIG"))
                            .isRequired(parseBoolFieldAny(record, "required", "is_required") != null
                                    ? parseBoolFieldAny(record, "required", "is_required") : true)
                            .description(getField(record, "description"))
                            .build();
                    mappingRepo.save(mapping);
                    successRows++;
                } catch (Exception e) {
                    errors.add(error(rowNum, "general", "Unexpected error: " + e.getMessage()));
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("IMPORT_PARSE_ERROR", "Failed to parse CSV file: " + e.getMessage());
        }

        batch.setStatus(errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
        batch.setSuccessRows(successRows);
        batch.setFailedRows(errors.size());
        batch.setErrorSummary(errors.isEmpty() ? null : errors.stream()
                .map(e -> "Row " + e.getRow() + ": " + e.getMessage())
                .collect(Collectors.joining("; ")));
        batch = batchRepo.save(batch);

        return MachineImportResultResponse.builder()
                .batchId(batch.getId())
                .importType("MAPPINGS")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    @Override
    public MachineImportResultResponse validateFile(MultipartFile file, String importType) {
        // Dry-run: parse and validate without persisting
        switch (importType.toLowerCase()) {
            case "machines":
                return validateMachinesCsv(file);
            case "profiles":
                return validateProfilesCsv(file);
            case "mappings":
                return validateMappingsCsv(file);
            default:
                throw new AppException("INVALID_IMPORT_TYPE", "Unsupported import type: " + importType);
        }
    }

    // ---- Validate helpers (dry run) ----

    private MachineImportResultResponse validateMachinesCsv(MultipartFile file) {
        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            requireHeaders(parser.getHeaderMap(), REQUIRED_MACHINE_HEADERS, "machines");

            Set<String> seenCodes = new HashSet<>();
            List<CSVRecord> records = parser.getRecords();
            totalRows = records.size();

            for (CSVRecord record : records) {
                int rowNum = (int) record.getRecordNumber() + 1;
                String code = getField(record, "machine_code");
                if (code == null || code.isBlank()) {
                    errors.add(error(rowNum, "machine_code", "Machine code is required"));
                    continue;
                }
                if (seenCodes.contains(code)) {
                    errors.add(error(rowNum, "machine_code", "Duplicate code in file: " + code));
                }
                seenCodes.add(code);

                String name = getField(record, "machine_name");
                if (name == null || name.isBlank()) {
                    errors.add(error(rowNum, "machine_name", "Machine name is required"));
                }

                String protocol = getField(record, "protocol");
                if (protocol != null && !protocol.isBlank() && !SUPPORTED_PROTOCOLS.contains(protocol.toLowerCase())) {
                    errors.add(error(rowNum, "protocol", "Unsupported protocol: " + protocol));
                }

                String profileCode = getField(record, "profile_code");
                if (profileCode == null || profileCode.isBlank()) {
                    errors.add(error(rowNum, "profile_code", "Profile code is required"));
                } else if (profileRepo.findByProfileCode(profileCode).isEmpty()) {
                    errors.add(error(rowNum, "profile_code", "Profile not found: " + profileCode));
                }

                if ("modbus-tcp".equalsIgnoreCase(protocol)) {
                    if (isBlank(getField(record, "host"))) {
                        errors.add(error(rowNum, "host", "Host is required for modbus-tcp"));
                    }
                    Integer port = parseIntField(record, "port");
                    if (port == null || port <= 0) {
                        errors.add(error(rowNum, "port", "Port must be greater than 0"));
                    }
                    Integer unitId = parseIntField(record, "unit_id");
                    if (unitId == null || unitId <= 0) {
                        errors.add(error(rowNum, "unit_id", "Unit ID must be greater than 0"));
                    }
                }
            }
        } catch (Exception e) {
            throw new AppException("IMPORT_PARSE_ERROR", "Failed to parse CSV file: " + e.getMessage());
        }

        return MachineImportResultResponse.builder()
                .importType("MACHINES")
                .totalRows(totalRows)
                .successRows(totalRows - errors.size())
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    private MachineImportResultResponse validateProfilesCsv(MultipartFile file) {
        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            requireHeaders(parser.getHeaderMap(), REQUIRED_PROFILE_HEADERS, "profiles");

            List<CSVRecord> records = parser.getRecords();
            totalRows = records.size();

            for (CSVRecord record : records) {
                int rowNum = (int) record.getRecordNumber() + 1;
                if (isBlank(getField(record, "profile_code"))) {
                    errors.add(error(rowNum, "profile_code", "Profile code is required"));
                }
                if (isBlank(getField(record, "profile_name"))) {
                    errors.add(error(rowNum, "profile_name", "Profile name is required"));
                }
                if (isBlank(getField(record, "protocol"))) {
                    errors.add(error(rowNum, "protocol", "Protocol is required"));
                }
            }
        } catch (Exception e) {
            throw new AppException("IMPORT_PARSE_ERROR", "Failed to parse CSV file: " + e.getMessage());
        }

        return MachineImportResultResponse.builder()
                .importType("PROFILES")
                .totalRows(totalRows)
                .successRows(totalRows - errors.size())
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    private MachineImportResultResponse validateMappingsCsv(MultipartFile file) {
        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            requireMappingHeaders(parser.getHeaderMap());

            List<CSVRecord> records = parser.getRecords();
            totalRows = records.size();

            Set<String> profileCodesInFile = records.stream()
                    .map(r -> getField(r, "profile_code"))
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (profileCodesInFile.size() != 1) {
                errors.add(error(1, "profile_code", "Mappings file must contain exactly one profile_code"));
            }

            Map<String, Set<String>> profileKeyMap = new HashMap<>();

            for (CSVRecord record : records) {
                int rowNum = (int) record.getRecordNumber() + 1;
                String profileCode = getField(record, "profile_code");
                String logicalKey = getField(record, "logical_key");

                if (isBlank(profileCode)) {
                    errors.add(error(rowNum, "profile_code", "Profile code is required"));
                    continue;
                }
                if (isBlank(logicalKey)) {
                    errors.add(error(rowNum, "logical_key", "Logical key is required"));
                    continue;
                }

                Set<String> keys = profileKeyMap.computeIfAbsent(profileCode, k -> new HashSet<>());
                String normalizedLogicalKey = normalizeLogicalKey(logicalKey);
                if (keys.contains(normalizedLogicalKey)) {
                    errors.add(error(rowNum, "logical_key", "Duplicate key in same profile: " + logicalKey));
                }
                keys.add(normalizedLogicalKey);

                if (isBlank(getField(record, "area"))) {
                    errors.add(error(rowNum, "area", "Area is required"));
                }
                if (parseIntFieldAny(record, "address", "address_start") == null) {
                    errors.add(error(rowNum, "address", "Address is required"));
                }
                if (isBlank(getField(record, "data_type"))) {
                    errors.add(error(rowNum, "data_type", "Data type is required"));
                }
            }
        } catch (Exception e) {
            throw new AppException("IMPORT_PARSE_ERROR", "Failed to parse CSV file: " + e.getMessage());
        }

        return MachineImportResultResponse.builder()
                .importType("MAPPINGS")
                .totalRows(totalRows)
                .successRows(totalRows - errors.size())
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    // ---- CSV field helpers ----

    private String getField(CSVRecord record, String name) {
        try {
            if (!record.isMapped(name)) return null;
            String val = record.get(name);
            return (val != null && !val.isBlank()) ? val.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getFieldOrDefault(CSVRecord record, String name, String defaultValue) {
        String val = getField(record, name);
        return val != null ? val : defaultValue;
    }

    private Integer parseIntFieldAny(CSVRecord record, String... names) {
        for (String name : names) {
            Integer val = parseIntField(record, name);
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    private Double parseDoubleFieldAny(CSVRecord record, double defaultValue, String... names) {
        for (String name : names) {
            String val = getField(record, name);
            if (val == null) {
                continue;
            }
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Boolean parseBoolFieldAny(CSVRecord record, String... names) {
        for (String name : names) {
            Boolean val = parseBoolField(record, name);
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    private Integer parseIntField(CSVRecord record, String name) {
        String val = getField(record, name);
        if (val == null) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDoubleField(CSVRecord record, String name, double defaultValue) {
        String val = getField(record, name);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean parseBoolField(CSVRecord record, String name) {
        String val = getField(record, name);
        if (val == null) return null;
        return "true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val);
    }

    private boolean isBlank(String val) {
        return val == null || val.isBlank();
    }

    private void requireHeaders(Map<String, Integer> headers, Set<String> requiredHeaders, String importType) {
        List<String> missing = requiredHeaders.stream()
                .filter(h -> !headers.containsKey(h))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new AppException("IMPORT_INVALID_HEADER",
                    "Missing required " + importType + " CSV headers: " + String.join(", ", missing));
        }
    }

    private void requireMappingHeaders(Map<String, Integer> headers) {
        requireHeaders(headers, REQUIRED_MAPPING_BASE_HEADERS, "mappings");
        boolean hasAddress = headers.containsKey("address") || headers.containsKey("address_start");
        boolean hasScale = headers.containsKey("scale") || headers.containsKey("scale_factor");
        boolean hasRequired = headers.containsKey("required") || headers.containsKey("is_required");

        List<String> missing = new ArrayList<>();
        if (!hasAddress) {
            missing.add("address");
        }
        if (!hasScale) {
            missing.add("scale");
        }
        if (!hasRequired) {
            missing.add("required");
        }
        if (!missing.isEmpty()) {
            throw new AppException("IMPORT_INVALID_HEADER",
                    "Missing required mappings CSV headers: " + String.join(", ", missing));
        }
    }

    private ImportRowError error(int row, String field, String message) {
        return ImportRowError.builder().row(row).field(field).message(message).build();
    }

    // ========== JSON IMPORT METHODS ==========

    @Override
    @Transactional
    public MachineImportResultResponse importMachinesJson(MachineConfigImportRequest request, ImportMode mode) {
        List<ImportRowError> errors = new ArrayList<>();
        int successRows = 0;
        Set<UUID> machineIdsToConnect = new LinkedHashSet<>();

        if (request.getMachines() == null || request.getMachines().isEmpty()) {
            return MachineImportResultResponse.builder()
                    .importType("MACHINES")
                    .totalRows(0)
                    .successRows(0)
                    .failedRows(0)
                    .errors(errors)
                    .build();
        }

        int totalRows = request.getMachines().size();
        Set<String> seenCodes = new HashSet<>();

        for (int rowNum = 1; rowNum <= request.getMachines().size(); rowNum++) {
            try {
                MachineConfigImportRequest.MachineRecord record = request.getMachines().get(rowNum - 1);

                String code = record.getMachineCode();
                if (code == null || code.isBlank()) {
                    errors.add(error(rowNum, "machine_code", "Machine code is required"));
                    continue;
                }
                if (seenCodes.contains(code)) {
                    errors.add(error(rowNum, "machine_code", "Duplicate code in request: " + code));
                    continue;
                }
                seenCodes.add(code);

                String name = record.getMachineName();
                if (name == null || name.isBlank()) {
                    errors.add(error(rowNum, "machine_name", "Machine name is required"));
                    continue;
                }

                String protocol = record.getProtocol();
                if (protocol != null && !protocol.isBlank() && !SUPPORTED_PROTOCOLS.contains(protocol.toLowerCase())) {
                    errors.add(error(rowNum, "protocol", "Unsupported protocol: " + protocol));
                    continue;
                }

                String profileCode = record.getProfileCode();
                if (profileCode == null || profileCode.isBlank()) {
                    errors.add(error(rowNum, "profile_code", "Profile code is required"));
                    continue;
                }
                UUID profileId = null;
                Optional<MachineProfileEntity> profileOpt = profileRepo.findByProfileCode(profileCode);
                if (profileOpt.isEmpty()) {
                    errors.add(error(rowNum, "profile_code", "Profile not found: " + profileCode));
                    continue;
                }
                profileId = profileOpt.get().getId();

                String host = record.getHost();
                Integer port = record.getPort();
                if (port == null && "modbus-tcp".equalsIgnoreCase(protocol)) {
                    port = 502;
                }
                Integer unitId = record.getUnitId();
                if (unitId == null && "modbus-tcp".equalsIgnoreCase(protocol)) {
                    unitId = 1;
                }
                Integer pollIntervalMs = record.getPollIntervalMs();
                Boolean autoConnect = record.getAutoConnect();

                if ("modbus-tcp".equalsIgnoreCase(protocol)) {
                    if (host == null || host.isBlank()) {
                        errors.add(error(rowNum, "host", "Host is required for modbus-tcp"));
                        continue;
                    }
                    if (port == null || port <= 0) {
                        errors.add(error(rowNum, "port", "Port must be greater than 0"));
                        continue;
                    }
                    if (unitId == null || unitId <= 0) {
                        errors.add(error(rowNum, "unit_id", "Unit ID must be greater than 0"));
                        continue;
                    }
                }

                Optional<MachineEntity> existingOpt = machineRepo.findByCode(code);
                if (existingOpt.isPresent()) {
                    if (mode == ImportMode.CREATE_ONLY) {
                        errors.add(error(rowNum, "machine_code", "Machine already exists: " + code));
                        continue;
                    }
                    // UPSERT - update existing
                    MachineEntity existing = existingOpt.get();
                    existing.setName(name);
                    if (record.getType() != null) existing.setType(record.getType());
                    if (record.getVendor() != null) existing.setVendor(record.getVendor());
                    if (record.getModel() != null) existing.setModel(record.getModel());
                    if (protocol != null) existing.setProtocol(protocol);
                    if (host != null) existing.setHost(host);
                    if (port != null) existing.setPort(port);
                    if (unitId != null) existing.setUnitId(unitId);
                    if (pollIntervalMs != null) existing.setPollIntervalMs(pollIntervalMs);
                    if (autoConnect != null) existing.setAutoConnect(autoConnect);
                    if (profileId != null) existing.setProfileId(profileId);
                    if (record.getLineId() != null) existing.setLineId(record.getLineId());
                    if (record.getPlantId() != null) existing.setPlantId(record.getPlantId());
                    machineRepo.save(existing);
                    if (Boolean.TRUE.equals(existing.getIsEnabled()) && Boolean.TRUE.equals(existing.getAutoConnect())) {
                        machineIdsToConnect.add(existing.getId());
                    }
                } else {
                    MachineEntity machine = MachineEntity.builder()
                            .code(code)
                            .name(name)
                            .type(record.getType() != null ? record.getType() : "CNC_MACHINE")
                            .vendor(record.getVendor() != null ? record.getVendor() : "UNKNOWN")
                            .model(record.getModel())
                            .lineId(record.getLineId())
                            .plantId(record.getPlantId())
                            .status("OFFLINE")
                            .protocol(protocol)
                            .host(host)
                            .port(port)
                            .unitId(unitId != null ? unitId : 1)
                            .pollIntervalMs(pollIntervalMs != null ? pollIntervalMs : 1000)
                            .isEnabled(true)
                            .autoConnect(autoConnect != null && autoConnect)
                            .profileId(profileId)
                            .connectionMode("MANUAL")
                            .build();
                    machine = machineRepo.save(machine);
                    if (Boolean.TRUE.equals(machine.getIsEnabled()) && Boolean.TRUE.equals(machine.getAutoConnect())) {
                        machineIdsToConnect.add(machine.getId());
                    }
                }
                successRows++;
            } catch (Exception e) {
                errors.add(error(rowNum, "general", "Unexpected error: " + e.getMessage()));
            }
        }

        // Save batch record
        MachineImportBatchEntity batch = MachineImportBatchEntity.builder()
                .fileName("json-machines-import")
                .importType("MACHINES")
                .status(errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_ERRORS")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errorSummary(errors.isEmpty() ? null : errors.stream()
                        .map(e -> "Row " + e.getRow() + ": " + e.getMessage())
                        .collect(Collectors.joining("; ")))
                .build();
        batch = batchRepo.save(batch);

        scheduleConnectAfterCommit(machineIdsToConnect);

        return MachineImportResultResponse.builder()
                .batchId(batch.getId())
                .importType("MACHINES")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    @Override
    @Transactional
    public MachineImportResultResponse importProfilesJson(MachineProfileImportRequest request) {
        List<ImportRowError> errors = new ArrayList<>();
        int successRows = 0;

        if (request.getProfiles() == null || request.getProfiles().isEmpty()) {
            return MachineImportResultResponse.builder()
                    .importType("PROFILES")
                    .totalRows(0)
                    .successRows(0)
                    .failedRows(0)
                    .errors(errors)
                    .build();
        }

        int totalRows = request.getProfiles().size();

        for (int rowNum = 1; rowNum <= request.getProfiles().size(); rowNum++) {
            try {
                MachineProfileImportRequest.ProfileRecord record = request.getProfiles().get(rowNum - 1);

                String profileCode = record.getProfileCode();
                if (profileCode == null || profileCode.isBlank()) {
                    errors.add(error(rowNum, "profile_code", "Profile code is required"));
                    continue;
                }
                String profileName = record.getProfileName();
                if (profileName == null || profileName.isBlank()) {
                    errors.add(error(rowNum, "profile_name", "Profile name is required"));
                    continue;
                }
                String protocol = record.getProtocol();
                if (protocol == null || protocol.isBlank()) {
                    errors.add(error(rowNum, "protocol", "Protocol is required"));
                    continue;
                }

                // Upsert by profile_code
                Optional<MachineProfileEntity> existing = profileRepo.findByProfileCode(profileCode);
                MachineProfileEntity profile;
                if (existing.isPresent()) {
                    profile = existing.get();
                    profile.setProfileName(profileName);
                    profile.setProtocol(protocol);
                    profile.setVendor(record.getVendor());
                    profile.setModel(record.getModel());
                    profile.setDescription(record.getDescription());
                } else {
                    profile = MachineProfileEntity.builder()
                            .profileCode(profileCode)
                            .profileName(profileName)
                            .protocol(protocol)
                            .vendor(record.getVendor())
                            .model(record.getModel())
                            .description(record.getDescription())
                            .build();
                }
                profileRepo.save(profile);
                successRows++;
            } catch (Exception e) {
                errors.add(error(rowNum, "general", "Unexpected error: " + e.getMessage()));
            }
        }

        MachineImportBatchEntity batch = MachineImportBatchEntity.builder()
                .fileName("json-profiles-import")
                .importType("PROFILES")
                .status(errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_ERRORS")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errorSummary(errors.isEmpty() ? null : errors.stream()
                        .map(e -> "Row " + e.getRow() + ": " + e.getMessage())
                        .collect(Collectors.joining("; ")))
                .build();
        batch = batchRepo.save(batch);

        return MachineImportResultResponse.builder()
                .batchId(batch.getId())
                .importType("PROFILES")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    @Override
    @Transactional
    public MachineImportResultResponse importMappingsJson(MachineMappingImportRequest request) {
        List<ImportRowError> errors = new ArrayList<>();
        int successRows = 0;

        if (request.getMappings() == null || request.getMappings().isEmpty()) {
            return MachineImportResultResponse.builder()
                    .importType("MAPPINGS")
                    .totalRows(0)
                    .successRows(0)
                    .failedRows(0)
                    .errors(errors)
                    .build();
        }

        int totalRows = request.getMappings().size();

        Set<String> profileCodesInRequest = request.getMappings().stream()
                .map(MachineMappingImportRequest.MappingRecord::getProfileCode)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (profileCodesInRequest.size() != 1) {
            throw new AppException("MAPPING_FILE_PROFILE_REQUIRED",
                    "JSON mappings import must contain exactly one profileCode");
        }

        String fileProfileCode = profileCodesInRequest.iterator().next();
        MachineProfileEntity fileProfile = profileRepo.findByProfileCode(fileProfileCode)
                .orElseThrow(() -> new AppException("PROFILE_NOT_FOUND", "Profile not found: " + fileProfileCode));

        MachineImportBatchEntity batch = MachineImportBatchEntity.builder()
                .fileName("json-mappings-import")
                .importType("MAPPINGS")
                .status("PENDING")
                .totalRows(totalRows)
                .successRows(0)
                .failedRows(0)
                .profileCode(fileProfileCode)
                .profileId(fileProfile.getId())
                .uploadedBy("system")
                .build();
        batch = batchRepo.save(batch);

        int recordIndex = 0;
        Set<String> seenKeys = new HashSet<>();
        for (MachineMappingImportRequest.MappingRecord record : request.getMappings()) {
            recordIndex++;
            try {
                String profileCode = record.getProfileCode();
                if (profileCode == null || profileCode.isBlank()) {
                    errors.add(error(recordIndex, "profile_code", "Profile code is required"));
                    continue;
                }
                if (!fileProfileCode.equalsIgnoreCase(profileCode)) {
                    errors.add(error(recordIndex, "profile_code", "All rows must use one profileCode: " + fileProfileCode));
                    continue;
                }

                String logicalKey = record.getLogicalKey();
                if (logicalKey == null || logicalKey.isBlank()) {
                    errors.add(error(recordIndex, "logical_key", "Logical key is required"));
                    continue;
                }
                String normalizedLogicalKey = normalizeLogicalKey(logicalKey);
                if (seenKeys.contains(normalizedLogicalKey)) {
                    errors.add(error(recordIndex, "logical_key", "Duplicate key in same file: " + logicalKey));
                    continue;
                }
                seenKeys.add(normalizedLogicalKey);

                String area = record.getArea();
                Integer addressStart = record.getAddressStart() != null ? record.getAddressStart() : record.getAddress();
                String dataType = record.getDataType();

                if (area == null || area.isBlank()) {
                    errors.add(error(recordIndex, "area", "Area is required"));
                    continue;
                }
                if (addressStart == null) {
                    errors.add(error(recordIndex, "address", "Address is required"));
                    continue;
                }
                if (dataType == null || dataType.isBlank()) {
                    errors.add(error(recordIndex, "data_type", "Data type is required"));
                    continue;
                }

                Double scaleFactor = record.getScaleFactor() != null ? record.getScaleFactor() : record.getScale();
                if (scaleFactor == null) {
                    scaleFactor = 1.0;
                }

                Boolean isRequired = record.getIsRequired() != null ? record.getIsRequired() : record.getRequired();
                if (isRequired == null) {
                    isRequired = true;
                }

                MachineProfileMappingEntity mapping = MachineProfileMappingEntity.builder()
                        .profileId(fileProfile.getId())
                        .mappingFileId(batch.getId())
                        .logicalKey(logicalKey)
                        .area(area)
                        .addressStart(addressStart)
                        .addressEnd(record.getAddressEnd())
                        .dataType(dataType)
                        .scaleFactor(scaleFactor)
                        .unit(record.getUnit())
                        .byteOrder(record.getByteOrder() != null ? record.getByteOrder() : "BIG")
                        .wordOrder(record.getWordOrder() != null ? record.getWordOrder() : "BIG")
                        .isRequired(isRequired)
                        .description(record.getDescription())
                        .build();
                mappingRepo.save(mapping);
                successRows++;
            } catch (Exception e) {
                errors.add(error(recordIndex, "general", "Unexpected error: " + e.getMessage()));
            }
        }

        batch.setStatus(errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
        batch.setSuccessRows(successRows);
        batch.setFailedRows(errors.size());
        batch.setErrorSummary(errors.isEmpty() ? null : errors.stream()
                .map(e -> "Row " + e.getRow() + ": " + e.getMessage())
                .collect(Collectors.joining("; ")));
        batch = batchRepo.save(batch);

        return MachineImportResultResponse.builder()
                .batchId(batch.getId())
                .importType("MAPPINGS")
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(errors.size())
                .errors(errors)
                .build();
    }

    @Override
    public List<ImportFileResponse> listImportFiles(String importType, String profileCode) {
        List<MachineImportBatchEntity> batches = batchRepo.findAll().stream()
                .sorted(Comparator.comparing(MachineImportBatchEntity::getCreatedAt).reversed())
                .collect(Collectors.toList());

        if (importType != null && !importType.isBlank()) {
            batches = batches.stream()
                    .filter(b -> matchesImportType(b.getImportType(), importType))
                    .collect(Collectors.toList());
        }

        if (profileCode != null && !profileCode.isBlank()) {
            batches = batches.stream()
                    .filter(b -> b.getProfileCode() != null && profileCode.equalsIgnoreCase(b.getProfileCode()))
                    .collect(Collectors.toList());
        }

        return batches.stream()
                .map(this::mapToImportFileResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ImportFileResponse getImportFileDetail(UUID fileId) {
        MachineImportBatchEntity batch = batchRepo.findById(fileId)
                .orElseThrow(() -> new AppException("IMPORT_FILE_NOT_FOUND", "Import file not found: " + fileId));
        return mapToImportFileResponse(batch);
    }

    @Override
    public ProfileMappingValidationResponse validateProfileMapping(UUID profileId, UUID mappingFileId) {
        // Get profile
        MachineProfileEntity profile = profileRepo.findById(profileId)
                .orElseThrow(() -> new AppException("PROFILE_NOT_FOUND", "Profile not found: " + profileId));

        // Get import file (mapping batch)
        MachineImportBatchEntity importFile = batchRepo.findById(mappingFileId)
                .orElseThrow(() -> new AppException("IMPORT_FILE_NOT_FOUND", "Mapping file not found: " + mappingFileId));

        // Validate file is MAPPING type and COMPLETED
        if (!matchesImportType(importFile.getImportType(), "MAPPING")) {
            return ProfileMappingValidationResponse.builder()
                    .valid(false)
                    .profileId(profileId)
                    .profileCode(profile.getProfileCode())
                    .mappingFileId(mappingFileId)
                    .mappingProfileCode(importFile.getProfileCode())
                    .message("Import file is not a MAPPING file")
                    .build();
        }

        if (!"COMPLETED".equalsIgnoreCase(importFile.getStatus()) && !"COMPLETED_WITH_ERRORS".equalsIgnoreCase(importFile.getStatus())) {
            return ProfileMappingValidationResponse.builder()
                    .valid(false)
                    .profileId(profileId)
                    .profileCode(profile.getProfileCode())
                    .mappingFileId(mappingFileId)
                    .mappingProfileCode(importFile.getProfileCode())
                    .message("Import file is not ready (status: " + importFile.getStatus() + ")")
                    .build();
        }

        // All validations passed (profile and mapping are treated as independently selectable)
        return ProfileMappingValidationResponse.builder()
                .valid(true)
                .profileId(profileId)
                .profileCode(profile.getProfileCode())
                .mappingFileId(mappingFileId)
                .mappingProfileCode(importFile.getProfileCode())
                .message("Profile and mapping file are compatible")
                .build();
    }

    private boolean matchesImportType(String storedType, String requestedType) {
        if (storedType == null || requestedType == null) {
            return false;
        }
        String s = storedType.trim().toUpperCase(Locale.ROOT);
        String r = requestedType.trim().toUpperCase(Locale.ROOT);
        if (s.equals(r)) {
            return true;
        }
        return ("MAPPING".equals(s) && "MAPPINGS".equals(r))
                || ("MAPPINGS".equals(s) && "MAPPING".equals(r));
    }

    private String normalizeLogicalKey(String logicalKey) {
        return logicalKey == null ? null : logicalKey.trim().toLowerCase(Locale.ROOT);
    }

    private ImportFileResponse mapToImportFileResponse(MachineImportBatchEntity batch) {
        return ImportFileResponse.builder()
                .fileId(batch.getId())
                .fileName(batch.getFileName())
                .importType(batch.getImportType())
                .batchId(batch.getId())
                .uploadedAt(batch.getCreatedAt())
                .uploadedBy(batch.getUploadedBy() != null ? batch.getUploadedBy() : "system")
                .status(batch.getStatus())
                .profileCode(batch.getProfileCode())
                .profileId(batch.getProfileId())
                .totalRows(batch.getTotalRows())
                .successRows(batch.getSuccessRows())
                .failedRows(batch.getFailedRows())
                .build();
    }
}

