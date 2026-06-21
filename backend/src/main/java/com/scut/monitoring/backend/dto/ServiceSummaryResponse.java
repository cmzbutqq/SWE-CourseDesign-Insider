package com.scut.monitoring.backend.dto;

public record ServiceSummaryResponse(
        Long id,
        String serviceName,
        String serviceType,
        Integer port,
        String processName,
        String metricsPath,
        Integer metricsPort,
        Long nodeId,
        String nodeName
) {
}
