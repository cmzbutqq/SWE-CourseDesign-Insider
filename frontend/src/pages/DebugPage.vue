<template>
  <section class="page debug-page">
    <ContextHeader
      eyebrow="Debug"
      title="监控调试"
      description="Prometheus 原始页面继续保留，但不再需要单独打开后台。"
    >
      <template #actions>
        <button class="ghost" :disabled="loading" @click="load">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </template>
    </ContextHeader>

    <section v-if="loading && !hasLoaded" class="panel loading-panel">
      <p>正在加载 Prometheus 摘要...</p>
    </section>

    <template v-else>
      <p v-if="error" class="error">{{ error }}</p>

      <RiskSummary
        title="Prometheus 摘要"
        description="先用前台摘要判断是否真有采集问题，再下钻到原始 targets 和 query。"
        :items="summaryItems"
      />

      <ObservabilityGrid
        :groups="groups"
      />

      <RiskSummary
        title="采集对象"
        description="直接看 job、实例和采集状态，不用先在原始页面里展开每个 pool。"
        variant="list"
        :items="targetItems"
        empty-text="当前没有从 Prometheus 返回可读的 target 列表。"
      />
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import RiskSummary from "../components/RiskSummary.vue";
import { buildDebugPanels } from "../services/observability";
import {
  fetchPrometheusTargets,
  summarizePrometheusTargets,
} from "../services/prometheus";

const groups = [
  {
    key: "debug",
    title: "Prometheus 调试面板",
    description: "优先验证 targets 和基础 up 查询，快速区分数据源问题与展示问题。",
    columns: 2,
    panels: buildDebugPanels(),
  },
];

const loading = ref(false);
const hasLoaded = ref(false);
const error = ref("");
const targetData = ref({ activeTargets: [] });

const summary = computed(() => summarizePrometheusTargets(targetData.value));

const summaryItems = computed(() => [
  {
    label: "活跃 targets",
    value: summary.value.totalTargets,
    detail: `当前共 ${summary.value.scrapePools} 个 scrape pools 暴露在前台。`,
  },
  {
    label: "正常采集",
    value: summary.value.upTargets,
    detail: "这部分 targets 当前处于可抓取状态。",
  },
  {
    label: "异常 targets",
    value: summary.value.problemTargets,
    detail: "down 或 unknown 的 targets 应优先回到 targets 原页确认错误。",
    tone: summary.value.problemTargets > 0 ? "warn" : "neutral",
  },
  {
    label: "最慢 scrape",
    value: `${summary.value.slowestTarget.durationMs} ms`,
    detail: `${summary.value.slowestTarget.job} · ${summary.value.slowestTarget.instance}`,
  },
]);

const targetItems = computed(() =>
  summary.value.targets.map((target) => ({
    title: `${target.job} · ${String(target.health || "").toUpperCase()}`,
    detail: [target.instance, `${target.durationMs}ms`, target.lastError]
      .filter(Boolean)
      .join(" · "),
    tone: target.health !== "up" ? "warn" : "neutral",
  }))
);

async function load() {
  loading.value = true;
  error.value = "";

  try {
    targetData.value = await fetchPrometheusTargets();
    hasLoaded.value = true;
  } catch (err) {
    error.value = err.message || "Prometheus 摘要加载失败";
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
</style>
