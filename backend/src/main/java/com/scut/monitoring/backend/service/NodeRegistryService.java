package com.scut.monitoring.backend.service;

import com.scut.monitoring.backend.dto.AgentRegisterRequest;
import com.scut.monitoring.backend.dto.AnomaliesDTO;
import com.scut.monitoring.backend.dto.AnomalyNodeDTO;
import com.scut.monitoring.backend.dto.AnomalyServiceDTO;
import com.scut.monitoring.backend.dto.CounterGroupDTO;
import com.scut.monitoring.backend.dto.HeartbeatRequest;
import com.scut.monitoring.backend.dto.HostMetricsDTO;
import com.scut.monitoring.backend.dto.NodeDetailResponse;
import com.scut.monitoring.backend.dto.NodeSummaryResponse;
import com.scut.monitoring.backend.dto.OverviewResponse;
import com.scut.monitoring.backend.dto.QuickLinkDTO;
import com.scut.monitoring.backend.dto.ServiceSummaryResponse;
import com.scut.monitoring.backend.dto.TrendData;
import com.scut.monitoring.backend.dto.TrendsResponse;
import com.scut.monitoring.backend.model.DiscoveredService;
import com.scut.monitoring.backend.model.HeartbeatEvent;
import com.scut.monitoring.backend.model.ManagedNode;
import com.scut.monitoring.backend.model.MetricsSnapshot;
import com.scut.monitoring.backend.model.NodeMetrics;
import com.scut.monitoring.backend.repository.DiscoveredServiceRepository;
import com.scut.monitoring.backend.repository.HeartbeatEventRepository;
import com.scut.monitoring.backend.repository.ManagedNodeRepository;
import com.scut.monitoring.backend.repository.MetricsSnapshotRepository;
import com.scut.monitoring.backend.repository.NodeMetricsRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class NodeRegistryService {
    private static final double MAX_TRENDS_QUERY_HOURS = 24d * 30d;
    private static final long HEARTBEAT_TIMEOUT_THRESHOLD_SECONDS = 90;
    private static final Set<String> ALLOWED_HEARTBEAT_STATUSES = Set.of("ONLINE", "OFFLINE", "WARNING");
    private static final double MAX_NETWORK_MEGABITS_PER_SECOND = 100_000d;

    private final ManagedNodeRepository managedNodeRepository;
    private final DiscoveredServiceRepository discoveredServiceRepository;
    private final HeartbeatEventRepository heartbeatEventRepository;
    private final MetricsSnapshotRepository metricsSnapshotRepository;
    private final NodeMetricsRepository nodeMetricsRepository;
    private final String grafanaBaseUrl;
    private final String skywalkingBaseUrl;
    private final String prometheusBaseUrl;

    public NodeRegistryService(
            ManagedNodeRepository managedNodeRepository,
            DiscoveredServiceRepository discoveredServiceRepository,
            HeartbeatEventRepository heartbeatEventRepository,
            MetricsSnapshotRepository metricsSnapshotRepository,
            NodeMetricsRepository nodeMetricsRepository,
            @Value("${monitoring.prometheus.base-url:http://localhost:19090}") String prometheusBaseUrl,
            @Value("${monitoring.grafana.base-url:http://localhost:13000}") String grafanaBaseUrl,
            @Value("${monitoring.skywalking.base-url:http://localhost:18082}") String skywalkingBaseUrl
    ) {
        this.managedNodeRepository = managedNodeRepository;
        this.discoveredServiceRepository = discoveredServiceRepository;
        this.heartbeatEventRepository = heartbeatEventRepository;
        this.metricsSnapshotRepository = metricsSnapshotRepository;
        this.nodeMetricsRepository = nodeMetricsRepository;
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
        String normalizedStatus = request.status().toUpperCase();
        if (!ALLOWED_HEARTBEAT_STATUSES.contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status value");
        }

        ManagedNode node = managedNodeRepository.findByNodeName(request.nodeName())
                .orElseThrow(() -> new EntityNotFoundException("Node not registered: " + request.nodeName()));

        Instant now = Instant.now();
        node.setStatus(normalizedStatus);
        node.setLastSeenAt(now);
        HeartbeatEvent event = new HeartbeatEvent();
        event.setNode(node);
        event.setStatus(normalizedStatus);
        event.setCreatedAt(now);
        heartbeatEventRepository.save(event);

        Double cpuUsage = normalizePercent(request.cpuUsage());
        Double memoryUsage = normalizePercent(request.memoryUsage());
        Long memoryTotalMb = normalizeNonNegativeLong(request.memoryTotalMb());
        Long memoryUsedMb = normalizeNonNegativeLong(request.memoryUsedMb());
        Double diskUsage = normalizePercent(request.diskUsage());
        Long diskTotalGb = normalizeNonNegativeLong(request.diskTotalGb());
        Long diskUsedGb = normalizeNonNegativeLong(request.diskUsedGb());
        Double networkRxMbps = normalizeNetworkMbps(request.networkRxMbps());
        Double networkTxMbps = normalizeNetworkMbps(request.networkTxMbps());

        if (hasMetricsData(cpuUsage, memoryUsage, memoryTotalMb, memoryUsedMb, diskUsage, diskTotalGb, diskUsedGb, networkRxMbps, networkTxMbps)) {
            NodeMetrics metrics = new NodeMetrics();
            metrics.setNode(node);
            metrics.setCollectedAt(now);
            metrics.setCpuUsage(cpuUsage);
            metrics.setMemoryUsage(memoryUsage);
            metrics.setMemoryTotalMb(memoryTotalMb);
            metrics.setMemoryUsedMb(memoryUsedMb);
            metrics.setDiskUsage(diskUsage);
            metrics.setDiskTotalGb(diskTotalGb);
            metrics.setDiskUsedGb(diskUsedGb);
            metrics.setNetworkRxMbps(networkRxMbps);
            metrics.setNetworkTxMbps(networkTxMbps);
            nodeMetricsRepository.save(metrics);
        }

        return toNodeSummary(node);
    }

    private boolean hasMetricsData(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }

    private Double normalizePercent(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            return null;
        }
        return Math.min(100d, value);
    }

    private Long normalizeNonNegativeLong(Long value) {
        if (value == null || value < 0) {
            return null;
        }
        return value;
    }

    private Double normalizeNetworkMbps(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            return null;
        }
        return Math.min(MAX_NETWORK_MEGABITS_PER_SECOND, value);
    }

    @Transactional(readOnly = true)
    public List<NodeSummaryResponse> listNodes(String status, String keyword, String serviceType, String sortBy) {
        return managedNodeRepository.findAll().stream()
                .filter(node -> status == null || status.isBlank() ||
                        node.getStatus().equalsIgnoreCase(status))
                .filter(node -> keyword == null || keyword.isBlank() ||
                        node.getNodeName().contains(keyword) ||
                        node.getIpAddress().contains(keyword))
                .filter(node -> serviceType == null || serviceType.isBlank() ||
                        node.getServices().stream()
                                .map(DiscoveredService::getServiceType)
                                .anyMatch(st -> st.equalsIgnoreCase(serviceType)))
                .sorted(getSortComparator(sortBy))
                .map(this::toNodeSummary)
                .toList();
    }

    private Comparator<ManagedNode> getSortComparator(String sortBy) {
        return switch (sortBy) {
            case "lastHeartbeat" -> Comparator.comparing(ManagedNode::getLastSeenAt).reversed();
            default -> Comparator.comparing(ManagedNode::getNodeName);
        };
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
        List<ManagedNode> allNodes = managedNodeRepository.findAll();
        long totalServices = discoveredServiceRepository.count();

        long onlineCount = allNodes.stream()
                .filter(node -> "ONLINE".equalsIgnoreCase(node.getStatus()))
                .count();
        long warningCount = allNodes.stream()
                .filter(node -> "WARNING".equalsIgnoreCase(node.getStatus()))
                .count();
        long offlineCount = allNodes.size() - onlineCount - warningCount;

        CounterGroupDTO nodesCounter = new CounterGroupDTO(
                allNodes.size(),
                onlineCount,
                offlineCount,
                warningCount,
                0,
                0
        );

        CounterGroupDTO servicesCounter = new CounterGroupDTO(
                totalServices,
                0,
                0,
                0,
                totalServices,
                0
        );

        AnomaliesDTO anomalies = buildAnomalies(allNodes);

        List<String> quickLinks = List.of(
                "Prometheus: " + prometheusBaseUrl,
                "Grafana: " + grafanaBaseUrl,
                "SkyWalking: " + skywalkingBaseUrl
        );

        return new OverviewResponse(
                nodesCounter,
                servicesCounter,
                offlineCount + warningCount,
                anomalies,
                quickLinks
        );
    }

    private AnomaliesDTO buildAnomalies(List<ManagedNode> allNodes) {
        List<AnomalyNodeDTO> anomalyNodes = allNodes.stream()
                .filter(node -> !"ONLINE".equalsIgnoreCase(node.getStatus()))
                .map(node -> new AnomalyNodeDTO(
                        node.getId(),
                        node.getNodeName(),
                        node.getStatus(),
                        buildNodeAnomalyReason(node),
                        node.getLastSeenAt(),
                        null,
                        null,
                        calculateDurationSeconds(node.getLastSeenAt())
                ))
                .sorted(Comparator.comparingLong(AnomalyNodeDTO::durationSeconds).reversed())
                .toList();

        List<AnomalyServiceDTO> anomalyServices = List.of();

        return new AnomaliesDTO(anomalyNodes, anomalyServices);
    }

    private String buildNodeAnomalyReason(ManagedNode node) {
        if ("WARNING".equalsIgnoreCase(node.getStatus())) {
            return "WARNING";
        }
        return "OFFLINE";
    }

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
        String statusSummary = buildStatusSummary(node);
        boolean heartbeatTimeoutRisk = isHeartbeatTimeoutRisk(node);
        HostMetricsDTO hostMetrics = buildHostMetrics(node);
        List<ServiceSummaryResponse> highRiskServices = buildHighRiskServices(node);
        List<QuickLinkDTO> quickLinks = buildQuickLinks(node);

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
                        .toList(),
                statusSummary,
                node.getLastSeenAt(),
                heartbeatTimeoutRisk,
                hostMetrics,
                highRiskServices,
                quickLinks
        );
    }

    private String buildStatusSummary(ManagedNode node) {
        if ("WARNING".equalsIgnoreCase(node.getStatus())) {
            return "节点处于告警状态";
        }
        if (!"ONLINE".equalsIgnoreCase(node.getStatus())) {
            return "节点离线";
        }
        long secondsSinceLastSeen = calculateDurationSeconds(node.getLastSeenAt());
        if (secondsSinceLastSeen > HEARTBEAT_TIMEOUT_THRESHOLD_SECONDS) {
            return "心跳超时，可能失联";
        }
        if (secondsSinceLastSeen > HEARTBEAT_TIMEOUT_THRESHOLD_SECONDS / 2) {
            return "心跳延迟，存在超时风险";
        }
        return "节点正常运行";
    }

    private boolean isHeartbeatTimeoutRisk(ManagedNode node) {
        if (!"ONLINE".equalsIgnoreCase(node.getStatus())) {
            return false;
        }
        long secondsSinceLastSeen = calculateDurationSeconds(node.getLastSeenAt());
        return secondsSinceLastSeen > HEARTBEAT_TIMEOUT_THRESHOLD_SECONDS / 2;
    }

    private HostMetricsDTO buildHostMetrics(ManagedNode node) {
        return nodeMetricsRepository.findTopByNodeOrderByCollectedAtDesc(node)
                .map(m -> new HostMetricsDTO(
                        m.getCpuUsage(),
                        m.getMemoryUsage(),
                        m.getMemoryTotalMb(),
                        m.getMemoryUsedMb(),
                        m.getDiskUsage(),
                        m.getDiskTotalGb(),
                        m.getDiskUsedGb(),
                        m.getNetworkRxMbps(),
                        m.getNetworkTxMbps()
                ))
                .orElse(null);
    }

    private List<ServiceSummaryResponse> buildHighRiskServices(ManagedNode node) {
        return node.getServices().stream()
                .filter(s -> s.getMetricsPath() == null || s.getMetricsPath().isBlank())
                .sorted(Comparator.comparing(DiscoveredService::getServiceName))
                .map(this::toServiceSummary)
                .toList();
    }

    private List<QuickLinkDTO> buildQuickLinks(ManagedNode node) {
        String jobName = node.getNodeName() == null ? "" : node.getNodeName();
        String prometheusExpression = "up{job=\"" + jobName + "\"}";
        return List.of(
                new QuickLinkDTO("Grafana", grafanaBaseUrl + "/d/scut-monitoring-overview/scut-monitoring-overview?var-job=" + encodeQueryValue(jobName)),
                new QuickLinkDTO("Prometheus", prometheusBaseUrl + "/graph?g0.expr=" + encodeQueryValue(prometheusExpression)),
                new QuickLinkDTO("SkyWalking", skywalkingBaseUrl + "/general-service")
        );
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    @Transactional
    public void saveMetricsSnapshot() {
        List<ManagedNode> allNodes = managedNodeRepository.findAll();
        long totalServices = discoveredServiceRepository.count();

        long onlineCount = allNodes.stream()
                .filter(node -> "ONLINE".equalsIgnoreCase(node.getStatus()))
                .count();
        long warningNodes = allNodes.stream()
                .filter(node -> "WARNING".equalsIgnoreCase(node.getStatus()))
                .count();
        long offlineCount = allNodes.size() - onlineCount - warningNodes;

        long abnormalServices = 0;
        long healthyServices = Math.max(0, totalServices - abnormalServices);
        long unresolvedAlerts = offlineCount + warningNodes + abnormalServices;

        MetricsSnapshot snapshot = new MetricsSnapshot(
                Instant.now(),
                allNodes.size(),
                onlineCount,
                offlineCount,
                warningNodes,
                totalServices,
                healthyServices,
                abnormalServices,
                unresolvedAlerts
        );

        metricsSnapshotRepository.save(snapshot);
    }

    @Transactional
    public int cleanupOldSnapshots(Instant cutoffTime) {
        return metricsSnapshotRepository.deleteOlderThan(cutoffTime);
    }

    @Transactional
    public int cleanupOldNodeMetrics(Instant cutoffTime) {
        return nodeMetricsRepository.deleteOlderThan(cutoffTime);
    }

    @Transactional(readOnly = true)
    public TrendsResponse getTrends(double hoursBack) {
        validateHoursBack(hoursBack);

        Instant now = Instant.now();
        Instant startTime = now.minusSeconds((long) (hoursBack * 3600));

        List<MetricsSnapshot> snapshots = metricsSnapshotRepository.findByTimestampRange(startTime, now);

        if (snapshots.isEmpty()) {
            return new TrendsResponse(
                    "最近" + hoursBack + "小时",
                    startTime.toEpochMilli(),
                    now.toEpochMilli(),
                    List.of()
            );
        }

        List<Long> timestamps = snapshots.stream()
                .map(s -> s.getTimestamp().toEpochMilli())
                .toList();

        List<Number> onlineNodeValues = snapshots.stream()
                .map(MetricsSnapshot::getOnlineNodes)
                .map(Number.class::cast)
                .toList();

        List<Number> offlineNodeValues = snapshots.stream()
                .map(MetricsSnapshot::getOfflineNodes)
                .map(Number.class::cast)
                .toList();

        List<Number> totalServiceValues = snapshots.stream()
                .map(MetricsSnapshot::getTotalServices)
                .map(Number.class::cast)
                .toList();

        List<Number> abnormalServiceValues = snapshots.stream()
                .map(MetricsSnapshot::getAbnormalServices)
                .map(Number.class::cast)
                .toList();

        List<Number> alertValues = snapshots.stream()
                .map(MetricsSnapshot::getUnresolvedAlerts)
                .map(Number.class::cast)
                .toList();

        MetricsSnapshot latest = snapshots.get(snapshots.size() - 1);

        List<TrendData> trends = List.of(
                new TrendData("在线节点", timestamps, onlineNodeValues, "个", latest.getOnlineNodes()),
                new TrendData("离线节点", timestamps, offlineNodeValues, "个", latest.getOfflineNodes()),
                new TrendData("识别服务", timestamps, totalServiceValues, "个", latest.getTotalServices()),
                new TrendData("异常服务", timestamps, abnormalServiceValues, "个", latest.getAbnormalServices()),
                new TrendData("未处理告警", timestamps, alertValues, "项", latest.getUnresolvedAlerts())
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
