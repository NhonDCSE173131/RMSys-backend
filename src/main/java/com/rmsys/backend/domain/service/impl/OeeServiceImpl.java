package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.OeeOverviewResponse;
import com.rmsys.backend.api.response.OeeOverviewResponse.MachineOeeItem;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.repository.OeeSnapshotRepository;
import com.rmsys.backend.domain.service.OeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class OeeServiceImpl implements OeeService {

    private final MachineRepository machineRepo;
    private final OeeSnapshotRepository oeeRepo;

    @Override
    public OeeOverviewResponse getOverview() {
        var machines = machineRepo.findAll();
        var items = new ArrayList<MachineOeeItem>();
        double sumA = 0, sumP = 0, sumQ = 0, sumO = 0;
        int count = 0;

        for (var m : machines) {
            var latest = oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(m.getId(), "HOUR");
            if (latest.isPresent()) {
                var s = latest.get();
                sumA += s.getAvailability() != null ? s.getAvailability() : 0;
                sumP += s.getPerformance() != null ? s.getPerformance() : 0;
                sumQ += s.getQuality() != null ? s.getQuality() : 0;
                sumO += s.getOee() != null ? s.getOee() : 0;
                count++;
                items.add(MachineOeeItem.builder()
                        .machineId(m.getId()).machineName(m.getName())
                        .availability(s.getAvailability()).performance(s.getPerformance())
                        .quality(s.getQuality()).oee(s.getOee()).build());
            }
        }

        return OeeOverviewResponse.builder()
                .avgAvailability(count > 0 ? sumA / count : 0)
                .avgPerformance(count > 0 ? sumP / count : 0)
                .avgQuality(count > 0 ? sumQ / count : 0)
                .avgOee(count > 0 ? sumO / count : 0)
                .machines(items).build();
    }
}

