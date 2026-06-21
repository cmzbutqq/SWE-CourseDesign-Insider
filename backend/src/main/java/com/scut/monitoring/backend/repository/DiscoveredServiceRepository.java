package com.scut.monitoring.backend.repository;

import com.scut.monitoring.backend.model.DiscoveredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DiscoveredServiceRepository extends JpaRepository<DiscoveredService, Long> {
    List<DiscoveredService> findAllByOrderByServiceTypeAscServiceNameAsc();

    @Query("""
            select count(s) from DiscoveredService s
            where (s.metricsPath is null or trim(s.metricsPath) = '')
              and s.serviceType <> 'NODE_EXPORTER'
            """)
    long countAbnormalServices();

    @Query("""
            select s from DiscoveredService s
            join fetch s.node
            where (s.metricsPath is null or trim(s.metricsPath) = '')
              and s.serviceType <> 'NODE_EXPORTER'
            order by s.node.nodeName asc, s.serviceName asc
            """)
    List<DiscoveredService> findAbnormalServicesWithNode();
}
