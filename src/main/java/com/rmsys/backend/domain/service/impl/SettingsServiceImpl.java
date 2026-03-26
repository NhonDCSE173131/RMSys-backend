package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.ThresholdResponse;
import com.rmsys.backend.api.response.ThresholdResponse.ThresholdItem;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.repository.MachineThresholdRepository;
import com.rmsys.backend.domain.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private final MachineThresholdRepository thresholdRepo;
    private final MachineRepository machineRepo;

    @Override
    public ThresholdResponse getAllThresholds() {
        var machines = machineRepo.findAll().stream()
                .collect(Collectors.toMap(m -> m.getId(), m -> m));

        var items = thresholdRepo.findAll().stream().map(t -> {
            var machine = machines.get(t.getMachineId());
            return ThresholdItem.builder()
                    .machineId(t.getMachineId().toString())
                    .machineName(machine != null ? machine.getName() : "Unknown")
                    .metricCode(t.getMetricCode())
                    .warningValue(t.getWarningValue())
                    .criticalValue(t.getCriticalValue())
                    .unit(t.getUnit()).build();
        }).toList();

        return ThresholdResponse.builder().thresholds(items).build();
    }
}

