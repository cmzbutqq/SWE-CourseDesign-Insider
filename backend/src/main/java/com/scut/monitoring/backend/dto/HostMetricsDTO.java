package com.scut.monitoring.backend.dto;

public record HostMetricsDTO(
        Double cpuUsage,
        Double memoryUsage,
        Long memoryTotalMb,
        Long memoryUsedMb,
        Double diskUsage,
        Long diskTotalGb,
        Long diskUsedGb,
        Double networkRxMbps,
        Double networkTxMbps
) {
}
