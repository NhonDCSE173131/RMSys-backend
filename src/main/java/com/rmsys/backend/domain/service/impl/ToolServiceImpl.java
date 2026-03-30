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
            if (!catalogs.isEmpty()) {
                for (var cat : catalogs) {
                    var latest = usageRepo.findFirstByMachineIdAndToolCodeOrderByTsDesc(mid, cat.getToolCode()).orElse(null);
                    var summary = summarizeRisk(latest != null ? latest.getRemainingLifePct() : null);
                    if ("CRITICAL".equals(summary.risk())) {
                        critical++;
                    } else if ("HIGH".equals(summary.risk())) {
                        warning++;
                    }

                    items.add(ToolItem.builder()
                            .machineId(mid).machineCode(machine.getCode()).machineName(machine.getName())
                            .toolCode(cat.getToolCode()).toolName(cat.getToolName())
                            .usageMinutes(latest != null ? latest.getUsageMinutes() : null)
                            .usageCycles(latest != null ? latest.getUsageCycles() : null)
                            .remainingLifePct(summary.remaining()).wearLevel(summary.wear()).riskLevel(summary.risk()).build());
                }
                continue;
            }

            // Fallback: derive tools directly from latest usage telemetry when catalog is not configured.
            var latestByToolCode = new java.util.LinkedHashMap<String, com.rmsys.backend.domain.entity.ToolUsageTelemetryEntity>();
            for (var usage : usageRepo.findByMachineIdOrderByTsDesc(mid)) {
                if (usage.getToolCode() == null || usage.getToolCode().isBlank()) {
                    continue;
                }
                latestByToolCode.putIfAbsent(usage.getToolCode(), usage);
            }
            for (var usage : latestByToolCode.values()) {
                var summary = summarizeRisk(usage.getRemainingLifePct());
                if ("CRITICAL".equals(summary.risk())) {
                    critical++;
                } else if ("HIGH".equals(summary.risk())) {
                    warning++;
                }

                items.add(ToolItem.builder()
                        .machineId(mid).machineCode(machine.getCode()).machineName(machine.getName())
                        .toolCode(usage.getToolCode()).toolName(usage.getToolCode())
                        .usageMinutes(usage.getUsageMinutes())
                        .usageCycles(usage.getUsageCycles())
                        .remainingLifePct(summary.remaining()).wearLevel(summary.wear()).riskLevel(summary.risk()).build());
            }
        }

        return ToolOverviewResponse.builder()
                .totalTools(items.size()).criticalTools(critical).warningTools(warning)
                .tools(items).build();
    }

    private RiskSummary summarizeRisk(Double remainingLifePct) {
        if (remainingLifePct == null) {
            return new RiskSummary(null, "UNKNOWN", "UNKNOWN");
        }

        double remaining = Math.max(0, Math.min(100, remainingLifePct));
        String risk = remaining < 10 ? "CRITICAL" : remaining < 20 ? "HIGH" : remaining < 40 ? "MEDIUM" : "LOW";
        String wear = remaining < 10 ? "CRITICAL" : remaining < 30 ? "WORN" : remaining < 60 ? "NORMAL" : "NEW";
        return new RiskSummary(remaining, wear, risk);
    }

    private record RiskSummary(Double remaining, String wear, String risk) {}
}

