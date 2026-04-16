<template>
  <section class="page overview-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Dashboard</p>
        <h2>系统健康总览</h2>
      </div>
      <div class="header-actions">
        <span v-if="isLoading" class="loading-indicator">加载中...</span>
        <button
          v-else
          class="ghost"
          @click="load"
          :disabled="isLoading"
        >
          {{ autoRefresh ? '自动更新中' : '刷新' }}
        </button>
        <button
          class="ghost"
          @click="toggleAutoRefresh"
          :class="{ active: autoRefresh }"
        >
          {{ autoRefresh ? '⏸' : '▶' }}
        </button>
      </div>
    </header>

    <!-- 骨架屏加载状态 -->
    <div v-if="isLoading && !hasData" class="skeleton-screen">
      <div class="skeleton-kpi-cards">
        <div v-for="i in 5" :key="`skeleton-${i}`" class="skeleton-card"></div>
      </div>
    </div>

    <!-- 已加载的内容 -->
    <div v-else-if="hasData" class="content">
      <!-- 第一层：KPI 状态卡片区 -->
      <KPICards :data="overviewData" />

      <!-- 第二层：异常前置聚焦区 -->
      <AnomalyPanel :anomalies="overviewData.anomalies" />

      <!-- 第三层：节点与服务列表（下移第二屏） -->
      <div class="bottom-section">
        <h3>节点与服务详情</h3>
        <p class="section-hint">点击卡片查看详细信息和实时监控数据</p>

        <!-- 快速链接到各个观测工具 -->
        <article class="tools-panel">
          <h4>观测工具</h4>
          <ul class="link-list">
            <li v-for="link in overviewData.quickLinks" :key="link">
              <a :href="extractUrl(link)" target="_blank" rel="noopener">
                {{ extractLabel(link) }}
              </a>
            </li>
          </ul>
        </article>

        <!-- 链接到详情页面 -->
        <div class="quick-nav">
          <router-link to="/nodes" class="nav-button">
            查看所有节点
          </router-link>
          <router-link to="/services" class="nav-button">
            查看所有服务
          </router-link>
        </div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else class="error-state">
      <p class="error-message">{{ error || '加载失败，请重试' }}</p>
      <button class="primary" @click="load">重新加载</button>
    </div>
  </section>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref, computed, watch } from 'vue';
import { fetchOverview } from '../services/api.js';
import KPICards from '../components/KPICards.vue';
import AnomalyPanel from '../components/AnomalyPanel.vue';

// 数据状态
const overviewData = reactive({
  nodes: { total: 0, online: 0, offline: 0, warning: 0 },
  services: { total: 0, healthy: 0, abnormal: 0 },
  unresolvedAlerts: 0,
  anomalies: { nodes: [], services: [] },
  quickLinks: []
});

const error = ref('');
const isLoading = ref(false);
const hasLoaded = ref(false);
const autoRefresh = ref(true);
const pollingInterval = ref(null);
const POLL_INTERVAL = 15000; // 15秒轮询一次

// 计算属性
const hasData = computed(() => hasLoaded.value);

// 加载数据
async function load() {
  error.value = '';
  isLoading.value = true;
  try {
    const data = await fetchOverview();
    Object.assign(overviewData, data);
    hasLoaded.value = true;
  } catch (err) {
    error.value = err.message || '获取数据失败';
    console.error('Overview load error:', err);
  } finally {
    isLoading.value = false;
  }
}

// 切换自动更新
function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value;
}

// 监听自动更新状态
watch(autoRefresh, (newVal) => {
  if (newVal) {
    // 开启轮询
    startPolling();
  } else {
    // 停止轮询
    stopPolling();
  }
});

// 启动轮询
function startPolling() {
  if (pollingInterval.value) return;
  pollingInterval.value = setInterval(load, POLL_INTERVAL);
}

// 停止轮询
function stopPolling() {
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value);
    pollingInterval.value = null;
  }
}

// 解析URL和标签
function extractUrl(linkString) {
  // 格式为 "Prometheus: http://..."
  const match = linkString.match(/:\s*(http.+)/);
  return match ? match[1] : linkString;
}

function extractLabel(linkString) {
  // 格式为 "Prometheus: http://..."
  const match = linkString.match(/^([^:]+):/);
  return match ? match[1] : linkString;
}

// 生命周期
onMounted(() => {
  load();
  if (autoRefresh.value) {
    startPolling();
  }
});

onUnmounted(() => {
  stopPolling();
});
</script>

<style scoped>
.overview-page {
  background: #f5f7fa;
  min-height: 100vh;
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  padding: 1.5rem 2rem;
  background: white;
  border-bottom: 1px solid #f0f0f0;
}

.page-header > div h2 {
  margin: 0;
}

.page-header .eyebrow {
  margin: 0 0 0.5rem 0;
  font-size: 0.85rem;
  color: #999;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.loading-indicator {
  font-size: 0.9rem;
  color: #1890ff;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

.ghost {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.ghost:hover:not(:disabled) {
  border-color: #40a9ff;
  color: #1890ff;
}

.ghost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ghost.active {
  background: #e6f7ff;
  border-color: #1890ff;
  color: #1890ff;
}

.content {
  width: 100%;
  flex: 1;
  padding: 0 2rem 2rem 2rem;
  max-width: 1400px;
  margin: 0 auto;
}

/* 骨架屏 */
.skeleton-screen {
  padding: 2rem;
}

.skeleton-kpi-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
}

.skeleton-card {
  height: 180px;
  background: linear-gradient(90deg, #f0f0f0 25%, #fff 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: loading 1.5s infinite;
  border-radius: 8px;
}

@keyframes loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 错误状态 */
.error-state {
  text-align: center;
  padding: 3rem 2rem;
}

.error-message {
  color: #ff4d4f;
  font-size: 1rem;
  margin-bottom: 1.5rem;
}

.primary {
  padding: 0.6rem 1.5rem;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
}

.primary:hover {
  background: #40a9ff;
}

/* 底部部分 */
.bottom-section {
  margin-top: 3rem;
  padding-top: 2rem;
  border-top: 2px solid #f0f0f0;
}

.bottom-section h3 {
  margin: 0 0 0.5rem 0;
  font-size: 1.2rem;
  color: #333;
}

.section-hint {
  color: #999;
  font-size: 0.9rem;
  margin-bottom: 1.5rem;
}

.tools-panel {
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 2rem;
}

.tools-panel h4 {
  margin: 0 0 1rem 0;
  font-size: 1rem;
  color: #333;
}

.link-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1rem;
}

.link-list li {
  margin: 0;
}

.link-list a {
  display: block;
  padding: 1rem;
  background: #f5f7fa;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  color: #1890ff;
  text-decoration: none;
  text-align: center;
  font-weight: 500;
  transition: all 0.2s;
}

.link-list a:hover {
  background: #e6f7ff;
  border-color: #1890ff;
}

.quick-nav {
  display: flex;
  gap: 1rem;
  justify-content: center;
}

.nav-button {
  padding: 0.8rem 2rem;
  background: #1890ff;
  color: white;
  border-radius: 4px;
  text-decoration: none;
  font-weight: 500;
  transition: all 0.2s;
}

.nav-button:hover {
  background: #40a9ff;
}
</style>
