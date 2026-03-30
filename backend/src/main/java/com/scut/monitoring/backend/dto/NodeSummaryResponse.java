package com.scut.monitoring.backend.dto;

import java.time.Instant;
import java.util.List;

public record NodeSummaryResponse(
        Long id,
        String nodeName,
        String hostname,
        String ipAddress,
        String osName,
        String agentVersion,
        String status,
        Instant lastSeenAt,
        List<String> serviceTypes
) {
}
