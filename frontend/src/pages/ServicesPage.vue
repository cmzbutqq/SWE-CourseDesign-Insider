<template>
  <section class="page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Services</p>
        <h2>服务清单</h2>
      </div>
      <button class="ghost" @click="load" :disabled="loading">
        {{ loading ? "加载中…" : "刷新" }}
      </button>
    </header>

    <!-- 汇总栏 -->
    <div class="summary-bar" v-if="!loading && services.length > 0">
      <span class="summary-item">
        共 <strong>{{ services.length }}</strong> 个服务
      </span>
      <span class="summary-item" v-if="noMetricsCount > 0">
        <span class="dot dot-warn"></span>
        {{ noMetricsCount }} 个指标路径不可用
      </span>
      <span class="summary-item ok" v-else>
        <span class="dot dot-ok"></span>
        全部指标路径已配置
      </span>
    </div>

    <!-- 按类型分组 -->
    <div v-if="!loading && services.length > 0">
      <div
        v-for="(group, type) in groupedServices"
        :key="type"
        class="group-block"
      >
        <!-- 分组标题，可折叠 -->
        <button
          type="button"
          class="group-header"
          :aria-expanded="String(!collapsed[type])"
          @click="toggleGroup(type)"
        >
          <span class="group-toggle">{{ collapsed[type] ? "▶" : "▼" }}</span>
          <span :class="['type-tag', typeClass(type)]">{{ type }}</span>
          <span class="group-count">{{ group.length }} 个</span>
          <span v-if="unhealthyCount(group) > 0" class="group-warn">
            ⚠ {{ unhealthyCount(group) }} 个指标不可用
          </span>
          <span v-else class="group-ok">✓ 全部正常</span>
        </button>

        <!-- 分组内容 -->
        <article class="panel table-panel" v-show="!collapsed[type]">
          <table class="data-table">
            <thead>
              <tr>
                <th>服务名</th>
                <th>节点</th>
                <th>端口</th>
                <th>指标路径</th>
                <th>快捷入口</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="svc in group" :key="svc.id" class="svc-row">
                <td class="svc-name">{{ svc.serviceName }}</td>
                <td>
                  <RouterLink
                    v-if="svc.nodeId"
                    :to="`/nodes/${svc.nodeId}`"
                    class="node-link"
                  >{{ svc.nodeName }}</RouterLink>
                  <span v-else class="muted">{{ svc.nodeName || "-" }}</span>
                </td>
                <td class="mono">{{ svc.port || "-" }}</td>
                <td>
                  <span v-if="hasMetricsPath(svc)" class="metrics-ok">
                    <span class="dot dot-ok"></span>{{ svc.metricsPath.trim() }}
                  </span>
                  <span v-else class="metrics-na">
                    <span class="dot dot-warn"></span>未配置
                  </span>
                </td>
                <td class="actions">
                  <RouterLink
                    v-if="svc.nodeId"
                    :to="`/nodes/${svc.nodeId}`"
                    class="action-btn"
                    title="节点详情"
                  >节点详情</RouterLink>
                  <RouterLink
                    to="/nodes"
                    class="action-btn secondary"
                    title="节点列表"
                  >节点列表</RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </article>
      </div>
    </div>

    <!-- 空状态 -->
    <article class="panel empty-state" v-if="!loading && services.length === 0 && !error">
      <p>暂无服务数据，请确认 Agent 已注册。</p>
    </article>

    <!-- 加载中 -->
    <article class="panel empty-state" v-if="loading">
      <p>加载中…</p>
    </article>

    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, ref, computed, reactive } from "vue";
import { fetchServices } from "../services/api";

const services = ref([]);
const error = ref("");
const loading = ref(false);
const collapsed = reactive({});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    services.value = await fetchServices();
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}

// 按 serviceType 分组
const groupedServices = computed(() => {
  const groups = {};
  for (const svc of services.value) {
    const type = svc.serviceType || "OTHER";
    if (!groups[type]) groups[type] = [];
    groups[type].push(svc);
  }
  return groups;
});

// 没有 metricsPath 的服务数量（汇总用）
const noMetricsCount = computed(() =>
  services.value.filter((service) => !hasMetricsPath(service)).length
);

// 某分组内没有 metricsPath 的数量
function unhealthyCount(group) {
  return group.filter((service) => !hasMetricsPath(service)).length;
}

function hasMetricsPath(service) {
  return Boolean(service.metricsPath?.trim());
}

function toggleGroup(type) {
  collapsed[type] = !collapsed[type];
}

function typeClass(type) {
  const map = {
    SPRING_BOOT: "type-spring",
    NGINX: "type-web",
    REDIS: "type-cache",
    MYSQL: "type-db",
    NODE_EXPORTER: "type-node",
  };
  return map[type] || "type-other";
}

onMounted(load);
</script>

<style scoped>
/* 汇总栏 */
.summary-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 0.875rem;
  color: #6b7280;
  margin-bottom: 12px;
}
.summary-item { display: flex; align-items: center; gap: 6px; }
.summary-item.ok { color: #16a34a; }

/* 状态点 */
.dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-ok   { background: #16a34a; }
.dot-warn { background: #d97706; }

/* 分组块 */
.group-block { margin-bottom: 16px; }

.group-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  font-size: 0.875rem;
  width: 100%;
}
.group-header:hover { background: #f1f5f9; }

.group-toggle { color: #9ca3af; font-size: 0.75rem; width: 12px; }
.group-count  { color: #6b7280; margin-left: 2px; }
.group-warn   { color: #d97706; font-weight: 500; margin-left: auto; }
.group-ok     { color: #16a34a; font-weight: 500; margin-left: auto; }

/* 表格面板 */
.table-panel {
  padding: 0;
  overflow: hidden;
  border-top: none;
  border-radius: 0 0 8px 8px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}
.data-table th,
.data-table td {
  text-align: left;
  padding: 11px 16px;
  border-bottom: 1px solid #f1f5f9;
}
.data-table th {
  font-size: 0.75rem;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: #fafafa;
}
.svc-row:last-child td { border-bottom: none; }
.svc-row:hover { background: #fafafa; }

.svc-name { font-weight: 500; color: #111827; }
.mono { font-family: monospace; font-size: 0.8125rem; color: #374151; }
.muted { color: #9ca3af; }

/* 指标路径 */
.metrics-ok {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: monospace;
  font-size: 0.8rem;
  color: #374151;
}
.metrics-na {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.8rem;
  color: #9ca3af;
}

/* 节点链接 */
.node-link {
  color: #4338ca;
  font-weight: 500;
  text-decoration: none;
}
.node-link:hover { text-decoration: underline; }

/* 快捷入口按钮 */
.actions { display: flex; gap: 6px; flex-wrap: wrap; }
.action-btn {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  text-decoration: none;
  background: #eef2ff;
  color: #4338ca;
  border: 1px solid #c7d2fe;
  white-space: nowrap;
  cursor: pointer;
}
.action-btn:hover { background: #e0e7ff; }
.action-btn.secondary {
  background: #f9fafb;
  color: #6b7280;
  border-color: #e5e7eb;
}
.action-btn.secondary:hover { background: #f3f4f6; color: #374151; }

/* 类型标签 */
.type-tag {
  display: inline-block;
  font-size: 0.75rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.03em;
}
.type-spring { background: #ecfdf5; color: #065f46; }
.type-web    { background: #fdf4ff; color: #6b21a8; }
.type-cache  { background: #fff7ed; color: #9a3412; }
.type-db     { background: #eff6ff; color: #1e40af; }
.type-node   { background: #f0f9ff; color: #0369a1; }
.type-other  { background: #f3f4f6; color: #374151; }

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 3rem;
  color: #9ca3af;
  font-size: 0.875rem;
}

/* ghost 按钮 */
.ghost {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 0.875rem;
}
.ghost:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
