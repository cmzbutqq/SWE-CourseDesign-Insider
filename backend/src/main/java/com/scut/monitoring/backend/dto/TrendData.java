package com.scut.monitoring.backend.dto;

import java.util.List;

/**
 * 趋势数据DTO - 包含一个时间段内的指标数据点
 */
public record TrendData(
    String metricName,           // 指标名称（如 "在线节点数", "服务总数"）
    List<Long> timestamps,       // 时间戳列表（毫秒）
    List<Number> values,         // 对应时间点的数值
    String unit,                 // 单位（如 "个", "项"）
    Number currentValue          // 当前值
) {}
