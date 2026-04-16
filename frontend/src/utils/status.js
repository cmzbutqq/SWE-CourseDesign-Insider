/**
 * 监控系统全局状态色与文案规范
 */

export const STATUS_COLORS = {
  HEALTHY: '#52C41A',    // 绿色 - 正常
  WARNING: '#FAAD14',    // 橙色 - 需关注
  CRITICAL: '#FF4D4F',   // 红色 - 存在异常/服务中断
  OFFLINE: '#8C8C8C'     // 灰色 - 失联/离线
};

export function getNodesStatus(nodes) {
  if (nodes.offline > 0) {
    return { color: STATUS_COLORS.CRITICAL, label: '有离线', status: 'CRITICAL' };
  }
  if (nodes.warning > 0) {
    return { color: STATUS_COLORS.WARNING, label: '存在告警', status: 'WARNING' };
  }
  return { color: STATUS_COLORS.HEALTHY, label: '全部在线', status: 'HEALTHY' };
}

export function getServicesStatus(services) {
  if (services.abnormal > 0) {
    return { color: STATUS_COLORS.CRITICAL, label: '存在异常', status: 'CRITICAL' };
  }
  return { color: STATUS_COLORS.HEALTHY, label: '运行正常', status: 'HEALTHY' };
}

export function getAlertsStatus(count) {
  if (count === 0) {
    return { color: STATUS_COLORS.HEALTHY, label: '无告警', status: 'HEALTHY' };
  }
  return { color: STATUS_COLORS.CRITICAL, label: '有未处理告警', status: 'CRITICAL' };
}

/**
 * 格式化异常持续时间
 */
export function formatDuration(durationSeconds) {
  if (!durationSeconds) return '刚发生';
  const hours = Math.floor(durationSeconds / 3600);
  const minutes = Math.floor((durationSeconds % 3600) / 60);
  
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`;
  }
  return `${minutes}分钟`;
}
