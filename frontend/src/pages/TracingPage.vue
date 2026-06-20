<template>
  <section class="page tracing-page">
    <ContextHeader
      eyebrow="Tracing"
      title="链路观测"
      description="把 SkyWalking 的服务视图直接放进前台工作区。"
    >
      <template #actions>
        <button class="ghost" :disabled="loading" @click="load">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </template>
    </ContextHeader>

    <section v-if="loading && !hasLoaded" class="panel loading-panel">
      <p>正在加载链路摘要...</p>
    </section>

    <template v-else>
      <p v-if="error" class="error">{{ error }}</p>

      <RiskSummary
        title="链路摘要"
        description="先看前台对象摘要，再进入 SkyWalking 工作区查看服务、拓扑和 trace。"
        :items="summaryItems"
      />

      <RiskSummary
        title="排查提示"
        description="先看业务入口，再决定是否需要切到 SkyWalking 原始界面深挖 span。"
        variant="list"
        :items="guidanceItems"
      />

      <ObservabilityGrid
        :groups="groups"
      />

      <RiskSummary
        title="重点服务"
        description="优先把业务服务作为链路排查入口，不必先在 SkyWalking 里手动翻目录。"
        variant="list"
        :items="focusItems"
        empty-text="当前没有适合直接进入链路排查的业务服务。"
      />
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import RiskSummary from "../components/RiskSummary.vue";
import { fetchOverview, fetchServices } from "../services/api";
import { buildTracingPanels } from "../services/observability";

const groups = [
  {
    key: "tracing",
    title: "SkyWalking 工作区",
    description: "统一承接服务视图与链路入口，避免再去后台首页找面板。",
    columns: 1,
    panels: buildTracingPanels(),
  },
];

const loading = ref(false);
const hasLoaded = ref(false);
const error = ref("");

const overviewData = reactive({
  nodes: { total: 0, online: 0, offline: 0, warning: 0 },
  services: { total: 0, healthy: 0, abnormal: 0 },
  anomalies: { nodes: [], services: [] },
});

const services = ref([]);

const springBootServices = computed(() =>
  services.value.filter((service) => service.serviceType === "SPRING_BOOT")
);

const summaryItems = computed(() => [
  {
    label: "在线节点",
    value: overviewData.nodes.online,
    detail: `共 ${overviewData.nodes.total} 个节点，当前前台链路入口可直接追到在线主机。`,
  },
  {
    label: "识别服务",
    value: overviewData.services.total,
    detail: `前台已收口 ${overviewData.services.healthy} 个健康服务与 ${overviewData.services.abnormal} 个异常服务。`,
  },
  {
    label: "Spring Boot 服务",
    value: springBootServices.value.length,
    detail: "这类业务服务最适合作为链路观测的第一落点。",
  },
  {
    label: "异常服务",
    value: overviewData.anomalies.services.length,
    detail: "缺失 metricsPath 的对象会先影响可观测链路的完整度。",
    tone: overviewData.anomalies.services.length > 0 ? "warn" : "neutral",
  },
]);

const guidanceItems = computed(() => [
  {
    title: "优先关注业务 trace",
    detail:
      "当前最值得先看的是真实业务入口，例如 sample-service 的 /api/hello，而不是只盯着服务概览页。",
  },
  {
    title: "把 /actuator/prometheus 当成噪音",
    detail:
      "这类 trace 主要来自 Prometheus 抓指标，能证明链路接入正常，但不适合作为业务调用链分析入口。",
  },
  {
    title: "当前调用链偏浅是示例服务特征",
    detail:
      "sample-service 现在主要是单入口响应，还没有继续调用数据库、Redis 或下游服务，所以 span 树会比较短。",
  },
]);

const focusItems = computed(() =>
  springBootServices.value.map((service) => ({
    title: service.serviceName,
    detail: [
      service.serviceType,
      `${service.nodeName}:${service.port}`,
      service.metricsPath || "未配置 metricsPath",
    ]
      .filter(Boolean)
      .join(" · "),
  }))
);

async function load() {
  loading.value = true;
  error.value = "";

  const [overviewResult, servicesResult] = await Promise.allSettled([
    fetchOverview(),
    fetchServices(),
  ]);

  if (overviewResult.status === "fulfilled") {
    Object.assign(overviewData, overviewResult.value);
    hasLoaded.value = true;
  }

  if (servicesResult.status === "fulfilled") {
    services.value = servicesResult.value;
    hasLoaded.value = true;
  }

  const errors = [overviewResult, servicesResult]
    .filter((result) => result.status === "rejected")
    .map((result) => result.reason?.message || "摘要数据加载失败");

  if (errors.length) {
    error.value = errors.join("；");
  }

  loading.value = false;
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
