<template>
  <section class="page node-detail-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Node Detail</p>
        <h2>{{ node.nodeName || "节点详情" }}</h2>
      </div>
      <div class="header-actions">
        <span v-if="loading" class="loading-indicator">加载中...</span>
        <button class="ghost" @click="load" :disabled="loading">刷新</button>
      </div>
    </header>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <template v-if="!error">
      <!-- 第一层：状态摘要 + 心跳 + 超时风险 -->
      <section class="diagnosis-section">
        <div class="status-banner" :class="statusBannerClass">
          <div class="status-icon">
            <span v-if="node.status === 'ONLINE' && !node.heartbeatTimeoutRisk">✓</span>
            <span v-else-if="node.heartbeatTimeoutRisk">⚠</span>
            <span v-else>⊗</span>
          </div>
          <div class="status-text">
            <h3>{{ node.statusSummary || '未知状态' }}</h3>
            <p class="status-detail">
              节点状态：
              <span :class="['status-badge', node.status?.toLowerCase()]">
                {{ node.status === 'ONLINE' ? '在线' : '离线' }}
              </span>
              <span class="separator">|</span>
              最近心跳：
              <span :title="formatDateTime(node.lastHeartbeatAt)">{{ formatRelativeTime(node.lastHeartbeatAt) }}</span>
              <span v-if="node.heartbeatTimeoutRisk" class="timeout-warning">
                ⚠ 心跳超时风险
              </span>
            </p>
          </div>
        </div>
      </section>

      <!-- 第二层：主机指标摘要 -->
      <section class="metrics-section" v-if="node.hostMetrics">
        <h3 class="section-title">主机指标摘要</h3>
        <div class="metrics-grid">
          <article class="metric-card" :class="getMetricLevel('cpu', node.hostMetrics.cpuUsage)">
            <span class="metric-label">CPU 使用率</span>
            <strong class="metric-value">{{ formatPercent(node.hostMetrics.cpuUsage) }}</strong>
            <div class="metric-bar">
              <div class="metric-bar-fill" :style="{ width: clampPercent(node.hostMetrics.cpuUsage) }"></div>
            </div>
          </article>
          <article class="metric-card" :class="getMetricLevel('memory', node.hostMetrics.memoryUsage)">
            <span class="metric-label">内存使用率</span>
            <strong class="metric-value">{{ formatPercent(node.hostMetrics.memoryUsage) }}</strong>
            <div class="metric-detail">{{ node.hostMetrics.memoryUsedMb || '-' }} / {{ node.hostMetrics.memoryTotalMb || '-' }} MB</div>
            <div class="metric-bar">
              <div class="metric-bar-fill" :style="{ width: clampPercent(node.hostMetrics.memoryUsage) }"></div>
            </div>
          </article>
          <article class="metric-card" :class="getMetricLevel('disk', node.hostMetrics.diskUsage)">
            <span class="metric-label">磁盘使用率</span>
            <strong class="metric-value">{{ formatPercent(node.hostMetrics.diskUsage) }}</strong>
            <div class="metric-detail">{{ node.hostMetrics.diskUsedGb || '-' }} / {{ node.hostMetrics.diskTotalGb || '-' }} GB</div>
            <div class="metric-bar">
              <div class="metric-bar-fill" :style="{ width: clampPercent(node.hostMetrics.diskUsage) }"></div>
            </div>
          </article>
          <article class="metric-card metric-card-network">
            <span class="metric-label">网络吞吐</span>
            <div class="network-values">
              <div class="network-item">
                <span class="network-dir">↓ 接收</span>
                <strong>{{ formatMbps(node.hostMetrics.networkRxMbps) }}</strong>
              </div>
              <div class="network-item">
                <span class="network-dir">↑ 发送</span>
                <strong>{{ formatMbps(node.hostMetrics.networkTxMbps) }}</strong>
              </div>
            </div>
          </article>
        </div>
      </section>
      <section class="metrics-section" v-else>
        <h3 class="section-title">主机指标摘要</h3>
        <div class="no-metrics">
          <p>暂无主机指标数据，等待 Agent 上报心跳指标</p>
        </div>
      </section>

      <!-- 第三层：高风险服务提示 -->
      <section class="risk-section" v-if="node.highRiskServices && node.highRiskServices.length > 0">
        <div class="risk-banner">
          <h3>⚠ 高风险服务 ({{ node.highRiskServices.length }})</h3>
          <p class="risk-hint">以下服务未配置指标暴露路径，无法进行健康监控</p>
        </div>
        <div class="risk-list">
          <div v-for="svc in node.highRiskServices" :key="'risk-' + svc.id" class="risk-item">
            <span class="risk-name">{{ svc.serviceName }}</span>
            <span class="risk-type type-tag" :class="typeClass(svc.serviceType)">{{ svc.serviceType }}</span>
            <span class="risk-reason">无 metricsPath</span>
          </div>
        </div>
      </section>

      <!-- 第四层：观测工具快捷入口 -->
      <section class="tools-section" v-if="node.quickLinks && node.quickLinks.length > 0">
        <h3 class="section-title">观测工具快捷入口</h3>
        <div class="tools-grid">
          <a
            v-for="link in node.quickLinks"
            :key="link.name"
            :href="link.url"
            target="_blank"
            rel="noopener"
            class="tool-card"
          >
            <span class="tool-icon">{{ toolIcon(link.name) }}</span>
            <span class="tool-name">{{ link.name }}</span>
            <span class="tool-hint">在新窗口打开 →</span>
          </a>
        </div>
      </section>

      <!-- 第五层：基础信息 + 服务列表 -->
      <section class="info-section">
        <h3 class="section-title">基础信息</h3>
        <div class="info-cards">
          <article class="info-card">
            <span>主机名</span>
            <strong>{{ node.hostname || "-" }}</strong>
          </article>
          <article class="info-card">
            <span>IP 地址</span>
            <strong class="mono">{{ node.ipAddress || "-" }}</strong>
          </article>
          <article class="info-card">
            <span>系统</span>
            <strong>{{ node.osName || "-" }}</strong>
          </article>
          <article class="info-card">
            <span>Agent</span>
            <strong>{{ node.agentVersion || "-" }}</strong>
          </article>
        </div>
      </section>

      <section class="services-section">
        <h3 class="section-title">识别到的服务 ({{ node.services?.length || 0 }})</h3>
        <article class="panel">
          <table class="data-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>类型</th>
                <th>端口</th>
                <th>进程</th>
                <th>指标路径</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!node.services || node.services.length === 0">
                <td colspan="5" class="no-data">暂无服务数据</td>
              </tr>
              <tr v-for="service in node.services" :key="service.id">
                <td>{{ service.serviceName }}</td>
                <td>
                  <span class="type-tag" :class="typeClass(service.serviceType)">{{ service.serviceType }}</span>
                </td>
                <td class="mono">{{ service.port }}</td>
                <td>{{ service.processName }}</td>
                <td>
                  <span v-if="service.metricsPath" class="metrics-path">{{ service.metricsPath }}</span>
                  <span v-else class="no-metrics-path">未配置</span>
                </td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>
    </template>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from "vue";
import { useRoute } from "vue-router";
import { fetchNodeDetail } from "../services/api";

const route = useRoute();
const error = ref("");
const loading = ref(false);
const node = reactive({
  nodeName: "",
  hostname: "",
  ipAddress: "",
  osName: "",
  agentVersion: "",
  status: "",
  statusSummary: "",
  lastHeartbeatAt: null,
  heartbeatTimeoutRisk: false,
  hostMetrics: null,
  highRiskServices: [],
  quickLinks: [],
  services: []
});

const statusBannerClass = computed(() => {
  if (node.status !== "ONLINE") return "banner-offline";
  if (node.heartbeatTimeoutRisk) return "banner-warning";
  return "banner-healthy";
});

async function load() {
  error.value = "";
  loading.value = true;
  try {
    Object.assign(node, await fetchNodeDetail(route.params.id));
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}

function formatRelativeTime(timestamp) {
  if (!timestamp) return "-";
  const seconds = Math.floor((Date.now() - new Date(timestamp)) / 1000);
  if (seconds < 60) return "刚刚";
  if (seconds < 3600) return `${Math.floor(seconds / 60)}分钟前`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}小时前`;
  return `${Math.floor(seconds / 86400)}天前`;
}

function formatDateTime(timestamp) {
  if (!timestamp) return "";
  return new Date(timestamp).toLocaleString("zh-CN");
}

function formatPercent(value) {
  if (value == null) return "-";
  return value.toFixed(1) + "%";
}

function clampPercent(value) {
  if (value == null) return "0%";
  return Math.min(100, Math.max(0, value)).toFixed(1) + "%";
}

function formatMbps(value) {
  if (value == null) return "-";
  if (value < 1) return (value * 1000).toFixed(0) + " Kbps";
  return value.toFixed(2) + " Mbps";
}

function getMetricLevel(type, value) {
  if (value == null) return "";
  if (value >= 90) return "metric-critical";
  if (value >= 75) return "metric-warning";
  return "metric-healthy";
}

function typeClass(type) {
  return {
    "type-spring": type === "SPRING_BOOT",
    "type-cache": type === "CACHE",
    "type-db": type === "DATABASE",
    "type-web": type === "WEB_SERVER"
  };
}

function toolIcon(name) {
  if (name === "Grafana") return "📊";
  if (name === "Prometheus") return "🔥";
  if (name === "SkyWalking") return "🔭";
  return "🔗";
}

onMounted(load);
</script>

<style scoped>
.node-detail-page {
  max-width: 1200px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding: 1.5rem 2rem;
  background: white;
  border-bottom: 1px solid #f0f0f0;
  border-radius: 8px;
}

.page-header .eyebrow {
  margin: 0 0 0.25rem 0;
  font-size: 0.85rem;
  color: #999;
}

.page-header h2 {
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.loading-indicator {
  font-size: 0.9rem;
  color: #1890ff;
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

.error-banner {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 8px;
  padding: 1rem 1.5rem;
  color: #cf1322;
  margin-bottom: 1.5rem;
}

/* ── 第一层：状态摘要 ── */
.diagnosis-section {
  margin-bottom: 1.5rem;
}

.status-banner {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  border-radius: 8px;
  border-left: 4px solid;
}

.banner-healthy {
  background: #f0fdf4;
  border-left-color: #52c41a;
}

.banner-warning {
  background: #fffbe6;
  border-left-color: #faad14;
}

.banner-offline {
  background: #fff2f0;
  border-left-color: #ff4d4f;
}

.status-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  font-weight: bold;
  flex-shrink: 0;
}

.banner-healthy .status-icon {
  background: #d9f7be;
  color: #389e0d;
}

.banner-warning .status-icon {
  background: #fff1b8;
  color: #d48806;
}

.banner-offline .status-icon {
  background: #ffccc7;
  color: #cf1322;
}

.status-text h3 {
  margin: 0 0 0.25rem 0;
  font-size: 1.1rem;
}

.status-detail {
  margin: 0;
  font-size: 0.9rem;
  color: #666;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.separator {
  color: #d9d9d9;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 500;
}

.status-badge.online {
  background: #f0fdf4;
  color: #16a34a;
}

.status-badge.offline {
  background: #f9fafb;
  color: #9ca3af;
}

.timeout-warning {
  color: #faad14;
  font-weight: 500;
  font-size: 0.85rem;
}

/* ── 第二层：主机指标 ── */
.metrics-section {
  margin-bottom: 1.5rem;
}

.section-title {
  margin: 0 0 1rem 0;
  font-size: 1rem;
  color: #333;
  font-weight: 600;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}

@media (max-width: 1000px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 600px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}

.metric-card {
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 1.25rem;
  border-left: 4px solid #52c41a;
  transition: box-shadow 0.2s;
}

.metric-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.metric-card.metric-healthy {
  border-left-color: #52c41a;
}

.metric-card.metric-warning {
  border-left-color: #faad14;
}

.metric-card.metric-critical {
  border-left-color: #ff4d4f;
}

.metric-card-network {
  border-left-color: #1890ff;
}

.metric-label {
  display: block;
  font-size: 0.8rem;
  color: #8c8c8c;
  margin-bottom: 0.5rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.metric-value {
  font-size: 1.75rem;
  color: #333;
  display: block;
  margin-bottom: 0.5rem;
}

.metric-detail {
  font-size: 0.8rem;
  color: #8c8c8c;
  margin-bottom: 0.75rem;
}

.metric-bar {
  height: 4px;
  background: #f0f0f0;
  border-radius: 2px;
  overflow: hidden;
}

.metric-bar-fill {
  height: 100%;
  border-radius: 2px;
  background: #52c41a;
  transition: width 0.3s;
}

.metric-warning .metric-bar-fill {
  background: #faad14;
}

.metric-critical .metric-bar-fill {
  background: #ff4d4f;
}

.network-values {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.network-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.network-dir {
  font-size: 0.85rem;
  color: #666;
}

.network-item strong {
  font-size: 1.1rem;
  color: #333;
}

.no-metrics {
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 2rem;
  text-align: center;
  color: #999;
}

/* ── 第三层：高风险服务 ── */
.risk-section {
  margin-bottom: 1.5rem;
}

.risk-banner {
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px 8px 0 0;
  padding: 1rem 1.5rem;
}

.risk-banner h3 {
  margin: 0 0 0.25rem 0;
  color: #d48806;
  font-size: 1rem;
}

.risk-hint {
  margin: 0;
  font-size: 0.85rem;
  color: #8c8c8c;
}

.risk-list {
  background: white;
  border: 1px solid #ffe58f;
  border-top: none;
  border-radius: 0 0 8px 8px;
  padding: 0.75rem 1.5rem;
}

.risk-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f5f5f5;
}

.risk-item:last-child {
  border-bottom: none;
}

.risk-name {
  font-weight: 500;
  color: #333;
}

.risk-reason {
  font-size: 0.8rem;
  color: #faad14;
  margin-left: auto;
}

/* ── 第四层：观测工具 ── */
.tools-section {
  margin-bottom: 1.5rem;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

@media (max-width: 700px) {
  .tools-grid {
    grid-template-columns: 1fr;
  }
}

.tool-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1.25rem;
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  text-decoration: none;
  color: inherit;
  transition: all 0.2s;
}

.tool-card:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
  transform: translateY(-1px);
}

.tool-icon {
  font-size: 1.5rem;
}

.tool-name {
  font-weight: 600;
  font-size: 0.95rem;
  color: #333;
}

.tool-hint {
  font-size: 0.75rem;
  color: #1890ff;
}

/* ── 第五层：基础信息 ── */
.info-section {
  margin-bottom: 1.5rem;
}

.info-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
}

.info-card {
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 1rem 1.25rem;
}

.info-card span {
  display: block;
  font-size: 0.8rem;
  color: #8c8c8c;
  margin-bottom: 0.5rem;
}

.info-card strong {
  font-size: 1.1rem;
  color: #333;
}

.mono {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 0.9rem;
}

/* ── 服务列表 ── */
.services-section {
  margin-bottom: 1.5rem;
}

.panel {
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 0;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  text-align: left;
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
}

.data-table th {
  font-size: 0.75rem;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: #f8fafc;
}

.data-table tbody tr:hover {
  background: #fafafa;
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.no-data {
  text-align: center;
  padding: 2rem !important;
  color: #999;
}

.type-tag {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 4px;
  letter-spacing: 0.03em;
}

.type-spring {
  background: #ecfdf5;
  color: #065f46;
}

.type-cache {
  background: #fff7ed;
  color: #9a3412;
}

.type-db {
  background: #eff6ff;
  color: #1e40af;
}

.type-web {
  background: #fdf4ff;
  color: #6b21a8;
}

.metrics-path {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 0.8rem;
  color: #389e0d;
}

.no-metrics-path {
  color: #d9d9d9;
  font-size: 0.85rem;
}
</style>
