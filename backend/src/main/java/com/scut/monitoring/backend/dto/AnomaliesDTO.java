package com.scut.monitoring.backend.dto;

import java.util.List;

/**
 * 聚合异常信息
 */
public record AnomaliesDTO(
        List<AnomalyNodeDTO> nodes,
        List<AnomalyServiceDTO> services
) {
}
