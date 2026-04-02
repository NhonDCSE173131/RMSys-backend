package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.MachineImportResultResponse;
import com.rmsys.backend.api.response.MachineImportResultResponse.ImportRowError;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final MachineRepository machineRepo;
    private final MachineProfileRepository profileRepo;
    private final MachineProfileMappingRepository mappingRepo;
    private final MachineImportBatchRepository batchRepo;

    @Override
    @Transactional
    public MachineImportResultResponse importMachines(MultipartFile file, ImportMode mode) {
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
                    UUID profileId = null;
                    if (profileCode != null && !profileCode.isBlank()) {
                        Optional<MachineProfileEntity> profileOpt = profileRepo.findByProfileCode(profileCode);
                        if (profileOpt.isEmpty()) {
                            errors.add(error(rowNum, "profile_code", "Profile not found: " + profileCode));
                            continue;
                        }
                        profileId = profileOpt.get().getId();
                    }

                    String host = getField(record, "host");
                    Integer port = parseIntField(record, "port");
                    if (port == null && "modbus-tcp".equalsIgnoreCase(protocol)) {
                        port = 502;
                    }
                    Integer unitId = parseIntField(record, "unit_id");
                    Integer pollIntervalMs = parseIntField(record, "poll_interval_ms");
                    Boolean autoConnect = parseBoolField(record, "auto_connect");

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
                                .autoConnect(autoConnect != null && autoConnect)
                                .profileId(profileId)
                                .connectionMode("MANUAL")
                                .build();
                        machineRepo.save(machine);
                    }
                    successRows++;
                } catch (Exception e) {
                    errors.add(error(rowNum, "general", "Unexpected error: " + e.getMessage()));
                }
            }
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

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<CSVRecord> records = parser.getRecords();
            totalRows = records.size();

            // Group by profile_code, then replace all mappings per profile
            Map<String, List<CSVRecord>> grouped = new LinkedHashMap<>();
            for (CSVRecord record : records) {
                String profileCode = getField(record, "profile_code");
                grouped.computeIfAbsent(profileCode, k -> new ArrayList<>()).add(record);
            }

            for (Map.Entry<String, List<CSVRecord>> entry : grouped.entrySet()) {
                String profileCode = entry.getKey();
                if (profileCode == null || profileCode.isBlank()) {
                    for (CSVRecord r : entry.getValue()) {
                        errors.add(error((int) r.getRecordNumber() + 1, "profile_code", "Profile code is required"));
                    }
                    continue;
                }

                Optional<MachineProfileEntity> profileOpt = profileRepo.findByProfileCode(profileCode);
                if (profileOpt.isEmpty()) {
                    for (CSVRecord r : entry.getValue()) {
                        errors.add(error((int) r.getRecordNumber() + 1, "profile_code", "Profile not found: " + profileCode));
                    }
                    continue;
                }

                UUID profileId = profileOpt.get().getId();
                // Delete existing mappings for this profile
                mappingRepo.deleteByProfileId(profileId);

                Set<String> seenKeys = new HashSet<>();
                for (CSVRecord record : entry.getValue()) {
                    int rowNum = (int) record.getRecordNumber() + 1;
                    try {
                        String logicalKey = getField(record, "logical_key");
                        if (logicalKey == null || logicalKey.isBlank()) {
                            errors.add(error(rowNum, "logical_key", "Logical key is required"));
                            continue;
                        }
                        if (seenKeys.contains(logicalKey)) {
                            errors.add(error(rowNum, "logical_key", "Duplicate key in same profile: " + logicalKey));
                            continue;
                        }
                        seenKeys.add(logicalKey);

                        String area = getField(record, "area");
                        Integer addressStart = parseIntField(record, "address_start");
                        String dataType = getField(record, "data_type");

                        if (area == null || area.isBlank()) {
                            errors.add(error(rowNum, "area", "Area is required"));
                            continue;
                        }
                        if (addressStart == null) {
                            errors.add(error(rowNum, "address_start", "Address start is required"));
                            continue;
                        }
                        if (dataType == null || dataType.isBlank()) {
                            errors.add(error(rowNum, "data_type", "Data type is required"));
                            continue;
                        }

                        MachineProfileMappingEntity mapping = MachineProfileMappingEntity.builder()
                                .profileId(profileId)
                                .logicalKey(logicalKey)
                                .area(area)
                                .addressStart(addressStart)
                                .addressEnd(parseIntField(record, "address_end"))
                                .dataType(dataType)
                                .scaleFactor(parseDoubleField(record, "scale_factor", 1.0))
                                .unit(getField(record, "unit"))
                                .byteOrder(getFieldOrDefault(record, "byte_order", "BIG"))
                                .wordOrder(getFieldOrDefault(record, "word_order", "BIG"))
                                .isRequired(parseBoolField(record, "is_required") != null ? parseBoolField(record, "is_required") : true)
                                .description(getField(record, "description"))
                                .build();
                        mappingRepo.save(mapping);
                        successRows++;
                    } catch (Exception e) {
                        errors.add(error(rowNum, "general", "Unexpected error: " + e.getMessage()));
                    }
                }
            }
        } catch (Exception e) {
            throw new AppException("IMPORT_PARSE_ERROR", "Failed to parse CSV file: " + e.getMessage());
        }

        MachineImportBatchEntity batch = MachineImportBatchEntity.builder()
                .fileName(file.getOriginalFilename())
                .importType("MAPPINGS")
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
                if (profileCode != null && !profileCode.isBlank()) {
                    if (profileRepo.findByProfileCode(profileCode).isEmpty()) {
                        errors.add(error(rowNum, "profile_code", "Profile not found: " + profileCode));
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

            List<CSVRecord> records = parser.getRecords();
            totalRows = records.size();

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
                if (keys.contains(logicalKey)) {
                    errors.add(error(rowNum, "logical_key", "Duplicate key in same profile: " + logicalKey));
                }
                keys.add(logicalKey);

                if (isBlank(getField(record, "area"))) {
                    errors.add(error(rowNum, "area", "Area is required"));
                }
                if (parseIntField(record, "address_start") == null) {
                    errors.add(error(rowNum, "address_start", "Address start is required"));
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

    private ImportRowError error(int row, String field, String message) {
        return ImportRowError.builder().row(row).field(field).message(message).build();
    }
}

