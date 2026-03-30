package com.scut.monitoring.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DiscoveredServicePayload(
        @NotBlank String serviceName,
        @NotBlank String serviceType,
        @NotNull Integer port,
        String processName,
        String metricsPath
) {
}
