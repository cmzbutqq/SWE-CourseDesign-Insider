package com.scut.monitoring.backend.repository;

import com.scut.monitoring.backend.model.ManagedNode;
import com.scut.monitoring.backend.model.NodeMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NodeMetricsRepository extends JpaRepository<NodeMetrics, Long> {
    Optional<NodeMetrics> findTopByNodeOrderByCollectedAtDesc(ManagedNode node);
}
