<template>
  <section class="page overview-page">
    <ContextHeader
      eyebrow="Overview"
      title="统一观测总览"
      description="把基础资源、应用健康、链路诊断收拢到同一块前台看板。"
    >
      <template #actions>
        <button class="ghost" :disabled="loading" @click="load">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </template>
    </ContextHeader>

    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="loading && !hasLoaded" class="panel loading-panel">
      <p>正在加载总览数据...</p>
    </section>

    <template v-else-if="hasLoaded">
      <RiskSummary
        title="风险摘要"
        description="先看平台计数，再顺着异常项往下钻到节点、服务和第三方面板。"
        :items="summaryItems"
      />

      <RiskSummary
        title="异常入口"
        description="这些对象应该优先进入节点详情或服务详情继续排障。"
        variant="list"
        tone="warn"
        :items="anomalyItems"
        empty-text="当前没有需要优先处理的高风险节点或服务。"
      />

      <section class="page-section">
        <div class="section-heading">
          <div>
            <h3>趋势分析</h3>
            <p>保留本地汇总趋势，用来配合第三方面板做节奏判断。</p>
          </div>
        </div>
        <article class="panel">
          <TrendsChart />
        </article>
      </section>

      <ObservabilityGrid
        :groups="panelGroups"
      />
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import RiskSummary from "../components/RiskSummary.vue";
import TrendsChart from "../components/TrendsChart.vue";
import { fetchOverview } from "../services/api";
import { buildOverviewPanelGroups } from "../services/observability";

const overviewData = reactive({
  nodes: { total: 0, online: 0, offline: 0, warning: 0 },
  services: { total: 0, healthy: 0, abnormal: 0 },
  unresolvedAlerts: 0,
  anomalies: { nodes: [], services: [] },
});

const loading = ref(false);
const hasLoaded = ref(false);
const error = ref("");

const summaryItems = computed(() => [
  {
    label: "节点总数",
    value: overviewData.nodes.total,
    detail: `在线 ${overviewData.nodes.online} / 告警 ${overviewData.nodes.warning} / 离线 ${overviewData.nodes.offline}`,
  },
  {
    label: "服务总数",
    value: overviewData.services.total,
    detail: `健康 ${overviewData.services.healthy} / 异常 ${overviewData.services.abnormal}`,
  },
  {
    label: "未处理告警",
    value: overviewData.unresolvedAlerts,
    detail: "数值持续非零时优先处理异常清单。",
    tone: overviewData.unresolvedAlerts > 0 ? "warn" : "neutral",
  },
  {
    label: "高风险节点",
    value: overviewData.anomalies.nodes.length,
    detail: "可直接进入节点详情查看资源、服务与原始指标。",
  },
  {
    label: "高风险服务",
    value: overviewData.anomalies.services.length,
    detail: "优先确认 metricsPath、实例标签与运行态面板。",
  },
]);

const anomalyItems = computed(() => [
  ...overviewData.anomalies.nodes.map((node) => ({
    title: `高风险节点 · ${node.nodeName || node.id}`,
    detail: [node.reason || node.status || "状态异常", node.durationSeconds ? `持续 ${node.durationSeconds}s` : ""]
      .filter(Boolean)
      .join(" · "),
    tone: "warn",
  })),
  ...overviewData.anomalies.services.map((service) => ({
    title: `高风险服务 · ${service.serviceName || service.id}`,
    detail: [service.nodeName || "-", service.errorType || service.status || "指标异常"]
      .filter(Boolean)
      .join(" · "),
    tone: "warn",
  })),
]);

const panelGroups = computed(() =>
  buildOverviewPanelGroups().map((group) => ({
    ...group,
    columns: group.columns || (group.panels.length >= 3 ? 3 : 2),
  }))
);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    Object.assign(overviewData, await fetchOverview());
    hasLoaded.value = true;
  } catch (err) {
    error.value = err.message || "获取总览数据失败";
  } finally {
    loading.value = false;
  }
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
</style>
