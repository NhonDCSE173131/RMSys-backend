package com.rmsys.backend.api.response;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record AnalyticsTrendResponse(
		Instant from,
		Instant to,
		String interval,
		List<AnalyticsTrendPointResponse> points
) {}

