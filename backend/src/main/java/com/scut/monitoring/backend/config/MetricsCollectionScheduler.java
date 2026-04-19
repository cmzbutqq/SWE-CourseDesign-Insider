package com.scut.monitoring.backend.config;

import com.scut.monitoring.backend.service.NodeRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MetricsCollectionScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectionScheduler.class);

    private final NodeRegistryService nodeRegistryService;

    public MetricsCollectionScheduler(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
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
            // cleanup old data
        } catch (Exception e) {
            logger.error("Error cleaning up old snapshots", e);
            e.printStackTrace();
        }
    }
}
