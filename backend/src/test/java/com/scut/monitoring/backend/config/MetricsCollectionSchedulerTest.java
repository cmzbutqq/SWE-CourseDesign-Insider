package com.scut.monitoring.backend.config;

import com.scut.monitoring.backend.service.NodeRegistryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsCollectionSchedulerTest {

    private final NodeRegistryService nodeRegistryService = mock(NodeRegistryService.class);

    @Test
    void markTimedOutNodesOfflineShouldDelegateToNodeRegistryService() {
        MetricsCollectionScheduler scheduler = new MetricsCollectionScheduler(nodeRegistryService, 30);

        scheduler.markTimedOutNodesOffline();

        verify(nodeRegistryService).markTimedOutNodesOffline();
    }

    @Test
    void cleanupOldSnapshotsShouldDeleteSnapshotsOlderThanRetentionWindow() {
        when(nodeRegistryService.cleanupOldSnapshots(org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(5);
        MetricsCollectionScheduler scheduler = new MetricsCollectionScheduler(nodeRegistryService, 30);

        Instant before = Instant.now();
        scheduler.cleanupOldSnapshots();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(nodeRegistryService).cleanupOldSnapshots(cutoffCaptor.capture());

        Duration elapsedToBefore = Duration.between(cutoffCaptor.getValue(), before);
        Duration elapsedToAfter = Duration.between(cutoffCaptor.getValue(), after);
        assertThat(elapsedToBefore.toDays()).isBetween(29L, 31L);
        assertThat(elapsedToAfter.toDays()).isBetween(29L, 31L);
    }
}
