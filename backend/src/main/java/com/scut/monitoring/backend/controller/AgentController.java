package com.scut.monitoring.backend.controller;

import com.scut.monitoring.backend.dto.AgentRegisterRequest;
import com.scut.monitoring.backend.dto.HeartbeatRequest;
import com.scut.monitoring.backend.dto.NodeDetailResponse;
import com.scut.monitoring.backend.dto.NodeSummaryResponse;
import com.scut.monitoring.backend.service.NodeRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final NodeRegistryService nodeRegistryService;

    public AgentController(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public NodeDetailResponse register(@Valid @RequestBody AgentRegisterRequest request) {
        return nodeRegistryService.registerNode(request);
    }

    @PostMapping("/heartbeat")
    public NodeSummaryResponse heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        return nodeRegistryService.heartbeat(request);
    }
}
