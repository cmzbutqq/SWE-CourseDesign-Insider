package com.scut.monitoring.backend.dto;

import java.util.List;

public record ServiceDetailResponse(
        Long id,
        String serviceName,
        String serviceType,
        Integer port,
        String processName,
        String metricsPath,
        Long nodeId,
        String nodeName,
        String nodeIpAddress,
        String nodeStatus,
        boolean metricsMissing,
        List<QuickLinkDTO> quickLinks
) {
}
