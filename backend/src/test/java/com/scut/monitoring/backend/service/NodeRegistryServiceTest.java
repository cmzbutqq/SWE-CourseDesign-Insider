package com.scut.monitoring.backend.service;

import com.scut.monitoring.backend.dto.AgentRegisterRequest;
import com.scut.monitoring.backend.dto.DiscoveredServicePayload;
import com.scut.monitoring.backend.dto.HeartbeatRequest;
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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NodeRegistryServiceTest {

    private final ManagedNodeRepository managedNodeRepository = mock(ManagedNodeRepository.class);
    private final DiscoveredServiceRepository discoveredServiceRepository = mock(DiscoveredServiceRepository.class);
    private final HeartbeatEventRepository heartbeatEventRepository = mock(HeartbeatEventRepository.class);
    private final MetricsSnapshotRepository metricsSnapshotRepository = mock(MetricsSnapshotRepository.class);
    private final NodeMetricsRepository nodeMetricsRepository = mock(NodeMetricsRepository.class);

    private final NodeRegistryService nodeRegistryService = new NodeRegistryService(
            managedNodeRepository,
            discoveredServiceRepository,
            heartbeatEventRepository,
            metricsSnapshotRepository,
            nodeMetricsRepository,
            "http://localhost:15173/prometheus",
            "http://localhost:15173/grafana",
            "http://localhost:18082"
    );

    @Test
    void registerNodeShouldPersistServicesOnNode() {
        AgentRegisterRequest request = new AgentRegisterRequest(
                "app-node",
                "app-node-host",
                "172.20.0.10",
                "linux",
                "0.1.0",
                List.of(
                        new DiscoveredServicePayload("sample-service", "SPRING_BOOT", 8081, "java", "/actuator/prometheus", 8081),
                        new DiscoveredServicePayload("node-exporter", "NODE_EXPORTER", 9100, "node_exporter", "/metrics", 9100)
                )
        );

        when(managedNodeRepository.findByNodeName("app-node")).thenReturn(Optional.empty());
        when(managedNodeRepository.save(org.mockito.ArgumentMatchers.any(ManagedNode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = nodeRegistryService.registerNode(request);

        ArgumentCaptor<ManagedNode> captor = ArgumentCaptor.forClass(ManagedNode.class);
        verify(managedNodeRepository).save(captor.capture());
        ManagedNode savedNode = captor.getValue();

        assertThat(savedNode.getStatus()).isEqualTo("ONLINE");
        assertThat(savedNode.getServices()).hasSize(2);
        assertThat(savedNode.getServices())
                .extracting(service -> service.getNode().getNodeName())
                .containsOnly("app-node");
        assertThat(savedNode.getServices())
                .extracting(DiscoveredService::getMetricsPort)
                .containsExactlyInAnyOrder(8081, 9100);
        assertThat(response.services()).hasSize(2);
    }

    @Test
    void registerNodeShouldAllowEmptyServiceList() {
        AgentRegisterRequest request = new AgentRegisterRequest(
                "empty-node",
                "empty-node-host",
                "172.20.0.30",
                "linux",
                "0.1.0",
                List.of()
        );

        when(managedNodeRepository.findByNodeName("empty-node")).thenReturn(Optional.empty());
        when(managedNodeRepository.save(org.mockito.ArgumentMatchers.any(ManagedNode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = nodeRegistryService.registerNode(request);

        ArgumentCaptor<ManagedNode> captor = ArgumentCaptor.forClass(ManagedNode.class);
        verify(managedNodeRepository).save(captor.capture());
        assertThat(captor.getValue().getServices()).isEmpty();
        assertThat(response.nodeName()).isEqualTo("empty-node");
        assertThat(response.services()).isEmpty();
    }

    @Test
    void registerNodeShouldReportWaitingHeartbeatAsWarning() {
        AgentRegisterRequest request = new AgentRegisterRequest(
                "app-node",
                "app-node-host",
                "172.20.0.10",
                "linux",
                "0.1.0",
                List.of(new DiscoveredServicePayload("nginx", "NGINX", 80, "nginx", "/metrics", 9113))
        );

        when(managedNodeRepository.findByNodeName("app-node")).thenReturn(Optional.empty());
        when(managedNodeRepository.save(org.mockito.ArgumentMatchers.any(ManagedNode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = nodeRegistryService.registerNode(request);

        assertThat(response.status()).isEqualTo("WARNING");
        assertThat(response.statusSummary()).isEqualTo("等待心跳上报");
        assertThat(response.lastHeartbeatAt()).isNull();
    }

    @Test
    void registerNodeShouldNotRefreshLastHeartbeatAt() {
        ManagedNode existingNode = createNode("app-node", "ONLINE");
        Instant staleHeartbeat = Instant.now().minusSeconds(120);
        existingNode.setLastHeartbeatAt(staleHeartbeat);

        AgentRegisterRequest request = new AgentRegisterRequest(
                "app-node",
                "app-node-host",
                "172.20.0.10",
                "linux",
                "0.1.1",
                List.of()
        );

        when(managedNodeRepository.findByNodeName("app-node")).thenReturn(Optional.of(existingNode));
        when(managedNodeRepository.save(org.mockito.ArgumentMatchers.any(ManagedNode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeMetricsRepository.findTopByNodeOrderByCollectedAtDesc(existingNode)).thenReturn(Optional.empty());

        var response = nodeRegistryService.registerNode(request);

        assertThat(existingNode.getLastHeartbeatAt()).isEqualTo(staleHeartbeat);
        assertThat(response.lastHeartbeatAt()).isEqualTo(staleHeartbeat);
        assertThat(response.statusSummary()).isEqualTo("心跳超时，可能失联");
        assertThat(response.status()).isEqualTo("WARNING");
    }

    @Test
    void heartbeatShouldSaveEventAndReturnUpdatedStatus() {
        ManagedNode node = new ManagedNode();
        node.setNodeName("middleware-node");
        node.setHostname("middleware-host");
        node.setIpAddress("172.20.0.20");
        node.setOsName("linux");
        node.setAgentVersion("0.1.0");
        node.setStatus("ONLINE");

        when(managedNodeRepository.findByNodeName("middleware-node")).thenReturn(Optional.of(node));

        var response = nodeRegistryService.heartbeat(new HeartbeatRequest("middleware-node", "ONLINE", null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<HeartbeatEvent> captor = ArgumentCaptor.forClass(HeartbeatEvent.class);
        verify(heartbeatEventRepository).save(captor.capture());
        assertThat(captor.getValue().getNode()).isSameAs(node);
        assertThat(captor.getValue().getStatus()).isEqualTo("ONLINE");
        assertThat(response.nodeName()).isEqualTo("middleware-node");
        assertThat(response.status()).isEqualTo("ONLINE");
    }

    @Test
    void heartbeatShouldPersistSanitizedHostMetrics() {
        ManagedNode node = new ManagedNode();
        node.setNodeName("app-node");
        node.setHostname("app-host");
        node.setIpAddress("172.20.0.10");
        node.setOsName("linux");
        node.setAgentVersion("0.1.0");
        node.setStatus("ONLINE");

        when(managedNodeRepository.findByNodeName("app-node")).thenReturn(Optional.of(node));

        nodeRegistryService.heartbeat(new HeartbeatRequest(
                "app-node",
                "WARNING",
                120d,
                55.5d,
                2048L,
                1024L,
                -10d,
                40L,
                10L,
                Double.POSITIVE_INFINITY,
                1.25d
        ));

        ArgumentCaptor<NodeMetrics> metricsCaptor = ArgumentCaptor.forClass(NodeMetrics.class);
        verify(nodeMetricsRepository).save(metricsCaptor.capture());
        NodeMetrics metrics = metricsCaptor.getValue();
        assertThat(metrics.getNode()).isSameAs(node);
        assertThat(metrics.getCpuUsage()).isEqualTo(100d);
        assertThat(metrics.getMemoryUsage()).isEqualTo(55.5d);
        assertThat(metrics.getDiskUsage()).isNull();
        assertThat(metrics.getNetworkRxMbps()).isNull();
        assertThat(metrics.getNetworkTxMbps()).isEqualTo(1.25d);
    }

    @Test
    void heartbeatShouldMergePartialMetricsWithPreviousSnapshot() {
        ManagedNode node = createNode("app-node", "ONLINE");
        NodeMetrics previousMetrics = new NodeMetrics();
        previousMetrics.setNode(node);
        previousMetrics.setCollectedAt(Instant.now().minusSeconds(10));
        previousMetrics.setCpuUsage(25d);
        previousMetrics.setMemoryUsage(40d);
        previousMetrics.setMemoryTotalMb(4096L);
        previousMetrics.setMemoryUsedMb(1638L);
        previousMetrics.setDiskUsage(70d);
        previousMetrics.setDiskTotalGb(80L);
        previousMetrics.setDiskUsedGb(56L);
        previousMetrics.setNetworkRxMbps(1.5d);
        previousMetrics.setNetworkTxMbps(2.5d);

        when(managedNodeRepository.findByNodeName("app-node")).thenReturn(Optional.of(node));
        when(nodeMetricsRepository.findTopByNodeOrderByCollectedAtDesc(node)).thenReturn(Optional.of(previousMetrics));

        nodeRegistryService.heartbeat(new HeartbeatRequest(
                "app-node",
                "ONLINE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                3.5d,
                null
        ));

        ArgumentCaptor<NodeMetrics> metricsCaptor = ArgumentCaptor.forClass(NodeMetrics.class);
        verify(nodeMetricsRepository).save(metricsCaptor.capture());
        NodeMetrics metrics = metricsCaptor.getValue();
        assertThat(metrics.getCpuUsage()).isEqualTo(25d);
        assertThat(metrics.getMemoryUsage()).isEqualTo(40d);
        assertThat(metrics.getMemoryTotalMb()).isEqualTo(4096L);
        assertThat(metrics.getDiskUsage()).isEqualTo(70d);
        assertThat(metrics.getNetworkRxMbps()).isEqualTo(3.5d);
        assertThat(metrics.getNetworkTxMbps()).isEqualTo(2.5d);
    }

    @Test
    void getNodeShouldBuildUsableObservationLinks() {
        ManagedNode node = new ManagedNode();
        node.setNodeName("app-node");
        node.setHostname("app-host");
        node.setIpAddress("172.20.0.10");
        node.setOsName("linux");
        node.setAgentVersion("0.1.0");
        node.setStatus("ONLINE");
        node.setLastSeenAt(Instant.now());

        when(managedNodeRepository.findById(1L)).thenReturn(Optional.of(node));
        when(nodeMetricsRepository.findTopByNodeOrderByCollectedAtDesc(node)).thenReturn(Optional.empty());

        var response = nodeRegistryService.getNode(1L);

        assertThat(response.quickLinks())
                .extracting(link -> link.url())
                .contains(
                        "http://localhost:15173/grafana/d/node-detail/node-detail?var-job=app-node",
                        "http://localhost:15173/prometheus/graph?g0.expr=up%7Bjob%3D%22app-node%22%7D",
                        "http://localhost:18082/General-Service/Services"
                );
    }

    @Test
    void getNodeShouldDescribeWarningStatusWithoutCallingItOffline() {
        ManagedNode node = createNode("app-node", "WARNING");
        when(managedNodeRepository.findById(1L)).thenReturn(Optional.of(node));
        when(nodeMetricsRepository.findTopByNodeOrderByCollectedAtDesc(node)).thenReturn(Optional.empty());

        var response = nodeRegistryService.getNode(1L);

        assertThat(response.statusSummary()).isEqualTo("节点处于告警状态");
    }

    @Test
    void overviewShouldCountWarningNodesSeparatelyFromOfflineNodes() {
        ManagedNode onlineNode = createNode("app-node", "ONLINE");
        ManagedNode warningNode = createNode("middleware-node", "WARNING");
        ManagedNode offlineNode = createNode("db-node", "OFFLINE");
        when(managedNodeRepository.findAll()).thenReturn(List.of(onlineNode, warningNode, offlineNode));
        when(discoveredServiceRepository.count()).thenReturn(0L);
        when(discoveredServiceRepository.findAbnormalServicesWithNode()).thenReturn(List.of());

        var response = nodeRegistryService.overview();

        assertThat(response.nodes().online()).isEqualTo(1);
        assertThat(response.nodes().warning()).isEqualTo(1);
        assertThat(response.nodes().offline()).isEqualTo(1);
        assertThat(response.anomalies().nodes())
                .extracting(node -> node.reason())
                .containsExactlyInAnyOrder("WARNING", "OFFLINE");
    }

    @Test
    void overviewShouldTreatTimedOutOnlineNodeAsWarning() {
        ManagedNode timedOutNode = createNode("app-node", "ONLINE");
        timedOutNode.setLastHeartbeatAt(Instant.now().minusSeconds(120));
        when(managedNodeRepository.findAll()).thenReturn(List.of(timedOutNode));
        when(discoveredServiceRepository.count()).thenReturn(0L);
        when(discoveredServiceRepository.findAbnormalServicesWithNode()).thenReturn(List.of());

        var response = nodeRegistryService.overview();

        assertThat(response.nodes().online()).isZero();
        assertThat(response.nodes().warning()).isEqualTo(1);
        assertThat(response.unresolvedAlerts()).isEqualTo(1);
        assertThat(response.anomalies().nodes())
                .extracting(node -> node.reason())
                .containsExactly("HEARTBEAT_TIMEOUT");
    }

    @Test
    void overviewShouldTreatOnlineNodeWithoutHeartbeatAsWarning() {
        ManagedNode waitingNode = createNode("app-node", "ONLINE");
        waitingNode.setLastSeenAt(Instant.now().minusSeconds(300));
        waitingNode.setLastHeartbeatAt(null);
        when(managedNodeRepository.findAll()).thenReturn(List.of(waitingNode));
        when(discoveredServiceRepository.count()).thenReturn(0L);
        when(discoveredServiceRepository.findAbnormalServicesWithNode()).thenReturn(List.of());

        var response = nodeRegistryService.overview();

        assertThat(response.nodes().online()).isZero();
        assertThat(response.nodes().warning()).isEqualTo(1);
        assertThat(response.anomalies().nodes())
                .extracting(node -> node.reason())
                .containsExactly("NO_HEARTBEAT");
        assertThat(response.anomalies().nodes().get(0).durationSeconds()).isGreaterThan(0);
    }

    @Test
    void overviewShouldCountServicesWithoutScrapeEndpointAsAbnormalAlerts() {
        ManagedNode middlewareNode = createNode("middleware-node", "ONLINE");
        DiscoveredService nginx = createService(middlewareNode, "nginx", "NGINX", null);
        DiscoveredService redis = createService(middlewareNode, "redis", "REDIS", " ");
        middlewareNode.getServices().add(nginx);
        middlewareNode.getServices().add(redis);
        middlewareNode.getServices().add(createService(middlewareNode, "node-exporter", "NODE_EXPORTER", "/metrics"));
        when(managedNodeRepository.findAll()).thenReturn(List.of(middlewareNode));
        when(discoveredServiceRepository.count()).thenReturn(3L);
        when(discoveredServiceRepository.findAbnormalServicesWithNode()).thenReturn(List.of(nginx, redis));

        var response = nodeRegistryService.overview();

        assertThat(response.services().total()).isEqualTo(3);
        assertThat(response.services().healthy()).isEqualTo(1);
        assertThat(response.services().abnormal()).isEqualTo(2);
        assertThat(response.unresolvedAlerts()).isEqualTo(2);
        assertThat(response.anomalies().services())
                .extracting(service -> service.serviceName())
                .containsExactly("nginx", "redis");
        assertThat(response.anomalies().services())
                .allSatisfy(service -> {
                    assertThat(service.status()).isEqualTo("ABNORMAL");
                    assertThat(service.errorType()).isEqualTo("NO_SCRAPE_ENDPOINT");
                    assertThat(service.nodeName()).isEqualTo("middleware-node");
                });
    }

    @Test
    void listNodesShouldExposeLastSeenAndLastHeartbeatSeparately() {
        ManagedNode waitingNode = createNode("app-node", "ONLINE");
        Instant lastSeenAt = Instant.now().minusSeconds(30);
        waitingNode.setLastSeenAt(lastSeenAt);
        waitingNode.setLastHeartbeatAt(null);
        when(managedNodeRepository.findAll()).thenReturn(List.of(waitingNode));

        var response = nodeRegistryService.listNodes(null, null, null, "name");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).status()).isEqualTo("WARNING");
        assertThat(response.get(0).lastSeenAt()).isEqualTo(lastSeenAt);
        assertThat(response.get(0).lastHeartbeatAt()).isNull();
    }

    @Test
    void listServicesShouldExposeNodeIdForNodeDetailNavigation() throws Exception {
        ManagedNode node = createNode("app-node", "ONLINE");
        ReflectionTestUtils.setField(node, "id", 42L);
        DiscoveredService service = createService(node, "nginx", "NGINX", "/metrics");

        when(discoveredServiceRepository.findAllByOrderByServiceTypeAscServiceNameAsc()).thenReturn(List.of(service));

        var response = nodeRegistryService.listServices();

        assertThat(response).hasSize(1);
        Object nodeId = response.get(0).getClass().getMethod("nodeId").invoke(response.get(0));
        assertThat(nodeId).isEqualTo(42L);
        assertThat(response.get(0).nodeName()).isEqualTo("app-node");
    }

    @Test
    void registerNodeShouldReuseMatchingServicesSoDetailIdsStayStable() {
        ManagedNode existingNode = createNode("app-node", "ONLINE");
        DiscoveredService existingService = createService(
                existingNode,
                "sample-service",
                "SPRING_BOOT",
                "/actuator/prometheus"
        );
        ReflectionTestUtils.setField(existingService, "id", 7L);
        existingService.setPort(8081);
        existingService.setProcessName("java");
        existingNode.getServices().add(existingService);

        AgentRegisterRequest request = new AgentRegisterRequest(
                "app-node",
                "app-node-host",
                "172.20.0.10",
                "linux",
                "0.1.1",
                List.of(new DiscoveredServicePayload(
                        "sample-service",
                        "SPRING_BOOT",
                        8081,
                        "java",
                        "/actuator/prometheus",
                        8081
                ))
        );

        when(managedNodeRepository.findByNodeName("app-node")).thenReturn(Optional.of(existingNode));
        when(managedNodeRepository.save(org.mockito.ArgumentMatchers.any(ManagedNode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeMetricsRepository.findTopByNodeOrderByCollectedAtDesc(existingNode)).thenReturn(Optional.empty());

        var response = nodeRegistryService.registerNode(request);

        assertThat(response.services()).hasSize(1);
        assertThat(existingNode.getServices()).hasSize(1);
        assertThat(existingNode.getServices().get(0)).isSameAs(existingService);
        assertThat(existingNode.getServices().get(0).getId()).isEqualTo(7L);
    }

    @Test
    void getServiceShouldReturnNodeContextAndObservationLinks() {
        ManagedNode node = createNode("app-node", "ONLINE");
        ReflectionTestUtils.setField(node, "id", 42L);
        node.setIpAddress("172.20.0.10");
        node.setStatus("WARNING");

        DiscoveredService service = createService(node, "sample-service", "SPRING_BOOT", " ");
        ReflectionTestUtils.setField(service, "id", 7L);
        service.setPort(8081);
        service.setMetricsPort(8081);
        service.setProcessName("java");

        when(discoveredServiceRepository.findById(7L)).thenReturn(Optional.of(service));

        var response = nodeRegistryService.getService(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.serviceName()).isEqualTo("sample-service");
        assertThat(response.serviceType()).isEqualTo("SPRING_BOOT");
        assertThat(response.port()).isEqualTo(8081);
        assertThat(response.processName()).isEqualTo("java");
        assertThat(response.metricsPath()).isNull();
        assertThat(response.metricsPort()).isEqualTo(8081);
        assertThat(response.nodeId()).isEqualTo(42L);
        assertThat(response.nodeName()).isEqualTo("app-node");
        assertThat(response.nodeIpAddress()).isEqualTo("172.20.0.10");
        assertThat(response.nodeStatus()).isEqualTo("WARNING");
        assertThat(response.metricsMissing()).isTrue();
        assertThat(response.quickLinks())
                .extracting(link -> link.url())
                .containsExactly(
                        "http://localhost:15173/grafana/d/service-detail/service-detail?var-service=sample-service&var-instance=app-node%3A8081",
                        "http://localhost:15173/prometheus/graph?g0.expr=up%7Bjob%3D%22sample-service%22%2Cinstance%3D%22app-node%3A8081%22%7D",
                        "http://localhost:18082/General-Service/Services"
                );
    }

    @Test
    void heartbeatShouldRejectUnsupportedStatusAsBadRequest() {
        ManagedNode node = new ManagedNode();
        node.setNodeName("middleware-node");
        when(managedNodeRepository.findByNodeName("middleware-node")).thenReturn(Optional.of(node));

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> nodeRegistryService.heartbeat(new HeartbeatRequest("middleware-node", "DEGRADED", null, null, null, null, null, null, null, null, null)),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode().value()).isEqualTo(400);
        verify(heartbeatEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveMetricsSnapshotShouldNeverPersistNegativeHealthyServices() {
        when(managedNodeRepository.findAll()).thenReturn(List.of());
        when(discoveredServiceRepository.count()).thenReturn(0L);
        when(discoveredServiceRepository.countAbnormalServices()).thenReturn(0L);

        nodeRegistryService.saveMetricsSnapshot();

        ArgumentCaptor<MetricsSnapshot> snapshotCaptor = ArgumentCaptor.forClass(MetricsSnapshot.class);
        verify(metricsSnapshotRepository).save(snapshotCaptor.capture());
        MetricsSnapshot snapshot = snapshotCaptor.getValue();

        assertThat(snapshot.getTotalServices()).isZero();
        assertThat(snapshot.getAbnormalServices()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.getHealthyServices()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.getHealthyServices()).isLessThanOrEqualTo(snapshot.getTotalServices());
    }

    @Test
    void saveMetricsSnapshotShouldPersistAbnormalServiceCounts() {
        ManagedNode middlewareNode = createNode("middleware-node", "ONLINE");
        middlewareNode.getServices().add(createService(middlewareNode, "mysql", "MYSQL", null));
        middlewareNode.getServices().add(createService(middlewareNode, "node-exporter", "NODE_EXPORTER", "/metrics"));
        when(managedNodeRepository.findAll()).thenReturn(List.of(middlewareNode));
        when(discoveredServiceRepository.count()).thenReturn(2L);
        when(discoveredServiceRepository.countAbnormalServices()).thenReturn(1L);

        nodeRegistryService.saveMetricsSnapshot();

        ArgumentCaptor<MetricsSnapshot> snapshotCaptor = ArgumentCaptor.forClass(MetricsSnapshot.class);
        verify(metricsSnapshotRepository).save(snapshotCaptor.capture());
        MetricsSnapshot snapshot = snapshotCaptor.getValue();

        assertThat(snapshot.getTotalServices()).isEqualTo(2);
        assertThat(snapshot.getHealthyServices()).isEqualTo(1);
        assertThat(snapshot.getAbnormalServices()).isEqualTo(1);
        assertThat(snapshot.getUnresolvedAlerts()).isEqualTo(1);
    }

    @Test
    void cleanupOldSnapshotsShouldDelegateDeletionToRepository() {
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        when(metricsSnapshotRepository.deleteOlderThan(cutoff)).thenReturn(8);

        int deleted = nodeRegistryService.cleanupOldSnapshots(cutoff);

        assertThat(deleted).isEqualTo(8);
        verify(metricsSnapshotRepository).deleteOlderThan(cutoff);
    }

    @Test
    void cleanupOldNodeMetricsShouldDelegateDeletionToRepository() {
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        when(nodeMetricsRepository.deleteOlderThan(cutoff)).thenReturn(12);

        int deleted = nodeRegistryService.cleanupOldNodeMetrics(cutoff);

        assertThat(deleted).isEqualTo(12);
        verify(nodeMetricsRepository).deleteOlderThan(cutoff);
    }

    @Test
    void backfillMissingLastHeartbeatAtShouldDelegateToRepository() {
        when(managedNodeRepository.backfillMissingLastHeartbeatAt()).thenReturn(3);

        int updated = nodeRegistryService.backfillMissingLastHeartbeatAt();

        assertThat(updated).isEqualTo(3);
        verify(managedNodeRepository).backfillMissingLastHeartbeatAt();
    }

    @Test
    void getTrendsShouldRejectOverflowHoursAsBadRequest() {
        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> nodeRegistryService.getTrends(1e308),
                ResponseStatusException.class
        );
        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(metricsSnapshotRepository);
    }

    @Test
    void getTrendsShouldRejectNonPositiveAndNonFiniteHoursAsBadRequest() {
        List<Double> invalidHours = List.of(0d, -0.5d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);

        for (Double invalidHour : invalidHours) {
            ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                    () -> nodeRegistryService.getTrends(invalidHour),
                    ResponseStatusException.class
            );
            assertThat(exception).isNotNull();
            assertThat(exception.getStatusCode().value()).isEqualTo(400);
        }

        verifyNoInteractions(metricsSnapshotRepository);
    }

    @Test
    void getTrendsShouldRejectHoursBeyondMaximumWindow() {
        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> nodeRegistryService.getTrends(721d),
                ResponseStatusException.class
        );
        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(metricsSnapshotRepository);
    }

    @Test
    void getTrendsShouldAllowHoursAtMaximumWindow() {
        when(metricsSnapshotRepository.findByTimestampRange(
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> nodeRegistryService.getTrends(720d));
        verify(metricsSnapshotRepository).findByTimestampRange(
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        verify(metricsSnapshotRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private ManagedNode createNode(String nodeName, String status) {
        ManagedNode node = new ManagedNode();
        node.setNodeName(nodeName);
        node.setHostname(nodeName + "-host");
        node.setIpAddress("172.20.0.1");
        node.setOsName("linux");
        node.setAgentVersion("0.1.0");
        node.setStatus(status);
        node.setLastSeenAt(Instant.now());
        node.setLastHeartbeatAt(Instant.now());
        return node;
    }

    private DiscoveredService createService(ManagedNode node, String serviceName, String serviceType, String metricsPath) {
        DiscoveredService service = new DiscoveredService();
        service.setNode(node);
        service.setServiceName(serviceName);
        service.setServiceType(serviceType);
        service.setPort(80);
        service.setProcessName(serviceName);
        service.setMetricsPath(metricsPath);
        return service;
    }
}
