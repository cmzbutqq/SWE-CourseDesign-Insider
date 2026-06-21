package com.scut.monitoring.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "discovered_services")
public class DiscoveredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String serviceType;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false)
    private String processName;

    private String metricsPath;

    private Integer metricsPort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private ManagedNode node;

    public Long getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getMetricsPath() {
        return metricsPath;
    }

    public void setMetricsPath(String metricsPath) {
        this.metricsPath = metricsPath;
    }

    public Integer getMetricsPort() {
        return metricsPort;
    }

    public void setMetricsPort(Integer metricsPort) {
        this.metricsPort = metricsPort;
    }

    public ManagedNode getNode() {
        return node;
    }

    public void setNode(ManagedNode node) {
        this.node = node;
    }
}
