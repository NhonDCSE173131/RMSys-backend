package com.rmsys.backend.domain.service;

public interface AggregationService {
    void aggregateRealtimeRollingKpis();
    void aggregateOee();
    void aggregateHealth();
    void aggregateMaintenancePredictions();
}

