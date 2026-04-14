<template>
  <!-- 第二层：异常前置聚焦区 -->
  <div v-if="hasAnomalies" class="anomaly-section">
    <div class="anomaly-header">
      <h3>⚠️ 异常前置</h3>
      <span class="anomaly-count">{{ totalAnomalies }} 项异常</span>
    </div>

    <!-- 异常节点列表 -->
    <div v-if="anomalies.nodes.length > 0" class="anomaly-panel">
      <h4 class="panel-title">异常节点</h4>
      <div class="anomaly-list">
        <div
          v-for="node in anomalies.nodes"
          :key="`node-${node.id}`"
          class="anomaly-item"
          :class="{ 'offline': node.status !== 'ONLINE' }"
        >
          <div class="anomaly-icon">
            <span v-if="node.status !== 'ONLINE'" class="icon-offline">⊗</span>
            <span v-else class="icon-warning">!</span>
          </div>
          <div class="anomaly-info">
            <div class="anomaly-title">
              <strong>{{ node.nodeName }}</strong>
              <span class="status-tag" :style="{ backgroundColor: getNodeColor(node) }">
                {{ node.status }}
              </span>
            </div>
            <div class="anomaly-details">
              <span class="detail-item">
                <span class="label">原因：</span>{{ node.reason }}
              </span>
              <span class="detail-item">
                <span class="label">持续：</span>{{ formatDuration(node.durationSeconds) }}
              </span>
            </div>
          </div>
          <router-link
            :to="`/nodes/${node.id}`"
            class="anomaly-link"
          >
            查看详情 →
          </router-link>
        </div>
      </div>
    </div>

    <!-- 异常服务列表 -->
    <div v-if="anomalies.services.length > 0" class="anomaly-panel">
      <h4 class="panel-title">异常服务</h4>
      <div class="anomaly-list">
        <div
          v-for="service in anomalies.services"
          :key="`service-${service.id}`"
          class="anomaly-item"
        >
          <div class="anomaly-icon">
            <span class="icon-service">✕</span>
          </div>
          <div class="anomaly-info">
            <div class="anomaly-title">
              <strong>{{ service.serviceName }}</strong>
              <span class="status-tag critical">{{ service.status }}</span>
            </div>
            <div class="anomaly-details">
              <span class="detail-item">
                <span class="label">所属节点：</span>{{ service.nodeName }}
              </span>
              <span class="detail-item">
                <span class="label">错误类型：</span>{{ service.errorType }}
              </span>
            </div>
          </div>
          <router-link
            :to="`/services`"
            class="anomaly-link"
          >
            查看详情 →
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { STATUS_COLORS, formatDuration } from '../utils/status.js';

const props = defineProps({
  anomalies: {
    type: Object,
    required: true
  }
});

const hasAnomalies = computed(() => {
  return (props.anomalies?.nodes?.length || 0) > 0 ||
         (props.anomalies?.services?.length || 0) > 0;
});

const totalAnomalies = computed(() => {
  return (props.anomalies?.nodes?.length || 0) + 
         (props.anomalies?.services?.length || 0);
});

function getNodeColor(node) {
  if (node.status !== 'ONLINE') {
    return STATUS_COLORS.OFFLINE;
  }
  return STATUS_COLORS.WARNING;
}
</script>

<style scoped>
.anomaly-section {
  background: #fff5f5;
  border: 2px solid #ff4d4f;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 2rem;
  animation: slideDown 0.3s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.anomaly-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid rgba(255, 77, 79, 0.2);
}

.anomaly-header h3 {
  margin: 0;
  color: #ff4d4f;
  font-size: 1.2rem;
}

.anomaly-count {
  background: #ff4d4f;
  color: white;
  padding: 0.3rem 0.8rem;
  border-radius: 12px;
  font-size: 0.85rem;
  font-weight: 500;
}

.anomaly-panel {
  margin-bottom: 1.5rem;
}

.anomaly-panel:last-child {
  margin-bottom: 0;
}

.panel-title {
  margin: 0 0 1rem 0;
  font-size: 0.95rem;
  color: #666;
  font-weight: 600;
}

.anomaly-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.anomaly-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: white;
  border-left: 3px solid #ff4d4f;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.anomaly-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.anomaly-item.offline {
  border-left-color: #8c8c8c;
}

.anomaly-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  font-weight: bold;
}

.anomaly-item .icon-offline {
  background: #f5f5f5;
  color: #8c8c8c;
}

.anomaly-item .icon-warning {
  background: #fff7e6;
  color: #faad14;
}

.anomaly-item .icon-service {
  background: #fff1f0;
  color: #ff4d4f;
}

.anomaly-info {
  flex: 1;
  min-width: 0;
}

.anomaly-title {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  margin-bottom: 0.5rem;
}

.anomaly-title strong {
  color: #333;
  font-size: 1rem;
}

.status-tag {
  display: inline-block;
  padding: 0.2rem 0.6rem;
  border-radius: 3px;
  font-size: 0.75rem;
  color: white;
  font-weight: 500;
}

.status-tag.critical {
  background: #ff4d4f;
}

.anomaly-details {
  display: flex;
  gap: 1.2rem;
  flex-wrap: wrap;
}

.detail-item {
  font-size: 0.9rem;
  color: #666;
}

.detail-item .label {
  color: #999;
  font-weight: 500;
}

.anomaly-link {
  flex-shrink: 0;
  color: #1890ff;
  text-decoration: none;
  font-weight: 500;
  white-space: nowrap;
  transition: color 0.2s;
}

.anomaly-link:hover {
  color: #40a9ff;
}
</style>
