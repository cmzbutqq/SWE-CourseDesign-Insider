package com.scut.monitoring.backend.repository;

import com.scut.monitoring.backend.model.DiscoveredService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscoveredServiceRepository extends JpaRepository<DiscoveredService, Long> {
    List<DiscoveredService> findAllByOrderByServiceTypeAscServiceNameAsc();
}
