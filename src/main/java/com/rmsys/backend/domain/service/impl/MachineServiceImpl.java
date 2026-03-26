package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.MachineDetailResponse;
import com.rmsys.backend.api.response.MachineSnapshotResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineTelemetryEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.repository.MachineTelemetryRepository;
import com.rmsys.backend.domain.service.MachineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MachineServiceImpl implements MachineService {

    private final MachineRepository machineRepo;
    private final MachineTelemetryRepository telemetryRepo;

    @Override
    public List<MachineDetailResponse> getAllMachines() {
        return machineRepo.findAll().stream().map(this::toDetail).toList();
    }

    @Override
    public MachineDetailResponse getMachineDetail(UUID machineId) {
        return toDetail(findMachine(machineId));
    }

    @Override
    public MachineSnapshotResponse getLatestSnapshot(UUID machineId) {
        var machine = findMachine(machineId);
        return telemetryRepo.findFirstByMachineIdOrderByTsDesc(machineId)
                .map(t -> toSnapshot(t, machine))
                .orElse(emptySnapshot(machine));
    }

    @Override
    public List<MachineSnapshotResponse> getAllLatestSnapshots() {
        var machines = machineRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(MachineEntity::getId, m -> m));
        return telemetryRepo.findLatestForAllMachines().stream()
                .map(t -> toSnapshot(t, machines.get(t.getMachineId())))
                .toList();
    }

    private MachineEntity findMachine(UUID id) {
        return machineRepo.findById(id).orElseThrow(() -> AppException.notFound("Machine", id));
    }

    private MachineDetailResponse toDetail(MachineEntity m) {
        return MachineDetailResponse.builder()
                .id(m.getId()).code(m.getCode()).name(m.getName())
                .type(m.getType()).vendor(m.getVendor()).model(m.getModel())
                .lineId(m.getLineId()).plantId(m.getPlantId())
                .status(m.getStatus()).isEnabled(m.getIsEnabled())
                .createdAt(m.getCreatedAt()).build();
    }

    private MachineSnapshotResponse toSnapshot(MachineTelemetryEntity t, MachineEntity m) {
        return MachineSnapshotResponse.builder()
                .machineId(t.getMachineId()).machineCode(m.getCode()).machineName(m.getName())
                .ts(t.getTs()).connectionStatus(t.getConnectionStatus())
                .machineState(t.getMachineState()).operationMode(t.getOperationMode())
                .programName(t.getProgramName()).cycleRunning(t.getCycleRunning())
                .powerKw(t.getPowerKw()).temperatureC(t.getTemperatureC())
                .vibrationMmS(t.getVibrationMmS()).runtimeHours(t.getRuntimeHours())
                .cycleTimeSec(t.getCycleTimeSec()).outputCount(t.getOutputCount())
                .goodCount(t.getGoodCount()).rejectCount(t.getRejectCount())
                .spindleSpeedRpm(t.getSpindleSpeedRpm()).feedRateMmMin(t.getFeedRateMmMin())
                .build();
    }

    private MachineSnapshotResponse emptySnapshot(MachineEntity m) {
        return MachineSnapshotResponse.builder()
                .machineId(m.getId()).machineCode(m.getCode()).machineName(m.getName())
                .connectionStatus("OFFLINE").machineState("OFFLINE").build();
    }
}

