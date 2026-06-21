package com.scut.monitoring.backend.dto;

import java.util.List;

public record LatestTracePreviewDTO(
        String traceId,
        String entryService,
        String entryEndpoint,
        List<String> serviceChain,
        List<String> dependencyChain,
        Integer spanCount,
        Integer durationMs,
        String startTime,
        boolean error
) {
}
