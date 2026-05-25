package com.scut.monitoring.backend.repository;

import com.scut.monitoring.backend.model.ManagedNode;
import com.scut.monitoring.backend.model.NodeMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NodeMetricsRepository extends JpaRepository<NodeMetrics, Long> {
    Optional<NodeMetrics> findTopByNodeOrderByCollectedAtDesc(ManagedNode node);

    @Modifying
    @Query("delete from NodeMetrics metrics where metrics.collectedAt < :cutoffTime")
    int deleteOlderThan(@Param("cutoffTime") Instant cutoffTime);
}
