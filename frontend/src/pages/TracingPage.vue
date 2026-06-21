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

    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="loading && !hasLoaded" class="panel loading-panel">
      <p>正在加载链路摘要...</p>
    </section>

    <template v-if="hasLoaded">
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

      <RiskSummary
        title="最近业务 Trace"
        description="这里直接列出最近抓到的业务调用链入口，避免先去 SkyWalking 表格里翻。"
        variant="list"
        :items="traceItems"
        empty-text="当前还没有读取到最近业务 trace，稍等自动演示流量生成后再刷新。"
      />

      <RiskSummary
        title="最新完整调用链"
        description="把最近一条业务 trace 翻译成前台可读的服务链和依赖链，不必先理解 SkyWalking 原始 span 表。"
        :items="latestTracePreviewItems"
        empty-text="当前还没有拿到完整业务调用链，先刷新或等待演示流量生成。"
      />
    </template>

    <ObservabilityGrid
      :groups="groups"
    />

    <RiskSummary
      v-if="hasLoaded"
      title="重点服务"
      description="优先把业务服务作为链路排查入口，不必先在 SkyWalking 里手动翻目录。"
      variant="list"
      :items="focusItems"
      empty-text="当前没有适合直接进入链路排查的业务服务。"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import RiskSummary from "../components/RiskSummary.vue";
import {
  fetchOverview,
  fetchServices,
  fetchTracingSummary,
} from "../services/api";
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
const tracingSummary = reactive({
  serviceNames: [],
  traces: [],
  latestTrace: null,
});

const services = ref([]);

const springBootServices = computed(() =>
  services.value.filter((service) => service.serviceType === "SPRING_BOOT")
);

const inferredDependencyChain = computed(() => {
  const dependencyOrder = ["MYSQL", "REDIS", "NGINX"];
  return dependencyOrder
    .map((serviceType) =>
      services.value.find((service) => service.serviceType === serviceType)?.serviceName
    )
    .filter(Boolean);
});

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
    detail: "缺失可抓取指标端点的对象会先影响可观测链路的完整度。",
    tone: overviewData.anomalies.services.length > 0 ? "warn" : "neutral",
  },
]);

const guidanceItems = computed(() => [
  {
    title: "优先关注业务 trace",
    detail:
      "当前最值得先看的是真实业务入口，例如 sample-service 的 /api/demo-chain，而不是只盯着服务概览页。",
  },
  {
    title: "把 /actuator/prometheus 当成噪音",
    detail:
      "这类 trace 主要来自 Prometheus 抓指标，能证明链路接入正常，但不适合作为业务调用链分析入口。",
  },
  {
    title: "Topology 和 Trace 不是一回事",
    detail:
      "Topology 看的是聚合依赖关系，Trace 看的是单次请求真实经过的节点；排查时优先看最近业务 Trace。",
  },
]);

const focusItems = computed(() =>
  springBootServices.value.map((service) => ({
    title: service.serviceName,
    detail: [
      service.serviceType,
      `${service.nodeName}:${service.port}`,
      service.metricsPath || "未配置抓取路径",
    ]
      .filter(Boolean)
      .join(" · "),
  }))
);

const traceItems = computed(() =>
  (tracingSummary.traces || []).map((trace) => ({
    title:
      trace.endpoints?.join(" -> ") ||
      trace.traceId ||
      "未命名 Trace",
    detail: [
      trace.durationMs ? `${trace.durationMs}ms` : "",
      trace.startTime || "",
      trace.error ? "ERROR" : "SUCCESS",
    ]
      .filter(Boolean)
      .join(" · "),
    tone: trace.error ? "warn" : "neutral",
  }))
);

const resolvedLatestTrace = computed(() => {
  if (tracingSummary.latestTrace) {
    return tracingSummary.latestTrace;
  }

  const firstTrace = tracingSummary.traces?.[0];
  if (!firstTrace) {
    return null;
  }

  const entryEndpoint = firstTrace.endpoints?.[0] || "未识别到业务入口";
  const serviceNames = tracingSummary.serviceNames || [];
  const firstSpringService = springBootServices.value?.[0]?.serviceName;

  return {
    traceId: firstTrace.traceId,
    entryService: firstSpringService || serviceNames[0] || "未知",
    entryEndpoint,
    serviceChain: serviceNames,
    dependencyChain: inferredDependencyChain.value,
    spanCount: null,
    durationMs: firstTrace.durationMs,
    startTime: firstTrace.startTime,
    error: firstTrace.error,
  };
});

const latestTracePreviewItems = computed(() => {
  const latestTrace = resolvedLatestTrace.value;
  if (!latestTrace) {
    return [];
  }

  const serviceChain = (latestTrace.serviceChain || []).join(" -> ");
  const dependencyChain = (latestTrace.dependencyChain || []).join(" -> ");

  return [
    {
      label: "入口服务",
      value: latestTrace.entryService || "未知",
      detail: latestTrace.entryEndpoint || "未识别到业务入口",
    },
    {
      label: "服务链",
      value: latestTrace.serviceChain?.length || 0,
      detail: serviceChain || "当前只有单服务入口",
    },
    {
      label: "依赖链",
      value: latestTrace.dependencyChain?.length || 0,
      detail: dependencyChain || "等待 SkyWalking span 明细载入后补齐 mysql / redis / nginx 等依赖链",
    },
    {
      label: "链路规模",
      value: latestTrace.spanCount || 0,
      detail: [
        latestTrace.durationMs ? `${latestTrace.durationMs}ms` : "",
        latestTrace.startTime || "",
      ]
        .filter(Boolean)
        .join(" · "),
      tone: latestTrace.error ? "warn" : "neutral",
    },
  ];
});

async function load() {
  loading.value = true;
  error.value = "";

  const [overviewResult, servicesResult, tracingResult] = await Promise.allSettled([
    fetchOverview(),
    fetchServices(),
    fetchTracingSummary(),
  ]);

  if (overviewResult.status === "fulfilled") {
    Object.assign(overviewData, overviewResult.value);
    hasLoaded.value = true;
  }

  if (servicesResult.status === "fulfilled") {
    services.value = servicesResult.value;
    hasLoaded.value = true;
  }

  if (tracingResult.status === "fulfilled") {
    Object.assign(tracingSummary, tracingResult.value);
    hasLoaded.value = true;
  }

  const errors = [overviewResult, servicesResult, tracingResult]
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
