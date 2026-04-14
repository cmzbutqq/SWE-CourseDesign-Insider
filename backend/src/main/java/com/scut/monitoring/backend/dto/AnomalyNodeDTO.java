package com.scut.monitoring.backend.dto;

import java.time.Instant;

/**
 * 异常节点详情
 */
public record AnomalyNodeDTO(
        Long id,
        String nodeName,
        String status,
        String reason,  // "OFFLINE" / "HIGH_CPU" / "HIGH_MEMORY" / "HIGH_LOAD"
        Instant lastSeenAt,
        Double cpuUsage,
        Double memoryUsage,
        Long durationSeconds  // 异常持续时间
) {
}
