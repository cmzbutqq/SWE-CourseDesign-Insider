package com.scut.monitoring.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AgentRegisterRequest(
        @NotBlank String nodeName,
        @NotBlank String hostname,
        @NotBlank String ipAddress,
        @NotBlank String osName,
        @NotBlank String agentVersion,
        @NotEmpty @Valid List<DiscoveredServicePayload> services
) {
}
