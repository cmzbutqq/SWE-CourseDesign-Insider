<template>
  <section class="page node-detail-page">
    <ContextHeader
      eyebrow="Node Detail"
      :title="node.nodeName || '节点详情'"
      :description="headerDescription"
      :badges="headerBadges"
    >
      <template #actions>
        <button class="ghost" :disabled="loading" @click="load">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </template>
    </ContextHeader>

    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="loading && !hasLoaded" class="panel loading-panel">
      <p>正在加载节点详情...</p>
    </section>

    <template v-else-if="hasLoaded">
      <RiskSummary
        title="节点摘要"
        :items="summaryItems"
        description="先用本地结构化摘要判断状态，再切到下方 Grafana / Prometheus / SkyWalking 面板。"
      />

      <RiskSummary
        v-if="highRiskItems.length > 0"
        title="高风险服务"
        description="以下服务缺少 metricsPath，建议优先补齐采集路径。"
        variant="list"
        tone="warn"
        :items="highRiskItems"
      />

      <section v-if="node.quickLinks.length > 0" class="panel quick-links-panel">
        <div class="section-heading">
          <div>
            <h3>外部原视图</h3>
            <p>需要时可直接跳回 Grafana / Prometheus / SkyWalking 原页面。</p>
          </div>
        </div>
        <div class="quick-link-list">
          <a
            v-for="link in node.quickLinks"
            :key="link.name"
            class="quick-link"
            :href="link.url"
            target="_blank"
            rel="noopener"
          >
            <span>{{ link.name }}</span>
            <span>打开</span>
          </a>
        </div>
      </section>

      <div class="tab-strip">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="tab-button"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <ObservabilityGrid
        v-if="activeTab === 'resources'"
        :groups="resourceGroups"
      />

      <section v-else-if="activeTab === 'services'" class="page-section">
        <div class="section-heading">
          <div>
            <h3>服务清单</h3>
            <p>这里保留本地结构化信息，方便定位指标缺口与跳转服务详情。</p>
          </div>
        </div>
        <article class="panel">
          <table class="data-table">
            <thead>
              <tr>
                <th>服务名</th>
                <th>类型</th>
                <th>端口</th>
                <th>进程</th>
                <th>metricsPath</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="node.services.length === 0">
                <td colspan="5">暂无服务数据</td>
              </tr>
              <tr v-for="service in node.services" :key="service.id">
                <td>
                  <RouterLink
                    v-if="service.id"
                    class="table-link"
                    :to="`/services/${service.id}`"
                  >
                    {{ service.serviceName }}
                  </RouterLink>
                  <span v-else>{{ service.serviceName }}</span>
                </td>
                <td>{{ service.serviceType || "-" }}</td>
                <td>{{ service.port || "-" }}</td>
                <td>{{ service.processName || "-" }}</td>
                <td>{{ service.metricsPath || "未配置" }}</td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <ObservabilityGrid
        v-else-if="activeTab === 'tracing'"
        :groups="tracingGroups"
      />

      <ObservabilityGrid
        v-else
        :groups="rawGroups"
      />
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import RiskSummary from "../components/RiskSummary.vue";
import { fetchNodeDetail } from "../services/api";
import { buildNodePanelGroups } from "../services/observability";

const route = useRoute();
const activeTab = ref("resources");
const loading = ref(false);
const hasLoaded = ref(false);
const error = ref("");

const node = reactive({
  nodeName: "",
  hostname: "",
  ipAddress: "",
  osName: "",
  agentVersion: "",
  status: "",
  statusSummary: "",
  lastHeartbeatAt: "",
  heartbeatTimeoutRisk: false,
  hostMetrics: null,
  highRiskServices: [],
  quickLinks: [],
  services: [],
});

const tabs = [
  { key: "resources", label: "资源监控" },
  { key: "services", label: "服务清单" },
  { key: "tracing", label: "链路关联" },
  { key: "raw", label: "原始监控" },
];

const headerDescription = computed(() =>
  [node.ipAddress || "-", statusLabel.value, node.hostname || ""]
    .filter(Boolean)
    .join(" · ")
);

const headerBadges = computed(() => [
  {
    label: statusLabel.value,
    tone: node.status === "WARNING" || node.heartbeatTimeoutRisk ? "warn" : "info",
  },
  {
    label: node.agentVersion || "Agent N/A",
    tone: "neutral",
  },
]);

const statusLabel = computed(() => {
  if (node.status === "ONLINE") return "在线";
  if (node.status === "WARNING") return "告警";
  if (node.status === "OFFLINE") return "离线";
  return "未知";
});

const summaryItems = computed(() => [
  { label: "主机名", value: node.hostname || "-" },
  { label: "系统", value: node.osName || "-" },
  { label: "状态", value: node.statusSummary || statusLabel.value },
  { label: "最后心跳", value: formatDateTime(node.lastHeartbeatAt) },
  { label: "识别服务数", value: node.services.length },
  { label: "高风险服务", value: node.highRiskServices.length },
]);

const panelGroups = computed(() => buildNodePanelGroups(node));
const resourceGroups = computed(() => [
  {
    key: "resources",
    title: "资源监控",
    description: "Grafana 节点看板用于判断主机可用性与资源抖动。",
    columns: 2,
    panels: panelGroups.value.resources,
  },
]);
const tracingGroups = computed(() => [
  {
    key: "tracing",
    title: "链路关联",
    description: "SkyWalking 保留服务视图入口，帮助确认链路是否正常接入。",
    columns: 1,
    panels: panelGroups.value.tracing,
  },
]);
const rawGroups = computed(() => [
  {
    key: "raw",
    title: "原始监控",
    description: "Prometheus 原始页面用来验证 targets 与查询条件是否命中。",
    columns: 2,
    panels: panelGroups.value.raw,
  },
]);
const highRiskItems = computed(() =>
  node.highRiskServices.map((service) => ({
    title: service.serviceName || service.id,
    detail: [service.serviceType || "-", service.port ? `端口 ${service.port}` : "", "缺少 metricsPath"]
      .filter(Boolean)
      .join(" · "),
    tone: "warn",
  }))
);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    Object.assign(node, await fetchNodeDetail(route.params.id));
    hasLoaded.value = true;
  } catch (err) {
    error.value = err.message || "获取节点详情失败";
  } finally {
    loading.value = false;
  }
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  return new Date(value).toLocaleString("zh-CN");
}

onMounted(load);
</script>

<style scoped>
.section-heading {
  margin-bottom: 12px;
}

.section-heading h3 {
  margin: 0 0 4px;
}

.section-heading p {
  margin: 0;
  color: #5b6472;
  font-size: 14px;
}

.loading-panel {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5b6472;
}

.quick-links-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quick-link-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.quick-link {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid #d7dee8;
  border-radius: 8px;
  color: #1f2937;
}

.table-link {
  color: #0f6cbd;
  font-weight: 500;
}
</style>
