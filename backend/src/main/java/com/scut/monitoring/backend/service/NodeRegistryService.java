package com.scut.monitoring.backend.service;

import com.scut.monitoring.backend.dto.AgentRegisterRequest;
import com.scut.monitoring.backend.dto.AnomaliesDTO;
import com.scut.monitoring.backend.dto.AnomalyNodeDTO;
import com.scut.monitoring.backend.dto.AnomalyServiceDTO;
import com.scut.monitoring.backend.dto.CounterGroupDTO;
import com.scut.monitoring.backend.dto.HeartbeatRequest;
import com.scut.monitoring.backend.dto.NodeDetailResponse;
import com.scut.monitoring.backend.dto.NodeSummaryResponse;
import com.scut.monitoring.backend.dto.OverviewResponse;
import com.scut.monitoring.backend.dto.ServiceSummaryResponse;
import com.scut.monitoring.backend.model.DiscoveredService;
import com.scut.monitoring.backend.model.HeartbeatEvent;
import com.scut.monitoring.backend.model.ManagedNode;
import com.scut.monitoring.backend.repository.DiscoveredServiceRepository;
import com.scut.monitoring.backend.repository.HeartbeatEventRepository;
import com.scut.monitoring.backend.repository.ManagedNodeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class NodeRegistryService {

    private final ManagedNodeRepository managedNodeRepository;
    private final DiscoveredServiceRepository discoveredServiceRepository;
    private final HeartbeatEventRepository heartbeatEventRepository;
    private final String grafanaBaseUrl;
    private final String skywalkingBaseUrl;
    private final String prometheusBaseUrl;

    public NodeRegistryService(
            ManagedNodeRepository managedNodeRepository,
            DiscoveredServiceRepository discoveredServiceRepository,
            HeartbeatEventRepository heartbeatEventRepository,
            @Value("${monitoring.prometheus.base-url:http://localhost:19090}") String prometheusBaseUrl,
            @Value("${monitoring.grafana.base-url:http://localhost:13000}") String grafanaBaseUrl,
            @Value("${monitoring.skywalking.base-url:http://localhost:18082}") String skywalkingBaseUrl
    ) {
        this.managedNodeRepository = managedNodeRepository;
        this.discoveredServiceRepository = discoveredServiceRepository;
        this.heartbeatEventRepository = heartbeatEventRepository;
        this.prometheusBaseUrl = prometheusBaseUrl;
        this.grafanaBaseUrl = grafanaBaseUrl;
        this.skywalkingBaseUrl = skywalkingBaseUrl;
    }

    @Transactional
    public NodeDetailResponse registerNode(AgentRegisterRequest request) {
        ManagedNode node = managedNodeRepository.findByNodeName(request.nodeName())
                .orElseGet(ManagedNode::new);

        node.setNodeName(request.nodeName());
        node.setHostname(request.hostname());
        node.setIpAddress(request.ipAddress());
        node.setOsName(request.osName());
        node.setAgentVersion(request.agentVersion());
        node.setStatus("ONLINE");
        node.setLastSeenAt(Instant.now());
        node.getServices().clear();

        for (var servicePayload : request.services()) {
            DiscoveredService service = new DiscoveredService();
            service.setNode(node);
            service.setServiceName(servicePayload.serviceName());
            service.setServiceType(servicePayload.serviceType());
            service.setPort(servicePayload.port());
            service.setProcessName(servicePayload.processName() == null ? servicePayload.serviceName() : servicePayload.processName());
            service.setMetricsPath(servicePayload.metricsPath());
            node.getServices().add(service);
        }

        ManagedNode savedNode = managedNodeRepository.save(node);
        return toNodeDetail(savedNode);
    }

    @Transactional
    public NodeSummaryResponse heartbeat(HeartbeatRequest request) {
        ManagedNode node = managedNodeRepository.findByNodeName(request.nodeName())
                .orElseThrow(() -> new EntityNotFoundException("Node not registered: " + request.nodeName()));

        node.setStatus(request.status());
        node.setLastSeenAt(Instant.now());
        HeartbeatEvent event = new HeartbeatEvent();
        event.setNode(node);
        event.setStatus(request.status());
        event.setCreatedAt(Instant.now());
        heartbeatEventRepository.save(event);

        return toNodeSummary(node);
    }

    @Transactional(readOnly = true)
    public List<NodeSummaryResponse> listNodes() {
        return managedNodeRepository.findAll().stream()
                .sorted(Comparator.comparing(ManagedNode::getNodeName))
                .map(this::toNodeSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public NodeDetailResponse getNode(Long id) {
        ManagedNode node = managedNodeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Node not found: " + id));
        return toNodeDetail(node);
    }

    @Transactional(readOnly = true)
    public List<ServiceSummaryResponse> listServices() {
        return discoveredServiceRepository.findAllByOrderByServiceTypeAscServiceNameAsc().stream()
                .map(this::toServiceSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        // 获取所有节点和服务
        List<ManagedNode> allNodes = managedNodeRepository.findAll();
        long totalServices = discoveredServiceRepository.count();
        
        // 统计节点状态
        long onlineCount = allNodes.stream()
                .filter(node -> "ONLINE".equalsIgnoreCase(node.getStatus()))
                .count();
        long offlineCount = allNodes.size() - onlineCount;
        
        // 构建节点统计
        CounterGroupDTO nodesCounter = new CounterGroupDTO(
                allNodes.size(),      // total
                onlineCount,          // online
                offlineCount,         // offline
                0,                    // warning（暂时为0，需要实现详细指标）
                0,                    // healthy（不用）
                0                     // abnormal（不用）
        );
        
        // 构建服务统计（简化版：暂时全部标记为 healthy）
        CounterGroupDTO servicesCounter = new CounterGroupDTO(
                totalServices,        // total
                0,                    // online（不用）
                0,                    // offline（不用）
                0,                    // warning（不用）
                totalServices,        // healthy（暂时等于总数）
                0                     // abnormal（0表示无异常服务）
        );
        
        // 收集异常节点和服务
        AnomaliesDTO anomalies = buildAnomalies(allNodes);
        
        // 快捷链接
        List<String> quickLinks = List.of(
                "Prometheus: " + prometheusBaseUrl,
                "Grafana: " + grafanaBaseUrl,
                "SkyWalking: " + skywalkingBaseUrl
        );
        
        return new OverviewResponse(
                nodesCounter,
                servicesCounter,
                0L,                   // unresolvedAlerts（暂时为0）
                anomalies,
                quickLinks
        );
    }

    /**
     * 构建异常数据：离线节点和异常服务
     */
    private AnomaliesDTO buildAnomalies(List<ManagedNode> allNodes) {
        // 异常节点：筛选离线或在线时间很久未更新的节点
        List<AnomalyNodeDTO> anomalyNodes = allNodes.stream()
                .filter(node -> !"ONLINE".equalsIgnoreCase(node.getStatus()))
                .map(node -> new AnomalyNodeDTO(
                        node.getId(),
                        node.getNodeName(),
                        node.getStatus(),
                        "OFFLINE",
                        node.getLastSeenAt(),
                        null,  // cpuUsage
                        null,  // memoryUsage
                        calculateDurationSeconds(node.getLastSeenAt())
                ))
                .sorted(Comparator.comparingLong(AnomalyNodeDTO::durationSeconds).reversed())
                .toList();
        
        // 异常服务：暂时为空列表（等待后端实现服务健康检查）
        List<AnomalyServiceDTO> anomalyServices = List.of();
        
        return new AnomaliesDTO(anomalyNodes, anomalyServices);
    }

    /**
     * 计算从指定时间到现在的秒数
     */
    private Long calculateDurationSeconds(Instant lastSeen) {
        if (lastSeen == null) return 0L;
        return Math.max(0, Instant.now().getEpochSecond() - lastSeen.getEpochSecond());
    }

    private NodeSummaryResponse toNodeSummary(ManagedNode node) {
        return new NodeSummaryResponse(
                node.getId(),
                node.getNodeName(),
                node.getHostname(),
                node.getIpAddress(),
                node.getOsName(),
                node.getAgentVersion(),
                node.getStatus(),
                node.getLastSeenAt(),
                node.getServices().stream().map(DiscoveredService::getServiceType).distinct().sorted().toList()
        );
    }

    private NodeDetailResponse toNodeDetail(ManagedNode node) {
        return new NodeDetailResponse(
                node.getId(),
                node.getNodeName(),
                node.getHostname(),
                node.getIpAddress(),
                node.getOsName(),
                node.getAgentVersion(),
                node.getStatus(),
                node.getLastSeenAt(),
                node.getServices().stream()
                        .sorted(Comparator.comparing(DiscoveredService::getServiceType).thenComparing(DiscoveredService::getServiceName))
                        .map(this::toServiceSummary)
                        .toList()
        );
    }

    private ServiceSummaryResponse toServiceSummary(DiscoveredService service) {
        return new ServiceSummaryResponse(
                service.getId(),
                service.getServiceName(),
                service.getServiceType(),
                service.getPort(),
                service.getProcessName(),
                service.getMetricsPath(),
                service.getNode().getNodeName()
        );
    }
}
