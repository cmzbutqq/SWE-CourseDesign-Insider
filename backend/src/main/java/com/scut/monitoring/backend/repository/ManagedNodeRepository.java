package com.scut.monitoring.backend.repository;

import com.scut.monitoring.backend.model.ManagedNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagedNodeRepository extends JpaRepository<ManagedNode, Long> {
    Optional<ManagedNode> findByNodeName(String nodeName);
}
