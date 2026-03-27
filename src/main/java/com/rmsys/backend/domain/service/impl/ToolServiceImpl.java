package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.ToolOverviewResponse;
import com.rmsys.backend.api.response.ToolOverviewResponse.ToolItem;
import com.rmsys.backend.domain.repository.*;
import com.rmsys.backend.domain.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {

    private final MachineRepository machineRepo;
    private final ToolCatalogRepository catalogRepo;
    private final ToolUsageTelemetryRepository usageRepo;

    @Override
    public ToolOverviewResponse getOverview() {
        return buildToolOverview(machineRepo.findAll().stream().map(m -> m.getId()).toList());
    }

    @Override
    public ToolOverviewResponse getByMachine(UUID machineId) {
        return buildToolOverview(List.of(machineId));
    }

    private ToolOverviewResponse buildToolOverview(List<UUID> machineIds) {
        var items = new ArrayList<ToolItem>();
        int critical = 0, warning = 0;

        for (var mid : machineIds) {
            var machine = machineRepo.findById(mid).orElse(null);
            if (machine == null) continue;
            var catalogs = catalogRepo.findByMachineId(mid);
            for (var cat : catalogs) {
                var latest = usageRepo.findFirstByMachineIdAndToolCodeOrderByTsDesc(mid, cat.getToolCode());
                double remaining = latest.map(u -> u.getRemainingLifePct() != null ? u.getRemainingLifePct() : 100.0).orElse(100.0);
                String risk = remaining < 10 ? "CRITICAL" : remaining < 20 ? "HIGH" : remaining < 40 ? "MEDIUM" : "LOW";
                String wear = remaining < 10 ? "CRITICAL" : remaining < 30 ? "WORN" : remaining < 60 ? "NORMAL" : "NEW";
                if (remaining < 10) critical++;
                else if (remaining < 20) warning++;

                items.add(ToolItem.builder()
                        .machineId(mid).machineCode(machine.getCode()).machineName(machine.getName())
                        .toolCode(cat.getToolCode()).toolName(cat.getToolName())
                        .usageMinutes(latest.map(u -> u.getUsageMinutes()).orElse(null))
                        .usageCycles(latest.map(u -> u.getUsageCycles()).orElse(null))
                        .remainingLifePct(remaining).wearLevel(wear).riskLevel(risk).build());
            }
        }

        return ToolOverviewResponse.builder()
                .totalTools(items.size()).criticalTools(critical).warningTools(warning)
                .tools(items).build();
    }
}

