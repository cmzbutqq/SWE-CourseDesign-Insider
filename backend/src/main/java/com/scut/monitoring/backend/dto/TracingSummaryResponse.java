package com.scut.monitoring.backend.dto;

import java.util.List;

public record TracingSummaryResponse(
        List<String> serviceNames,
        List<TraceSummaryItemDTO> traces,
        LatestTracePreviewDTO latestTrace
) {
}
