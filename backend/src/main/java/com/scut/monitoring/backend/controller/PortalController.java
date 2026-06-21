package com.scut.monitoring.backend.controller;

import com.scut.monitoring.backend.dto.NodeDetailResponse;
import com.scut.monitoring.backend.dto.NodeSummaryResponse;
import com.scut.monitoring.backend.dto.OverviewResponse;
import com.scut.monitoring.backend.dto.ServiceDetailResponse;
import com.scut.monitoring.backend.dto.ServiceSummaryResponse;
import com.scut.monitoring.backend.dto.TracingSummaryResponse;
import com.scut.monitoring.backend.dto.TrendsResponse;
import com.scut.monitoring.backend.service.NodeRegistryService;
import com.scut.monitoring.backend.service.SkyWalkingQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api", produces = "application/json;charset=UTF-8")
public class PortalController {

    private final NodeRegistryService nodeRegistryService;
    private final SkyWalkingQueryService skyWalkingQueryService;

    public PortalController(NodeRegistryService nodeRegistryService, SkyWalkingQueryService skyWalkingQueryService) {
        this.nodeRegistryService = nodeRegistryService;
        this.skyWalkingQueryService = skyWalkingQueryService;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        return nodeRegistryService.overview();
    }

    @GetMapping("/nodes")
    public List<NodeSummaryResponse> nodes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String serviceType,
            @RequestParam(defaultValue = "name") String sortBy) {
        return nodeRegistryService.listNodes(status, keyword, serviceType, sortBy);
    }

    @GetMapping("/nodes/{id}")
    public NodeDetailResponse nodeDetail(@PathVariable Long id) {
        return nodeRegistryService.getNode(id);
    }

    @GetMapping("/services")
    public List<ServiceSummaryResponse> services() {
        return nodeRegistryService.listServices();
    }

    @GetMapping("/services/{id}")
    public ServiceDetailResponse serviceDetail(@PathVariable Long id) {
        return nodeRegistryService.getService(id);
    }

    @GetMapping(value = "/trends", produces = "application/json;charset=UTF-8")
    public TrendsResponse trends(@RequestParam(defaultValue = "1") double hours) {
        return nodeRegistryService.getTrends(hours);
    }

    @GetMapping("/tracing/summary")
    public TracingSummaryResponse tracingSummary() {
        return skyWalkingQueryService.loadTracingSummary();
    }
}
