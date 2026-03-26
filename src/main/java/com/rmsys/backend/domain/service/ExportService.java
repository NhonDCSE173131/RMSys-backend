package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.request.ExportRequestDto;
import com.rmsys.backend.api.response.ExportJobResponse;

public interface ExportService {

    /**
     * Creates an async export job for telemetry data and immediately returns job metadata.
     * Processing runs in the background; poll {@link #getJobStatus} for completion.
     */
    ExportJobResponse createTelemetryExport(ExportRequestDto request);

    /** Returns the current status and metadata of the given export job. */
    ExportJobResponse getJobStatus(String jobId);

    /**
     * Returns the raw file bytes once the job is COMPLETED.
     * Throws if the job does not exist or is not yet COMPLETED.
     */
    byte[] downloadResult(String jobId);
}

