package com.scut.monitoring.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DiscoveredServicePayload(
        @NotBlank @Size(max = 128) String serviceName,
        @NotBlank @Size(max = 64) String serviceType,
        @NotNull @Min(1) @Max(65535) Integer port,
        @Size(max = 128) String processName,
        @Size(max = 256) String metricsPath
) {
}
