package com.scut.monitoring.backend.dto;

/**
 * 异常服务详情
 */
public record AnomalyServiceDTO(
        Long id,
        String serviceName,
        String status,
        String errorType,  // "UNAVAILABLE" / "HIGH_ERROR_RATE" / "TIMEOUT"
        String nodeName
) {
}
