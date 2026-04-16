package com.scut.monitoring.backend.dto;

import java.util.List;

/**
 * 首页聚合总览数据
 */
public record OverviewResponse(
        // 第一层：KPI 统计数据
        CounterGroupDTO nodes,                  // 节点统计：total, online, offline, warning
        CounterGroupDTO services,               // 服务统计：total, healthy, abnormal
        long unresolvedAlerts,                  // 未处理告警数
        
        // 第二层：异常前置数据
        AnomaliesDTO anomalies,                 // 异常节点和服务列表
        
        // 第三层：快捷链接（保留兼容性）
        List<String> quickLinks
) {
}

