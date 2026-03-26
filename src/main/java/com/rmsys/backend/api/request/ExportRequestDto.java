package com.rmsys.backend.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request body for creating a telemetry export job.
 */
@Builder
public record ExportRequestDto(

        @NotNull(message = "machineId is required")
        UUID machineId,

        @NotNull(message = "from is required")
        Instant from,

        @NotNull(message = "to is required")
        Instant to,

        /** Optional list of metric names to include; null means all metrics. */
        List<String> metrics,

        /**
         * Optional time-bucket interval: "raw"|"1m"|"5m"|"15m"|"30m"|"1h"|"6h"|"12h"|"1d".
         * Defaults to "raw" (no aggregation).
         */
        String interval,

        /**
         * Aggregation function when interval is set: "avg"|"min"|"max"|"last".
         * Defaults to "avg".
         */
        String aggregation,

        /**
         * Output format: "csv" (currently the only supported value).
         * Defaults to "csv".
         */
        String format,

        /** IANA timezone identifier for timestamps in the output file (e.g. "Asia/Ho_Chi_Minh"). */
        String timezone
) {}

