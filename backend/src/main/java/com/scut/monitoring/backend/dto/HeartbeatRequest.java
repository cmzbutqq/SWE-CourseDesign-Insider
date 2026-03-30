package com.scut.monitoring.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record HeartbeatRequest(
        @NotBlank String nodeName,
        @NotBlank String status
) {
}
