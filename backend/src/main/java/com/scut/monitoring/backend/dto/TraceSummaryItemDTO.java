package com.scut.monitoring.backend.dto;

import java.util.List;

public record TraceSummaryItemDTO(
        String traceId,
        List<String> endpoints,
        Integer durationMs,
        String startTime,
        boolean error
) {
}
