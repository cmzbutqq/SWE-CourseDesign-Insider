package com.scut.monitoring.backend.dto;

import java.util.List;

public record OverviewResponse(
        long totalNodes,
        long totalServices,
        long onlineNodes,
        List<String> quickLinks
) {
}
