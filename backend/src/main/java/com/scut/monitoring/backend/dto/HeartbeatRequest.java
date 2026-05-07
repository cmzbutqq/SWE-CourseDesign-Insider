package com.scut.monitoring.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HeartbeatRequest(
        @NotBlank String nodeName,
        @NotBlank @Pattern(regexp = "ONLINE|OFFLINE|WARNING", message = "status must be ONLINE, OFFLINE, or WARNING") String status
) {
}
