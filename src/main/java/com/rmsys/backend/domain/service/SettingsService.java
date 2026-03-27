package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.ThresholdResponse;

import java.util.Map;

public interface SettingsService {
    Map<String, Object> getUiThresholds();

    ThresholdResponse getMachineThresholds();
}

