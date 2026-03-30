package com.scut.monitoring.backend.service;

import com.scut.monitoring.backend.dto.AgentRegisterRequest;
import com.scut.monitoring.backend.dto.DiscoveredServicePayload;
import com.scut.monitoring.backend.dto.HeartbeatRequest;
import com.scut.monitoring.backend.model.HeartbeatEvent;
import com.scut.monitoring.backend.model.ManagedNode;
import com.scut.monitoring.backend.repository.DiscoveredServiceRepository;
import com.scut.monitoring.backend.repository.HeartbeatEventRepository;
import com.scut.monitoring.backend.repository.ManagedNodeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeRegistryServiceTest {

    private final ManagedNodeRepository managedNodeRepository = mock(ManagedNodeRepository.class);
    private final DiscoveredServiceRepository discoveredServiceRepository = mock(DiscoveredServiceRepository.class);
    private final HeartbeatEventRepository heartbeatEventRepository = mock(HeartbeatEventRepository.class);

    private final NodeRegistryService nodeRegistryService = new NodeRegistryService(
            managedNodeRepository,
            discoveredServiceRepository,
            heartbeatEventRepository,
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

        var response = nodeRegistryService.heartbeat(new HeartbeatRequest("middleware-node", "ONLINE"));

        ArgumentCaptor<HeartbeatEvent> captor = ArgumentCaptor.forClass(HeartbeatEvent.class);
        verify(heartbeatEventRepository).save(captor.capture());
        assertThat(captor.getValue().getNode()).isSameAs(node);
        assertThat(captor.getValue().getStatus()).isEqualTo("ONLINE");
        assertThat(response.nodeName()).isEqualTo("middleware-node");
        assertThat(response.status()).isEqualTo("ONLINE");
    }
}
