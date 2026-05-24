<template>
  <section class="page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Nodes</p>
        <h2>模拟节点列表</h2>
      </div>
      <button class="ghost" @click="load" :disabled="loading">
        {{ loading ? '加载中…' : '刷新' }}
      </button>
    </header>

    <!-- 筛选面板 -->
    <article class="panel filter-panel">
      <div class="filter-panel-header">
        <span class="filter-panel-title">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M1 3h14M3 8h10M6 13h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          筛选
        </span>
        <button v-if="hasActiveFilters" @click="resetFilters" class="reset-btn">清除筛选</button>
      </div>

      <div class="filter-group">
        <!-- 关键字搜索 -->
        <div class="filter-item">
          <label for="keyword">关键字</label>
          <div class="input-wrapper">
            <svg class="input-icon" width="14" height="14" viewBox="0 0 16 16" fill="none">
              <circle cx="6.5" cy="6.5" r="5" stroke="currentColor" stroke-width="1.5"/>
              <path d="M10.5 10.5L14 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            <input
              id="keyword"
              v-model="filters.keyword"
              type="text"
              placeholder="节点名 / IP 地址"
              @input="debounceLoad"
              class="filter-input has-icon"
            />
            <button v-if="filters.keyword" class="clear-btn" @click="filters.keyword = ''; load()">×</button>
          </div>
        </div>

        <!-- 状态筛选 -->
        <div class="filter-item">
          <label for="status">状态</label>
          <select
            id="status"
            v-model="filters.status"
            @change="load"
            class="filter-select"
            :class="{ active: filters.status }"
          >
            <option value="">全部状态</option>
            <option value="ONLINE">在线</option>
            <option value="WARNING">告警</option>
            <option value="OFFLINE">离线</option>
          </select>
        </div>

        <!-- 服务类型筛选 -->
        <div class="filter-item">
          <label for="serviceType">服务类型</label>
          <select
            id="serviceType"
            v-model="filters.serviceType"
            @change="load"
            class="filter-select"
            :class="{ active: filters.serviceType }"
          >
            <option value="">全部类型</option>
            <option value="SPRING_BOOT">SPRING_BOOT</option>
            <option value="NGINX">NGINX</option>
            <option value="REDIS">REDIS</option>
            <option value="MYSQL">MYSQL</option>
            <option value="NODE_EXPORTER">NODE_EXPORTER</option>
          </select>
        </div>

        <!-- 排序 -->
        <div class="filter-item">
          <label for="sortBy">排序</label>
          <select
            id="sortBy"
            v-model="filters.sortBy"
            @change="load"
            class="filter-select"
            :class="{ active: filters.sortBy !== 'name' }"
          >
            <option value="name">按名称 A→Z</option>
            <option value="lastHeartbeat">按心跳时间（最新优先）</option>
          </select>
        </div>
      </div>

      <!-- 激活的筛选条件 chips -->
      <div v-if="hasActiveFilters" class="active-filters">
        <span class="active-filters-label">已筛选：</span>
        <span v-if="filters.keyword" class="chip">
          关键字: "{{ filters.keyword }}"
          <button @click="filters.keyword = ''; load()">×</button>
        </span>
        <span v-if="filters.status" class="chip">
          状态: {{ statusLabel(filters.status) }}
          <button @click="filters.status = ''; load()">×</button>
        </span>
        <span v-if="filters.serviceType" class="chip">
          类型: {{ filters.serviceType }}
          <button @click="filters.serviceType = ''; load()">×</button>
        </span>
        <span v-if="filters.sortBy !== 'name'" class="chip">
          排序: 按心跳时间
          <button @click="filters.sortBy = 'name'; load()">×</button>
        </span>
      </div>
    </article>

    <!-- 结果栏 -->
    <div class="result-bar">
      <span v-if="loading" class="result-loading">
        <span class="spinner"></span> 正在加载…
      </span>
      <span v-else>
        共 <strong>{{ nodes.length }}</strong> 个节点
        <span v-if="hasActiveFilters" class="filtered-hint">（已应用筛选）</span>
      </span>
    </div>

    <!-- 节点列表 -->
    <article class="panel table-panel" :class="{ 'is-loading': loading }">
      <table class="data-table">
        <thead>
          <tr>
            <th>节点名</th>
            <th>IP 地址</th>
            <th>状态</th>
            <th
              class="sortable-col"
              :class="{ sorted: filters.sortBy === 'lastHeartbeat' }"
              @click="toggleHeartbeatSort"
              title="点击按心跳时间排序"
            >
              最后心跳
              <span class="sort-icon">{{ filters.sortBy === 'lastHeartbeat' ? '↓' : '↕' }}</span>
            </th>
            <th>识别类型</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && nodes.length === 0">
            <td colspan="5" class="no-data">
              <div class="no-data-inner">
                <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
                  <circle cx="20" cy="20" r="18" stroke="#CBD5E1" stroke-width="2"/>
                  <path d="M13 20h14M20 13v14" stroke="#CBD5E1" stroke-width="2" stroke-linecap="round"/>
                </svg>
                <p>没有符合条件的节点</p>
                <button v-if="hasActiveFilters" class="ghost" @click="resetFilters">清除筛选</button>
              </div>
            </td>
          </tr>
          <tr v-for="node in nodes" :key="node.id" class="node-row">
            <td>
              <RouterLink :to="`/nodes/${node.id}`" class="node-link">{{ node.nodeName }}</RouterLink>
            </td>
            <td class="mono">{{ node.ipAddress }}</td>
            <td>
              <span :class="['status-badge', node.status.toLowerCase()]">
                <span class="status-dot"></span>
                {{ statusLabel(node.status) }}
              </span>
            </td>
            <td :title="formatDateTime(node.lastSeenAt)" class="heartbeat-cell">
              {{ formatRelativeTime(node.lastSeenAt) }}
            </td>
            <td>
              <span v-if="node.serviceTypes.length === 0" class="empty-types">-</span>
              <span
                v-for="type in node.serviceTypes"
                :key="type"
                class="type-tag"
                :class="typeClass(type)"
              >{{ type }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </article>

    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, ref, computed } from "vue";
import { fetchNodes } from "../services/api";

const nodes = ref([]);
const error = ref("");
const loading = ref(false);
const filters = ref({
  keyword: "",
  status: "",
  serviceType: "",
  sortBy: "name"
});

let debounceTimer;
let latestRequestToken = 0;

const hasActiveFilters = computed(() =>
  filters.value.keyword || filters.value.status || filters.value.serviceType || filters.value.sortBy !== "name"
);

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

function debounceLoad() {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(load, 300);
}

async function load() {
  const requestToken = ++latestRequestToken;
  error.value = "";
  loading.value = true;
  try {
    const data = await fetchNodes(filters.value);
    if (requestToken === latestRequestToken) {
      nodes.value = data;
    }
  } catch (err) {
    if (requestToken === latestRequestToken) {
      error.value = err.message;
    }
  } finally {
    if (requestToken === latestRequestToken) {
      loading.value = false;
    }
  }
}

function resetFilters() {
  filters.value = { keyword: "", status: "", serviceType: "", sortBy: "name" };
  load();
}

function toggleHeartbeatSort() {
  filters.value.sortBy = filters.value.sortBy === "lastHeartbeat" ? "name" : "lastHeartbeat";
  load();
}

function typeClass(type) {
  return {
    "type-spring": type === "SPRING_BOOT",
    "type-cache": type === "CACHE" || type === "REDIS",
    "type-db": type === "DATABASE" || type === "MYSQL",
    "type-web": type === "WEB_SERVER" || type === "NGINX",
    "type-node": type === "NODE_EXPORTER",
  };
}

function statusLabel(status) {
  if (status === "ONLINE") return "在线";
  if (status === "WARNING") return "告警";
  if (status === "OFFLINE") return "离线";
  return status || "未知";
}

onMounted(load);
</script>

<style scoped>
/* ── 筛选面板 ─────────────────────────────────── */
.filter-panel {
  padding: 16px 20px;
}

.filter-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.filter-panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
}

.reset-btn {
  font-size: 0.75rem;
  color: #6b7280;
  background: none;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 3px 10px;
  cursor: pointer;
}
.reset-btn:hover { background: #f9fafb; color: #374151; }

.filter-group {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1.5fr;
  gap: 12px;
  align-items: end;
}

@media (max-width: 900px) {
  .filter-group { grid-template-columns: 1fr 1fr; }
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.filter-item label {
  font-size: 0.75rem;
  font-weight: 500;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

/* 搜索框 */
.input-wrapper {
  position: relative;
}
.input-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #9ca3af;
  pointer-events: none;
}
.clear-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
}
.clear-btn:hover { color: #374151; }

.filter-input,
.filter-select {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  font-size: 0.875rem;
  font-family: inherit;
  background: #f9fafb;
  color: #111827;
  transition: border-color 0.15s, box-shadow 0.15s;
  appearance: none;
  -webkit-appearance: none;
}
.filter-input.has-icon { padding-left: 32px; }
.filter-input:focus,
.filter-select:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
  background: #fff;
}
.filter-select.active {
  border-color: #6366f1;
  background: #eef2ff;
  color: #4338ca;
  font-weight: 500;
}

/* Active filter chips */
.active-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}
.active-filters-label {
  font-size: 0.75rem;
  color: #9ca3af;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #eef2ff;
  color: #4338ca;
  border: 1px solid #c7d2fe;
  border-radius: 999px;
  font-size: 0.75rem;
  padding: 3px 8px 3px 10px;
}
.chip button {
  background: none;
  border: none;
  color: #818cf8;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0;
}
.chip button:hover { color: #4338ca; }

/* ── 结果栏 ─────────────────────────────────── */
.result-bar {
  font-size: 0.875rem;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: -6px;
}
.result-loading { display: flex; align-items: center; gap: 6px; }
.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #e5e7eb;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  display: inline-block;
}
@keyframes spin { to { transform: rotate(360deg); } }

.filtered-hint {
  color: #6366f1;
  font-weight: 500;
}

/* ── 表格 ─────────────────────────────────── */
.table-panel {
  padding: 0;
  overflow: hidden;
  transition: opacity 0.2s;
}
.table-panel.is-loading { opacity: 0.5; pointer-events: none; }

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
.node-row:hover { background: #fafafa; }
.node-row:last-child td { border-bottom: none; }

.sortable-col {
  cursor: pointer;
  user-select: none;
}
.sortable-col:hover { color: #4338ca; }
.sortable-col.sorted { color: #4338ca; }
.sort-icon { margin-left: 4px; opacity: 0.6; }

/* 节点链接 */
.node-link {
  font-weight: 500;
  color: #4338ca;
}
.node-link:hover { text-decoration: underline; }

.mono { font-family: "SFMono-Regular", Consolas, monospace; font-size: 0.8125rem; color: #374151; }

/* 状态 badge */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 500;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.status-badge.online  { background: #f0fdf4; color: #16a34a; }
.status-badge.warning { background: #fffbe6; color: #d48806; }
.status-badge.offline { background: #f9fafb; color: #9ca3af; }

/* 心跳列 */
.heartbeat-cell { font-size: 0.8125rem; color: #6b7280; }

/* 服务类型 tag */
.type-tag {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 4px;
  margin-right: 4px;
  letter-spacing: 0.03em;
}
.type-spring { background: #ecfdf5; color: #065f46; }
.type-cache  { background: #fff7ed; color: #9a3412; }
.type-db     { background: #eff6ff; color: #1e40af; }
.type-web    { background: #fdf4ff; color: #6b21a8; }
.type-node   { background: #f0f9ff; color: #0369a1; }
.empty-types { color: #d1d5db; }

/* 空状态 */
.no-data { text-align: center; padding: 3rem !important; }
.no-data-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #9ca3af;
  font-size: 0.875rem;
}
.no-data-inner p { margin: 0; }

.ghost {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 0.875rem;
}
</style>
