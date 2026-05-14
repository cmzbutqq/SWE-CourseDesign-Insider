package com.scut.monitoring.backend.service;

import com.scut.monitoring.backend.dto.AgentRegisterRequest;
import com.scut.monitoring.backend.dto.DiscoveredServicePayload;
import com.scut.monitoring.backend.dto.HeartbeatRequest;
import com.scut.monitoring.backend.model.HeartbeatEvent;
import com.scut.monitoring.backend.model.ManagedNode;
import com.scut.monitoring.backend.model.MetricsSnapshot;
import com.scut.monitoring.backend.repository.DiscoveredServiceRepository;
import com.scut.monitoring.backend.repository.HeartbeatEventRepository;
import com.scut.monitoring.backend.repository.ManagedNodeRepository;
import com.scut.monitoring.backend.repository.MetricsSnapshotRepository;
import com.scut.monitoring.backend.repository.NodeMetricsRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
            "http://localhost:19090",
            "http://localhost:13000",
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
                        new DiscoveredServicePayload("sample-service", "SPRING_BOOT", 8081, "java", "/actuator/prometheus"),
                        new DiscoveredServicePayload("node-exporter", "NODE_EXPORTER", 9100, "node_exporter", "/metrics")
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
        assertThat(response.services()).hasSize(2);
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
    void saveMetricsSnapshotShouldNeverPersistNegativeHealthyServices() {
        when(managedNodeRepository.findAll()).thenReturn(List.of());
        when(discoveredServiceRepository.count()).thenReturn(0L);

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
    void cleanupOldSnapshotsShouldDelegateDeletionToRepository() {
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        when(metricsSnapshotRepository.deleteOlderThan(cutoff)).thenReturn(8);

        int deleted = nodeRegistryService.cleanupOldSnapshots(cutoff);

        assertThat(deleted).isEqualTo(8);
        verify(metricsSnapshotRepository).deleteOlderThan(cutoff);
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
}
