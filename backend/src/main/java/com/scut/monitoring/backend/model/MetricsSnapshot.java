package com.scut.monitoring.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 指标快照 - 记录系统在特定时间点的监控指标
 * 用于生成趋势图，追踪系统状态变化
 */
@Entity
@Table(name = "metrics_snapshots", indexes = {
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_timestamp_desc", columnList = "timestamp DESC")
})
public class MetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    // 节点相关指标
    @Column(nullable = false)
    private long totalNodes;

    @Column(nullable = false)
    private long onlineNodes;

    @Column(nullable = false)
    private long offlineNodes;

    @Column(nullable = false)
    private long warningNodes;

    // 服务相关指标
    @Column(nullable = false)
    private long totalServices;

    @Column(nullable = false)
    private long healthyServices;

    @Column(nullable = false)
    private long abnormalServices;

    // 告警相关指标
    @Column(nullable = false)
    private long unresolvedAlerts;

    // 构造函数
    public MetricsSnapshot() {
    }

    public MetricsSnapshot(Instant timestamp, long totalNodes, long onlineNodes, 
                          long offlineNodes, long warningNodes, long totalServices, 
                          long healthyServices, long abnormalServices, long unresolvedAlerts) {
        this.timestamp = timestamp;
        this.totalNodes = totalNodes;
        this.onlineNodes = onlineNodes;
        this.offlineNodes = offlineNodes;
        this.warningNodes = warningNodes;
        this.totalServices = totalServices;
        this.healthyServices = healthyServices;
        this.abnormalServices = abnormalServices;
        this.unresolvedAlerts = unresolvedAlerts;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getTotalNodes() {
        return totalNodes;
    }

    public void setTotalNodes(long totalNodes) {
        this.totalNodes = totalNodes;
    }

    public long getOnlineNodes() {
        return onlineNodes;
    }

    public void setOnlineNodes(long onlineNodes) {
        this.onlineNodes = onlineNodes;
    }

    public long getOfflineNodes() {
        return offlineNodes;
    }

    public void setOfflineNodes(long offlineNodes) {
        this.offlineNodes = offlineNodes;
    }

    public long getWarningNodes() {
        return warningNodes;
    }

    public void setWarningNodes(long warningNodes) {
        this.warningNodes = warningNodes;
    }

    public long getTotalServices() {
        return totalServices;
    }

    public void setTotalServices(long totalServices) {
        this.totalServices = totalServices;
    }

    public long getHealthyServices() {
        return healthyServices;
    }

    public void setHealthyServices(long healthyServices) {
        this.healthyServices = healthyServices;
    }

    public long getAbnormalServices() {
        return abnormalServices;
    }

    public void setAbnormalServices(long abnormalServices) {
        this.abnormalServices = abnormalServices;
    }

    public long getUnresolvedAlerts() {
        return unresolvedAlerts;
    }

    public void setUnresolvedAlerts(long unresolvedAlerts) {
        this.unresolvedAlerts = unresolvedAlerts;
    }
}
