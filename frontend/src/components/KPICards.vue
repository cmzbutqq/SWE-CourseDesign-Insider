<template>
  <!-- 第一层：KPI 状态卡片区 -->
  <div class="kpi-cards">
    <!-- 在线节点卡片 -->
    <article class="kpi-card" :style="{ borderLeftColor: nodesStatus.color }">
      <div class="kpi-header">
        <h4>在线节点</h4>
        <div class="status-badge" :style="{ backgroundColor: nodesStatus.color }">
          {{ nodesStatus.label }}
        </div>
      </div>
      <div class="kpi-value">
        {{ data.nodes.online }}<span class="unit"> / {{ data.nodes.total }}</span>
      </div>
      <p class="kpi-subtitle">
        <span v-if="data.nodes.offline > 0" class="alert">
          ⚠ {{ data.nodes.offline }} 离线
        </span>
        <span v-else class="success">✓ 全部在线</span>
      </p>
    </article>

    <!-- 异常节点卡片 -->
    <article class="kpi-card" :style="{ borderLeftColor: warningStatus.color }">
      <div class="kpi-header">
        <h4>异常节点</h4>
        <div class="status-badge" :style="{ backgroundColor: warningStatus.color }">
          {{ warningStatus.label }}
        </div>
      </div>
      <div class="kpi-value">{{ data.nodes.warning }}</div>
      <p class="kpi-subtitle">
        <span v-if="data.nodes.warning > 0" class="alert">存在高负载</span>
        <span v-else class="success">无异常</span>
      </p>
    </article>

    <!-- 已识别服务卡片 -->
    <article class="kpi-card" :style="{ borderLeftColor: '#1890FF' }">
      <div class="kpi-header">
        <h4>已识别服务</h4>
        <div class="status-badge" style="background-color: #1890FF">中性</div>
      </div>
      <div class="kpi-value">{{ data.services.total }}</div>
      <p class="kpi-subtitle">{{ data.services.total }} 个服务</p>
    </article>

    <!-- 异常服务卡片 -->
    <article class="kpi-card" :style="{ borderLeftColor: servicesStatus.color }">
      <div class="kpi-header">
        <h4>异常服务</h4>
        <div class="status-badge" :style="{ backgroundColor: servicesStatus.color }">
          {{ servicesStatus.label }}
        </div>
      </div>
      <div class="kpi-value">{{ data.services.abnormal }}</div>
      <p class="kpi-subtitle">
        <span v-if="data.services.abnormal > 0" class="alert">存在故障</span>
        <span v-else class="success">全部正常</span>
      </p>
    </article>

    <!-- 未处理告警卡片 -->
    <article class="kpi-card" :style="{ borderLeftColor: alertStatus.color }">
      <div class="kpi-header">
        <h4>未处理告警</h4>
        <div class="status-badge" :style="{ backgroundColor: alertStatus.color }">
          {{ alertStatus.label }}
        </div>
      </div>
      <div class="kpi-value">{{ data.unresolvedAlerts }}</div>
      <p class="kpi-subtitle">
        <span v-if="data.unresolvedAlerts > 0" class="alert">需要处理</span>
        <span v-else class="success">已全部清理</span>
      </p>
    </article>

    <!-- 更新时间提示 -->
    <div class="update-time">
      <small>最后更新 {{ lastUpdateTime }}</small>
    </div>
  </div>
</template>

<script setup>
import {
  getNodesStatus,
  getServicesStatus,
  getAlertsStatus,
  STATUS_COLORS
} from '../utils/status.js';
import { ref, computed } from 'vue';

const props = defineProps({
  data: {
    type: Object,
    required: true
  }
});

const lastUpdateTime = ref(formatTime(new Date()));

const nodesStatus = computed(() => getNodesStatus(props.data.nodes));
const servicesStatus = computed(() => getServicesStatus(props.data.services));

const warningStatus = computed(() => {
  if (props.data.nodes.warning > 0) {
    return { color: STATUS_COLORS.WARNING, label: '存在告警' };
  }
  return { color: STATUS_COLORS.HEALTHY, label: '正常' };
});

const alertStatus = computed(() => getAlertsStatus(props.data.unresolvedAlerts));

function formatTime(date) {
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

// 定期更新时间显示
setInterval(() => {
  lastUpdateTime.value = formatTime(new Date());
}, 60000);
</script>

<style scoped>
.kpi-cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(180px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
  position: relative;
}

/* 响应式：屏幕小时减少列数 */
@media (max-width: 1200px) {
  .kpi-cards {
    grid-template-columns: repeat(3, minmax(150px, 1fr));
  }
}

@media (max-width: 900px) {
  .kpi-cards {
    grid-template-columns: repeat(2, minmax(150px, 1fr));
  }
}

@media (max-width: 600px) {
  .kpi-cards {
    grid-template-columns: 1fr;
  }
}

.kpi-card {
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  border-left: 4px solid;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.kpi-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.kpi-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.kpi-header h4 {
  margin: 0;
  font-size: 0.95rem;
  color: #666;
  font-weight: 500;
}

.status-badge {
  display: inline-block;
  padding: 0.3rem 0.8rem;
  border-radius: 12px;
  font-size: 0.8rem;
  color: white;
  font-weight: 500;
}

.kpi-value {
  font-size: 2.5rem;
  font-weight: bold;
  color: #333;
  margin: 0.5rem 0;
  letter-spacing: -0.5px;
}

.kpi-value .unit {
  font-size: 1rem;
  color: #999;
  font-weight: normal;
}

.kpi-subtitle {
  margin: 0.5rem 0 0 0;
  font-size: 0.9rem;
  color: #666;
}

.alert {
  color: #ff4d4f;
  font-weight: 500;
}

.success {
  color: #52c41a;
  font-weight: 500;
}

.update-time {
  grid-column: 1 / -1;
  text-align: right;
  padding-top: 0.5rem;
  color: #999;
  font-size: 0.85rem;
}
</style>
