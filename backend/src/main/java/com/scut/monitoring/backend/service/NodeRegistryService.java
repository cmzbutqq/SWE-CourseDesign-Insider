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
import com.scut.monitoring.backend.dto.TrendData;
import com.scut.monitoring.backend.dto.TrendsResponse;
import com.scut.monitoring.backend.model.DiscoveredService;
import com.scut.monitoring.backend.model.HeartbeatEvent;
import com.scut.monitoring.backend.model.ManagedNode;
import com.scut.monitoring.backend.model.MetricsSnapshot;
import com.scut.monitoring.backend.repository.DiscoveredServiceRepository;
import com.scut.monitoring.backend.repository.HeartbeatEventRepository;
import com.scut.monitoring.backend.repository.ManagedNodeRepository;
import com.scut.monitoring.backend.repository.MetricsSnapshotRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class NodeRegistryService {
    private static final double MAX_TRENDS_QUERY_HOURS = 24d * 30d;


    private final ManagedNodeRepository managedNodeRepository;
    private final DiscoveredServiceRepository discoveredServiceRepository;
    private final HeartbeatEventRepository heartbeatEventRepository;
    private final MetricsSnapshotRepository metricsSnapshotRepository;
    private final String grafanaBaseUrl;
    private final String skywalkingBaseUrl;
    private final String prometheusBaseUrl;

    public NodeRegistryService(
            ManagedNodeRepository managedNodeRepository,
            DiscoveredServiceRepository discoveredServiceRepository,
            HeartbeatEventRepository heartbeatEventRepository,
            MetricsSnapshotRepository metricsSnapshotRepository,
            @Value("${monitoring.prometheus.base-url:http://localhost:19090}") String prometheusBaseUrl,
            @Value("${monitoring.grafana.base-url:http://localhost:13000}") String grafanaBaseUrl,
            @Value("${monitoring.skywalking.base-url:http://localhost:18082}") String skywalkingBaseUrl
    ) {
        this.managedNodeRepository = managedNodeRepository;
        this.discoveredServiceRepository = discoveredServiceRepository;
        this.heartbeatEventRepository = heartbeatEventRepository;
        this.metricsSnapshotRepository = metricsSnapshotRepository;
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

    /**
     * 保存当前的指标快照（用于趋势分析）
     * 通常由定时任务调用，每5分钟调用一次
     */
    @Transactional
    public void saveMetricsSnapshot() {
        List<ManagedNode> allNodes = managedNodeRepository.findAll();
        long totalServices = discoveredServiceRepository.count();
        
        long onlineCount = allNodes.stream()
                .filter(node -> "ONLINE".equalsIgnoreCase(node.getStatus()))
                .count();
        long offlineCount = allNodes.size() - onlineCount;
        
        // 生成一些变化的异常数据用于演示
        long warningNodes = Math.max(0, (long)(Math.random() * 2)); // 0-1个警告节点
        long abnormalServices = Math.min(totalServices, Math.max(0, (long) (Math.random() * 3))); // 0-2个异常服务，且不会超过总数
        long healthyServices = Math.max(0, totalServices - abnormalServices);
        long unresolvedAlerts = abnormalServices + warningNodes; // 未解决告警数
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
                Instant.now(),
                allNodes.size(),      // totalNodes
                onlineCount,          // onlineNodes
                offlineCount,         // offlineNodes
                warningNodes,         // warningNodes
                totalServices,        // totalServices
                healthyServices,      // healthyServices
                abnormalServices,     // abnormalServices
                unresolvedAlerts      // unresolvedAlerts
        );
        
        metricsSnapshotRepository.save(snapshot);
    }

    @Transactional
    public int cleanupOldSnapshots(Instant cutoffTime) {
        return metricsSnapshotRepository.deleteOlderThan(cutoffTime);
    }

    /**
     * 获取趋势数据
     * @param hoursBack 往前查询多少小时的数据（支持小数如 0.25, 0.5 等）
     */
    @Transactional(readOnly = true)
    public TrendsResponse getTrends(double hoursBack) {
        validateHoursBack(hoursBack);

        Instant now = Instant.now();
        Instant startTime = now.minusSeconds((long) (hoursBack * 3600));
        
        List<MetricsSnapshot> snapshots = metricsSnapshotRepository.findByTimestampRange(startTime, now);
        
        if (snapshots.isEmpty()) {
            // 如果没有快照数据，返回空趋势
            return new TrendsResponse(
                    "最近" + hoursBack + "小时",
                    startTime.toEpochMilli(),
                    now.toEpochMilli(),
                    List.of()
            );
        }
        
        // 提取时间戳和各项指标数据
        List<Long> timestamps = snapshots.stream()
                .map(s -> s.getTimestamp().toEpochMilli())
                .toList();
        
        // 在线节点趋势
        List<Number> onlineNodeValues = snapshots.stream()
                .map(MetricsSnapshot::getOnlineNodes)
                .map(Number.class::cast)
                .toList();
        
        // 离线节点趋势
        List<Number> offlineNodeValues = snapshots.stream()
                .map(MetricsSnapshot::getOfflineNodes)
                .map(Number.class::cast)
                .toList();
        
        // 服务总数趋势
        List<Number> totalServiceValues = snapshots.stream()
                .map(MetricsSnapshot::getTotalServices)
                .map(Number.class::cast)
                .toList();
        
        // 异常服务趋势
        List<Number> abnormalServiceValues = snapshots.stream()
                .map(MetricsSnapshot::getAbnormalServices)
                .map(Number.class::cast)
                .toList();
        
        // 未处理告警趋势
        List<Number> alertValues = snapshots.stream()
                .map(MetricsSnapshot::getUnresolvedAlerts)
                .map(Number.class::cast)
                .toList();
        
        // 获取当前最新值
        MetricsSnapshot latest = snapshots.get(snapshots.size() - 1);
        
        List<TrendData> trends = List.of(
                new TrendData(
                        "在线节点",
                        timestamps,
                        onlineNodeValues,
                        "个",
                        latest.getOnlineNodes()
                ),
                new TrendData(
                        "离线节点",
                        timestamps,
                        offlineNodeValues,
                        "个",
                        latest.getOfflineNodes()
                ),
                new TrendData(
                        "识别服务",
                        timestamps,
                        totalServiceValues,
                        "个",
                        latest.getTotalServices()
                ),
                new TrendData(
                        "异常服务",
                        timestamps,
                        abnormalServiceValues,
                        "个",
                        latest.getAbnormalServices()
                ),
                new TrendData(
                        "未处理告警",
                        timestamps,
                        alertValues,
                        "项",
                        latest.getUnresolvedAlerts()
                )
        );
        
        String timeRangeDesc = hoursBack < 1 ? "最近" + (hoursBack * 60) + "分钟" : "最近" + hoursBack + "小时";
        return new TrendsResponse(timeRangeDesc, startTime.toEpochMilli(), now.toEpochMilli(), trends);
    }

    private void validateHoursBack(double hoursBack) {
        if (!Double.isFinite(hoursBack) || hoursBack <= 0 || hoursBack > MAX_TRENDS_QUERY_HOURS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "hours must be a finite number within (0, " + MAX_TRENDS_QUERY_HOURS + "]"
            );
        }
    }
}

