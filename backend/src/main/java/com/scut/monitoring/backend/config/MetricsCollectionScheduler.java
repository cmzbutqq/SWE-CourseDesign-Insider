package com.scut.monitoring.backend.config;

import com.scut.monitoring.backend.service.NodeRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@EnableScheduling
public class MetricsCollectionScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectionScheduler.class);

    private final NodeRegistryService nodeRegistryService;
    private final long snapshotRetentionDays;
    private final long nodeMetricsRetentionDays;

    public MetricsCollectionScheduler(
            NodeRegistryService nodeRegistryService,
            @Value("${monitoring.metrics.snapshot-retention-days:30}") long snapshotRetentionDays,
            @Value("${monitoring.metrics.node-retention-days:7}") long nodeMetricsRetentionDays
    ) {
        this.nodeRegistryService = nodeRegistryService;
        this.snapshotRetentionDays = snapshotRetentionDays;
        this.nodeMetricsRetentionDays = nodeMetricsRetentionDays;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void collectMetricsSnapshot() {
        try {
            logger.info("Starting metrics snapshot collection...");
            nodeRegistryService.saveMetricsSnapshot();
            logger.info("Metrics snapshot saved successfully");
        } catch (Exception e) {
            logger.error("Error collecting metrics snapshot", e);
            e.printStackTrace();
        }
    }

    @Scheduled(cron = "0 0 2 * * MON")
    public void cleanupOldSnapshots() {
        try {
            logger.info("Starting old snapshots cleanup...");
            Instant snapshotCutoffTime = Instant.now().minus(snapshotRetentionDays, ChronoUnit.DAYS);
            int deletedSnapshotCount = nodeRegistryService.cleanupOldSnapshots(snapshotCutoffTime);
            logger.info("Old snapshots cleanup finished, deleted {} rows older than {}", deletedSnapshotCount, snapshotCutoffTime);

            Instant nodeMetricsCutoffTime = Instant.now().minus(nodeMetricsRetentionDays, ChronoUnit.DAYS);
            int deletedNodeMetricsCount = nodeRegistryService.cleanupOldNodeMetrics(nodeMetricsCutoffTime);
            logger.info("Old node metrics cleanup finished, deleted {} rows older than {}", deletedNodeMetricsCount, nodeMetricsCutoffTime);
        } catch (Exception e) {
            logger.error("Error cleaning up old snapshots", e);
        }
    }
}
