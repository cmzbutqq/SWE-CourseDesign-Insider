<template>
  <section class="page service-detail-page">
    <ContextHeader
      eyebrow="Service Detail"
      :title="service.serviceName || '服务详情'"
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
      <p>正在加载服务详情...</p>
    </section>

    <template v-else-if="hasLoaded">
      <div v-if="service.metricsMissing" class="warning-banner">
        当前服务未配置 metricsPath，Grafana / Prometheus 的可观测内容可能不完整。
      </div>

      <RiskSummary
        title="服务摘要"
        description="先看服务上下文，再进入运行态、调用链和原始查询面板。"
        :items="summaryItems"
      />

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
        v-if="activeTab === 'runtime'"
        :groups="runtimeGroups"
      />

      <ObservabilityGrid
        v-else-if="activeTab === 'tracing'"
        :groups="tracingGroups"
      />

      <section v-else-if="activeTab === 'base'" class="page-section">
        <div class="section-heading">
          <div>
            <h3>基础信息</h3>
            <p>保留结构化上下文，诊断时不用反复切后台工具。</p>
          </div>
        </div>
        <article class="panel base-info-panel">
          <dl class="key-value-list">
            <div>
              <dt>服务类型</dt>
              <dd>{{ service.serviceType || "-" }}</dd>
            </div>
            <div>
              <dt>所属节点</dt>
              <dd>
                <RouterLink
                  v-if="service.nodeId"
                  class="table-link"
                  :to="`/nodes/${service.nodeId}`"
                >
                  {{ service.nodeName || "-" }}
                </RouterLink>
                <span v-else>{{ service.nodeName || "-" }}</span>
              </dd>
            </div>
            <div>
              <dt>节点 IP</dt>
              <dd>{{ service.nodeIpAddress || "-" }}</dd>
            </div>
            <div>
              <dt>节点状态</dt>
              <dd>{{ service.nodeStatus || "-" }}</dd>
            </div>
            <div>
              <dt>端口</dt>
              <dd>{{ service.port || "-" }}</dd>
            </div>
            <div>
              <dt>进程</dt>
              <dd>{{ service.processName || "-" }}</dd>
            </div>
            <div>
              <dt>metricsPath</dt>
              <dd>{{ service.metricsPath || "未配置" }}</dd>
            </div>
          </dl>

          <div v-if="service.quickLinks?.length" class="external-links">
            <h4>外部原视图</h4>
            <div class="quick-link-list">
              <a
                v-for="link in service.quickLinks"
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
          </div>
        </article>
      </section>

      <ObservabilityGrid
        v-else
        :groups="debugGroups"
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
import { fetchServiceDetail } from "../services/api";
import { buildServicePanelGroups } from "../services/observability";

const route = useRoute();
const activeTab = ref("runtime");
const loading = ref(false);
const hasLoaded = ref(false);
const error = ref("");

const service = reactive({
  serviceName: "",
  serviceType: "",
  port: null,
  processName: "",
  metricsPath: null,
  nodeId: null,
  nodeName: "",
  nodeIpAddress: "",
  nodeStatus: "",
  metricsMissing: false,
  quickLinks: [],
});

const tabs = [
  { key: "runtime", label: "运行指标" },
  { key: "tracing", label: "调用链" },
  { key: "base", label: "基础信息" },
  { key: "debug", label: "Prometheus 调试" },
];

const headerDescription = computed(() =>
  [service.serviceType || "-", service.nodeName || "-", service.nodeStatus || "-"]
    .filter(Boolean)
    .join(" · ")
);

const headerBadges = computed(() => [
  {
    label: service.metricsMissing ? "Metrics Missing" : "Metrics OK",
    tone: service.metricsMissing ? "warn" : "info",
  },
  {
    label: service.port ? `:${service.port}` : "No Port",
    tone: "neutral",
  },
]);

const summaryItems = computed(() => [
  { label: "所属节点", value: service.nodeName || "-" },
  { label: "节点状态", value: service.nodeStatus || "-" },
  { label: "端口", value: service.port || "-" },
  { label: "进程", value: service.processName || "-" },
  { label: "metricsPath", value: service.metricsPath || "未配置" },
]);

const panelGroups = computed(() => buildServicePanelGroups(service));
const runtimeGroups = computed(() => [
  {
    key: "runtime",
    title: "运行指标",
    description: "Grafana service-detail 看板直接承接可用性、运行时和资源占用。",
    columns: 2,
    panels: panelGroups.value.runtime,
  },
]);
const tracingGroups = computed(() => [
  {
    key: "tracing",
    title: "调用链",
    description: "SkyWalking 继续作为服务视角的调用链入口。",
    columns: 1,
    panels: panelGroups.value.tracing,
  },
]);
const debugGroups = computed(() => [
  {
    key: "debug",
    title: "Prometheus 调试",
    description: "原始查询用于确认标签、实例和数据源是否一致。",
    columns: 2,
    panels: panelGroups.value.debug,
  },
]);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    Object.assign(service, await fetchServiceDetail(route.params.id));
    hasLoaded.value = true;
  } catch (err) {
    error.value = err.message || "获取服务详情失败";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.loading-panel {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5b6472;
}

.warning-banner {
  padding: 12px 16px;
  border: 1px solid #f7b955;
  border-radius: 8px;
  background: #fffaeb;
  color: #7a2e0b;
}

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

.base-info-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.table-link {
  color: #0f6cbd;
  font-weight: 500;
}

.key-value-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
}

.key-value-list div {
  padding: 12px 14px;
  border: 1px solid #d7dee8;
  border-radius: 8px;
}

.key-value-list dt {
  margin-bottom: 6px;
  color: #5b6472;
  font-size: 13px;
}

.key-value-list dd {
  margin: 0;
  color: #101828;
  font-weight: 500;
}

.external-links h4 {
  margin: 0 0 12px;
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
</style>
