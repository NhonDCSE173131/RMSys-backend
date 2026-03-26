package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.EnergyOverviewResponse;
import com.rmsys.backend.api.response.EnergyOverviewResponse.MachineEnergyItem;
import com.rmsys.backend.domain.repository.EnergyTelemetryRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.EnergyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class EnergyServiceImpl implements EnergyService {

    private final MachineRepository machineRepo;
    private final EnergyTelemetryRepository energyRepo;

    @Override
    public EnergyOverviewResponse getOverview() {
        var machines = machineRepo.findAll();
        var items = new ArrayList<MachineEnergyItem>();
        double totalPower = 0, totalEnergy = 0, pfSum = 0;
        int pfCount = 0;

        for (var m : machines) {
            var latest = energyRepo.findFirstByMachineIdOrderByTsDesc(m.getId());
            if (latest.isPresent()) {
                var e = latest.get();
                var power = e.getPowerKw() != null ? e.getPowerKw() : 0;
                totalPower += power;
                totalEnergy += e.getEnergyKwhDay() != null ? e.getEnergyKwhDay() : 0;
                if (e.getPowerFactor() != null) { pfSum += e.getPowerFactor(); pfCount++; }
                items.add(MachineEnergyItem.builder()
                        .machineId(m.getId()).machineName(m.getName())
                        .powerKw(e.getPowerKw()).voltageV(e.getVoltageV())
                        .currentA(e.getCurrentA()).powerFactor(e.getPowerFactor())
                        .energyKwhDay(e.getEnergyKwhDay()).build());
            }
        }

        return EnergyOverviewResponse.builder()
                .totalPowerKw(totalPower).totalEnergyTodayKwh(totalEnergy)
                .avgPowerFactor(pfCount > 0 ? pfSum / pfCount : 0)
                .machines(items).build();
    }
}

