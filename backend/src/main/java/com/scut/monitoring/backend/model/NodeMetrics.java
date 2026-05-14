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

import java.time.Instant;

@Entity
@Table(name = "node_metrics")
public class NodeMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private ManagedNode node;

    @Column(nullable = false)
    private Instant collectedAt;

    private Double cpuUsage;

    private Double memoryUsage;

    private Long memoryTotalMb;

    private Long memoryUsedMb;

    private Double diskUsage;

    private Long diskTotalGb;

    private Long diskUsedGb;

    private Double networkRxMbps;

    private Double networkTxMbps;

    public Long getId() {
        return id;
    }

    public ManagedNode getNode() {
        return node;
    }

    public void setNode(ManagedNode node) {
        this.node = node;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public Long getMemoryTotalMb() {
        return memoryTotalMb;
    }

    public void setMemoryTotalMb(Long memoryTotalMb) {
        this.memoryTotalMb = memoryTotalMb;
    }

    public Long getMemoryUsedMb() {
        return memoryUsedMb;
    }

    public void setMemoryUsedMb(Long memoryUsedMb) {
        this.memoryUsedMb = memoryUsedMb;
    }

    public Double getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(Double diskUsage) {
        this.diskUsage = diskUsage;
    }

    public Long getDiskTotalGb() {
        return diskTotalGb;
    }

    public void setDiskTotalGb(Long diskTotalGb) {
        this.diskTotalGb = diskTotalGb;
    }

    public Long getDiskUsedGb() {
        return diskUsedGb;
    }

    public void setDiskUsedGb(Long diskUsedGb) {
        this.diskUsedGb = diskUsedGb;
    }

    public Double getNetworkRxMbps() {
        return networkRxMbps;
    }

    public void setNetworkRxMbps(Double networkRxMbps) {
        this.networkRxMbps = networkRxMbps;
    }

    public Double getNetworkTxMbps() {
        return networkTxMbps;
    }

    public void setNetworkTxMbps(Double networkTxMbps) {
        this.networkTxMbps = networkTxMbps;
    }
}
