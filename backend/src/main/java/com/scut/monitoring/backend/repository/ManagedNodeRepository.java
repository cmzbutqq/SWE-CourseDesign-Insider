package com.scut.monitoring.backend.repository;

import com.scut.monitoring.backend.model.ManagedNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ManagedNodeRepository extends JpaRepository<ManagedNode, Long> {
    Optional<ManagedNode> findByNodeName(String nodeName);

    @Modifying
    @Query("update ManagedNode node set node.lastHeartbeatAt = node.lastSeenAt where node.lastHeartbeatAt is null and node.lastSeenAt is not null")
    int backfillMissingLastHeartbeatAt();
}
