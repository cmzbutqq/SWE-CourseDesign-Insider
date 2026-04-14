package com.scut.monitoring.backend.dto;

/**
 * 数值分组统计
 */
public record CounterGroupDTO(
        long total,
        long online,      // 仅节点使用
        long offline,     // 仅节点使用
        long warning,     // 仅节点使用
        long healthy,     // 仅服务使用
        long abnormal     // 仅服务使用
) {
}
