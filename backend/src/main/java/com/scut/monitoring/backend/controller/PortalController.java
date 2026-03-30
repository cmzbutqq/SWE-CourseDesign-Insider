package com.scut.monitoring.backend.controller;

import com.scut.monitoring.backend.dto.NodeDetailResponse;
import com.scut.monitoring.backend.dto.NodeSummaryResponse;
import com.scut.monitoring.backend.dto.OverviewResponse;
import com.scut.monitoring.backend.dto.ServiceSummaryResponse;
import com.scut.monitoring.backend.service.NodeRegistryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PortalController {

    private final NodeRegistryService nodeRegistryService;

    public PortalController(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        return nodeRegistryService.overview();
    }

    @GetMapping("/nodes")
    public List<NodeSummaryResponse> nodes() {
        return nodeRegistryService.listNodes();
    }

    @GetMapping("/nodes/{id}")
    public NodeDetailResponse nodeDetail(@PathVariable Long id) {
        return nodeRegistryService.getNode(id);
    }

    @GetMapping("/services")
    public List<ServiceSummaryResponse> services() {
        return nodeRegistryService.listServices();
    }
}
