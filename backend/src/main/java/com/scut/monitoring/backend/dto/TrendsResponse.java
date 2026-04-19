package com.scut.monitoring.backend.dto;

import java.util.List;

/**
 * 趋势响应DTO - 包含多个趋势图的数据
 */
public record TrendsResponse(
    String timeRange,            // 时间范围描述（如 "最近1小时"）
    long startTime,              // 起始时间戳（毫秒）
    long endTime,                // 结束时间戳（毫秒）
    List<TrendData> trends       // 趋势数据列表
) {}
