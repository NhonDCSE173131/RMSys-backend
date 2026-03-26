package com.rmsys.backend.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Status and metadata for an async export job.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportJobResponse(
        String jobId,
        /** PENDING | PROCESSING | COMPLETED | FAILED */
        String status,
        UUID machineId,
        Instant from,
        Instant to,
        String format,
        Instant createdAt,
        Instant completedAt,
        /** Relative URL to download the result; populated only when status=COMPLETED. */
        String downloadUrl,
        String errorMessage,
        Integer totalRows
) {}

