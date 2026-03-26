package com.rmsys.backend.infrastructure.adapter.simulator;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.IngestService;
import com.rmsys.backend.infrastructure.adapter.DeviceAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatorAdapter implements DeviceAdapter {

    private final MachineRepository machineRepo;
    private final IngestService ingestService;
    private final java.util.concurrent.atomic.AtomicBoolean simulatorStartedLogged = new java.util.concurrent.atomic.AtomicBoolean(false);

    // Simulation state per machine
    private final java.util.Map<java.util.UUID, SimState> states = new java.util.concurrent.ConcurrentHashMap<>();

    @Override public String adapterCode() { return "SIMULATOR"; }
    @Override public void start() { log.info("Simulator adapter started"); }
    @Override public void stop() { log.info("Simulator adapter stopped"); }
    @Override public boolean supports(String v) { return true; }

    public void simulateAll() {
        var machines = machineRepo.findAll();
        long enabledMachines = machines.stream().filter(MachineEntity::getIsEnabled).count();
        if (enabledMachines > 0 && simulatorStartedLogged.compareAndSet(false, true)) {
            log.info("Simulator noi bo da kich hoat. Bat dau sinh du lieu mo phong cho {} may.", enabledMachines);
        }
        for (var machine : machines) {
            if (!machine.getIsEnabled()) continue;
            var state = states.computeIfAbsent(machine.getId(), k -> new SimState());
            var dto = generateTelemetry(machine, state);
            ingestService.ingestTelemetry(dto);
        }
    }

    private NormalizedTelemetryDto generateTelemetry(MachineEntity machine, SimState s) {
        var rng = ThreadLocalRandom.current();

        // Randomly change machine state
        s.tickCount++;
        if (s.tickCount % 30 == 0) { // every ~60s change state randomly
            s.machineState = rng.nextDouble() < 0.7 ? "RUNNING" : rng.nextDouble() < 0.5 ? "IDLE" : "STOPPED";
        }

        boolean isRunning = "RUNNING".equals(s.machineState);
        s.runtimeHours += isRunning ? 0.00056 : 0; // ~2s
        s.outputCount += isRunning && rng.nextDouble() < 0.3 ? 1 : 0;
        s.goodCount += isRunning && rng.nextDouble() < 0.28 ? 1 : 0;
        s.rejectCount += isRunning && rng.nextDouble() < 0.02 ? 1 : 0;

        // Gradually drift temperature and vibration
        double tempBase = isRunning ? 52 : 30;
        s.temperature = drift(s.temperature, tempBase, 0.5, 20, 95);
        double vibBase = isRunning ? 3.0 : 0.5;
        s.vibration = drift(s.vibration, vibBase, 0.2, 0, 12);
        double powerBase = isRunning ? 12.0 : 1.5;
        s.power = drift(s.power, powerBase, 0.5, 0, 30);

        s.toolLifePct = Math.max(0, s.toolLifePct - (isRunning ? rng.nextDouble(0.01, 0.05) : 0));
        if (s.toolLifePct <= 0) s.toolLifePct = 100; // auto replace for sim

        String[] programs = {"PROG_001", "PROG_002", "PROG_003", "PROG_MAINT"};
        String program = isRunning ? programs[rng.nextInt(programs.length)] : null;

        return NormalizedTelemetryDto.builder()
                .machineId(machine.getId()).ts(Instant.now())
                .connectionStatus("ONLINE").machineState(s.machineState)
                .operationMode(isRunning ? "AUTOMATIC" : "MANUAL")
                .programName(program).cycleRunning(isRunning)
                .powerKw(round(s.power)).temperatureC(round(s.temperature))
                .vibrationMmS(round(s.vibration)).runtimeHours(round(s.runtimeHours))
                .cycleTimeSec(isRunning ? round(25 + rng.nextDouble(10)) : null)
                .outputCount(s.outputCount).goodCount(s.goodCount).rejectCount(s.rejectCount)
                .spindleSpeedRpm(isRunning ? round(800 + rng.nextDouble(400)) : null)
                .feedRateMmMin(isRunning ? round(100 + rng.nextDouble(200)) : null)
                .toolCode("T01").remainingToolLifePct(round(s.toolLifePct))
                // Energy
                .voltageV(round(380 + rng.nextDouble(-5, 5)))
                .currentA(round(s.power / 0.38))
                .powerFactor(round(0.85 + rng.nextDouble(0.1)))
                .frequencyHz(50.0)
                .energyKwhShift(round(s.runtimeHours * s.power * 0.5))
                .energyKwhDay(round(s.runtimeHours * s.power))
                // Maintenance
                .motorTemperatureC(round(s.temperature + rng.nextDouble(-3, 5)))
                .bearingTemperatureC(round(s.temperature + rng.nextDouble(-5, 3)))
                .cabinetTemperatureC(round(28 + rng.nextDouble(5)))
                .servoOnHours(round(s.runtimeHours * 0.95))
                .startStopCount(s.outputCount / 10)
                .lubricationLevelPct(round(Math.max(20, 90 - s.runtimeHours * 0.01)))
                .batteryLow(s.runtimeHours > 9000)
                .build();
    }

    private double drift(double current, double target, double step, double min, double max) {
        var rng = ThreadLocalRandom.current();
        double delta = (target - current) * 0.1 + rng.nextDouble(-step, step);
        return Math.max(min, Math.min(max, current + delta));
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }

    static class SimState {
        String machineState = "RUNNING";
        double temperature = 45, vibration = 2.0, power = 8.0;
        double runtimeHours = 1200, toolLifePct = 85;
        int outputCount = 0, goodCount = 0, rejectCount = 0, tickCount = 0;
    }
}

