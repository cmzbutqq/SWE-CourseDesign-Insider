# Observability Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current Vue monitoring shell into a dense desktop observability workspace that exposes Grafana, SkyWalking, and Prometheus panels directly inside the frontend, while adding the missing service-detail API and preserving useful local summaries plus panel fallback states.

**Architecture:** The Vue app owns navigation, section grouping, tabs, summaries, and iframe failure handling. Grafana and Prometheus are embedded through same-origin sub-paths proxied by Vite, SkyWalking stays on its direct UI origin unless runtime verification proves a sub-path works cleanly, and the Spring backend only adds the missing service-detail read model and context for drill-down pages.

**Tech Stack:** Vue 3, Vue Router 4, Vitest, Spring Boot, Maven, Docker Compose, Grafana, Prometheus, SkyWalking, Vite proxy.

---

## File Structure

### Backend API

- Create: `backend/src/main/java/com/scut/monitoring/backend/dto/ServiceDetailResponse.java`
  - Dedicated read model for `/api/services/{id}`.
- Modify: `backend/src/main/java/com/scut/monitoring/backend/controller/PortalController.java:27-54`
  - Add the new read endpoint without changing existing routes.
- Modify: `backend/src/main/java/com/scut/monitoring/backend/service/NodeRegistryService.java:222-497`
  - Add `getService(Long id)`, service detail mapping, and dashboard-aware quick-link generation.
- Modify: `backend/src/test/java/com/scut/monitoring/backend/controller/PortalControllerTest.java:1-38`
  - Cover the new 404 path.
- Modify: `backend/src/test/java/com/scut/monitoring/backend/service/NodeRegistryServiceTest.java:1-260`
  - Cover the new service-detail response shape and quick links.

### Frontend data and embed URL generation

- Modify: `frontend/src/services/api.js:1-40`
  - Add `fetchServiceDetail(id)`.
- Modify: `frontend/src/services/api.test.js:1-9`
  - Cover the new API call.
- Create: `frontend/src/services/observability.js`
  - Central source for Grafana/Prometheus/SkyWalking URLs and page-level panel definitions.
- Create: `frontend/src/services/observability.test.js`
  - Verify embed URL generation and parameter encoding.
- Modify: `frontend/vite.config.js:1-20`
  - Proxy `/grafana` and `/prometheus` in addition to `/api`.
- Modify: `frontend/vite.config.test.js:1-8`
  - Lock the proxy map down with tests.

### Frontend shell and reusable observability components

- Modify: `frontend/src/router/index.js:1-18`
  - Add `/services/:id`, `/tracing`, and `/debug`.
- Modify: `frontend/src/App.vue:1-22`
  - Replace the tiny shell with a desktop operations frame.
- Modify: `frontend/src/style.css:1-96`
  - Establish the denser shared desktop layout, section, tab, and table styles.
- Create: `frontend/src/router/index.test.js`
  - Route coverage for the expanded information architecture.
- Create: `frontend/src/components/ContextHeader.vue`
  - Shared title row for detail pages.
- Create: `frontend/src/components/ObservabilityGrid.vue`
  - Shared grouped panel layout.
- Create: `frontend/src/components/ObservabilityPanel.vue`
  - Local wrapper with iframe, source badge, load state, and fallback link.
- Create: `frontend/src/components/RiskSummary.vue`
  - Shared risk/anomaly summary cards and lists.
- Create: `frontend/src/components/ObservabilityPanel.test.js`
  - Success, failure, and fallback coverage for the wrapper.

### Frontend pages

- Modify: `frontend/src/pages/OverviewPage.vue`
  - Rebuild the home page into the main cockpit.
- Create: `frontend/src/pages/OverviewPage.test.js`
  - Verify grouped observability sections and local summaries.
- Modify: `frontend/src/pages/NodesPage.vue`
  - Make the node list feel like an asset-and-health index.
- Modify: `frontend/src/pages/NodeDetailPage.vue`
  - Replace the current static detail view with tabbed observability sections.
- Create: `frontend/src/pages/NodeDetailPage.test.js`
  - Verify tabs and grouped panel sections.
- Modify: `frontend/src/pages/ServicesPage.vue`
  - Upgrade the service list into a true observability index with detail-entry links.
- Create: `frontend/src/pages/ServiceDetailPage.vue`
  - Dedicated service detail page with runtime, tracing, base info, and Prometheus debug tabs.
- Create: `frontend/src/pages/ServiceDetailPage.test.js`
  - Verify grouped sections and missing-metrics warning.
- Create: `frontend/src/pages/TracingPage.vue`
  - Dedicated SkyWalking-centered route.
- Create: `frontend/src/pages/DebugPage.vue`
  - Dedicated Prometheus-centered route.

### Observability runtime and dashboards

- Modify: `docker-compose.yml:23-176`
  - Enable Grafana embedding, anonymous viewer mode, Prometheus sub-path routing, and frontend proxy env.
- Create: `infra/grafana/dashboards/platform-overview.json`
  - Global infrastructure and service panels.
- Create: `infra/grafana/dashboards/node-detail.json`
  - Node-focused Grafana dashboard with `job` variable support.
- Create: `infra/grafana/dashboards/service-detail.json`
  - Service-focused Grafana dashboard with `service` variable support.
- Modify: `infra/grafana/dashboards/monitoring-overview.json`
  - Retire or redirect the old single-dashboard assumption so backend links and docs do not drift.

## Task 1: Add the service-detail backend API

**Files:**
- Create: `backend/src/main/java/com/scut/monitoring/backend/dto/ServiceDetailResponse.java`
- Modify: `backend/src/main/java/com/scut/monitoring/backend/controller/PortalController.java:41-54`
- Modify: `backend/src/main/java/com/scut/monitoring/backend/service/NodeRegistryService.java:222-497`
- Modify: `backend/src/test/java/com/scut/monitoring/backend/controller/PortalControllerTest.java`
- Modify: `backend/src/test/java/com/scut/monitoring/backend/service/NodeRegistryServiceTest.java`
- Test: `backend/src/test/java/com/scut/monitoring/backend/controller/PortalControllerTest.java`
- Test: `backend/src/test/java/com/scut/monitoring/backend/service/NodeRegistryServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// backend/src/test/java/com/scut/monitoring/backend/controller/PortalControllerTest.java
@Test
void serviceDetailShouldReturnNotFoundWhenServiceDoesNotExist() throws Exception {
    doThrow(new EntityNotFoundException("Service not found: 999"))
            .when(nodeRegistryService)
            .getService(999L);

    mockMvc.perform(get("/api/services/999"))
            .andExpect(status().isNotFound());
}

// backend/src/test/java/com/scut/monitoring/backend/service/NodeRegistryServiceTest.java
@Test
void getServiceShouldReturnMetricsWarningAndNodeContext() {
    ManagedNode node = createNode("app-node", "ONLINE");
    ReflectionTestUtils.setField(node, "id", 3L);
    node.setIpAddress("172.20.0.10");

    DiscoveredService service = new DiscoveredService();
    ReflectionTestUtils.setField(service, "id", 7L);
    service.setNode(node);
    service.setServiceName("sample-service");
    service.setServiceType("SPRING_BOOT");
    service.setPort(8081);
    service.setProcessName("java");
    service.setMetricsPath(" ");

    when(discoveredServiceRepository.findById(7L)).thenReturn(Optional.of(service));

    var response = nodeRegistryService.getService(7L);

    assertThat(response.nodeId()).isEqualTo(3L);
    assertThat(response.nodeName()).isEqualTo("app-node");
    assertThat(response.nodeStatus()).isEqualTo("WARNING");
    assertThat(response.metricsMissing()).isTrue();
    assertThat(response.quickLinks())
            .extracting(QuickLinkDTO::name)
            .containsExactly("Grafana", "Prometheus", "SkyWalking");
}
```

- [ ] **Step 2: Run the backend tests to verify they fail**

Run:

```bash
docker compose exec backend sh /workspace/infra/maven/mvn-with-mirror.sh -q -Dtest=PortalControllerTest,NodeRegistryServiceTest test
```

Expected: FAIL with compiler errors because `getService(...)` and `ServiceDetailResponse` do not exist yet.

- [ ] **Step 3: Write the minimal implementation**

```java
// backend/src/main/java/com/scut/monitoring/backend/dto/ServiceDetailResponse.java
package com.scut.monitoring.backend.dto;

import java.util.List;

public record ServiceDetailResponse(
        Long id,
        String serviceName,
        String serviceType,
        Integer port,
        String processName,
        String metricsPath,
        Long nodeId,
        String nodeName,
        String nodeIpAddress,
        String nodeStatus,
        boolean metricsMissing,
        List<QuickLinkDTO> quickLinks
) {
}

// backend/src/main/java/com/scut/monitoring/backend/controller/PortalController.java
@GetMapping("/services/{id}")
public ServiceDetailResponse serviceDetail(@PathVariable Long id) {
    return nodeRegistryService.getService(id);
}

// backend/src/main/java/com/scut/monitoring/backend/service/NodeRegistryService.java
@Transactional(readOnly = true)
public ServiceDetailResponse getService(Long id) {
    DiscoveredService service = discoveredServiceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Service not found: " + id));
    return toServiceDetail(service);
}

private ServiceDetailResponse toServiceDetail(DiscoveredService service) {
    ManagedNode node = service.getNode();
    Instant now = Instant.now();
    return new ServiceDetailResponse(
            service.getId(),
            service.getServiceName(),
            service.getServiceType(),
            service.getPort(),
            service.getProcessName(),
            service.getMetricsPath(),
            node.getId(),
            node.getNodeName(),
            node.getIpAddress(),
            effectiveStatus(node, now),
            isAbnormalService(service),
            buildServiceQuickLinks(service)
    );
}

private List<QuickLinkDTO> buildServiceQuickLinks(DiscoveredService service) {
    String serviceName = service.getServiceName() == null ? "" : service.getServiceName();
    String expr = "up{job=\"" + serviceName + "\"}";
    return List.of(
            new QuickLinkDTO("Grafana", grafanaBaseUrl + "/d/service-detail/service-detail?var-service=" + encodeQueryValue(serviceName)),
            new QuickLinkDTO("Prometheus", prometheusBaseUrl + "/graph?g0.expr=" + encodeQueryValue(expr)),
            new QuickLinkDTO("SkyWalking", skywalkingBaseUrl + "/general-service")
    );
}
```

- [ ] **Step 4: Run the backend tests to verify they pass**

Run:

```bash
docker compose exec backend sh /workspace/infra/maven/mvn-with-mirror.sh -q -Dtest=PortalControllerTest,NodeRegistryServiceTest test
```

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 5: Commit the backend API slice**

```bash
git add backend/src/main/java/com/scut/monitoring/backend/dto/ServiceDetailResponse.java \
  backend/src/main/java/com/scut/monitoring/backend/controller/PortalController.java \
  backend/src/main/java/com/scut/monitoring/backend/service/NodeRegistryService.java \
  backend/src/test/java/com/scut/monitoring/backend/controller/PortalControllerTest.java \
  backend/src/test/java/com/scut/monitoring/backend/service/NodeRegistryServiceTest.java
git commit -m "feat: add service detail api"
```

## Task 2: Centralize observability URL generation and frontend API access

**Files:**
- Create: `frontend/src/services/observability.js`
- Create: `frontend/src/services/observability.test.js`
- Modify: `frontend/src/services/api.js:1-40`
- Modify: `frontend/src/services/api.test.js`
- Modify: `frontend/vite.config.js:1-20`
- Modify: `frontend/vite.config.test.js`
- Test: `frontend/src/services/observability.test.js`
- Test: `frontend/src/services/api.test.js`
- Test: `frontend/vite.config.test.js`

- [ ] **Step 1: Write the failing frontend data-layer tests**

```js
// frontend/src/services/observability.test.js
import { describe, expect, it } from "vitest";
import {
  buildGrafanaPanelUrl,
  buildPrometheusGraphUrl,
  resolveObservabilityConfig,
} from "./observability";

describe("observability urls", () => {
  it("builds Grafana solo-panel urls on the proxied sub-path", () => {
    const url = buildGrafanaPanelUrl(
      {
        dashboardUid: "node-detail",
        panelId: 4,
        vars: { job: "app-node" },
      },
      {
        VITE_GRAFANA_BASE_URL: "/grafana",
      }
    );

    expect(url).toContain("/grafana/d-solo/node-detail?");
    expect(url).toContain("viewPanel=4");
    expect(url).toContain("var-job=app-node");
  });

  it("builds Prometheus graph urls on the proxied sub-path", () => {
    const url = buildPrometheusGraphUrl('up{job="app-node"}', {
      VITE_PROMETHEUS_BASE_URL: "/prometheus",
    });

    expect(url).toContain("/prometheus/graph?");
    expect(url).toContain("g0.expr=up%7Bjob%3D%22app-node%22%7D");
  });

  it("defaults SkyWalking to its direct absolute origin", () => {
    expect(resolveObservabilityConfig({}).skywalkingBaseUrl).toBe("http://localhost:18082");
  });
});

// frontend/src/services/api.test.js
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchServiceDetail, resolveApiBaseUrl } from "./api";

describe("api service helpers", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("uses the relative api path by default", () => {
    expect(resolveApiBaseUrl({})).toBe("/api");
  });

  it("requests service detail by id", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: true,
      json: async () => ({ id: 7 }),
    });

    await fetchServiceDetail(7);

    expect(fetchMock).toHaveBeenCalledWith("/api/services/7");
  });
});

// frontend/vite.config.test.js
import { describe, expect, it } from "vitest";
import config from "./vite.config.js";

describe("vite config", () => {
  it("proxies backend and observability requests", () => {
    expect(config.server.proxy["/api"].target).toBe("http://backend:8080");
    expect(config.server.proxy["/grafana"].target).toBe("http://grafana:3000");
    expect(config.server.proxy["/prometheus"].target).toBe("http://prometheus:9090");
  });
});
```

- [ ] **Step 2: Run the frontend tests to verify they fail**

Run:

```bash
docker compose exec frontend npm test -- src/services/api.test.js src/services/observability.test.js vite.config.test.js
```

Expected: FAIL because `fetchServiceDetail`, `observability.js`, and the new proxy entries do not exist yet.

- [ ] **Step 3: Write the minimal implementation**

```js
// frontend/src/services/api.js
export function fetchServiceDetail(id) {
  return fetchJson(`/services/${id}`);
}

// frontend/src/services/observability.js
export function resolveObservabilityConfig(env = import.meta.env) {
  return {
    grafanaBaseUrl: env.VITE_GRAFANA_BASE_URL || "/grafana",
    prometheusBaseUrl: env.VITE_PROMETHEUS_BASE_URL || "/prometheus",
    skywalkingBaseUrl: env.VITE_SKYWALKING_BASE_URL || "http://localhost:18082",
    grafanaFrom: env.VITE_GRAFANA_FROM || "now-6h",
    grafanaTo: env.VITE_GRAFANA_TO || "now",
  };
}

export function buildGrafanaPanelUrl({ dashboardUid, panelId, vars = {}, from, to }, env = import.meta.env) {
  const config = resolveObservabilityConfig(env);
  const params = new URLSearchParams({
    orgId: "1",
    theme: "light",
    viewPanel: String(panelId),
    from: from || config.grafanaFrom,
    to: to || config.grafanaTo,
  });

  Object.entries(vars).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.append(`var-${key}`, String(value));
    }
  });

  return `${config.grafanaBaseUrl}/d-solo/${dashboardUid}?${params.toString()}`;
}

export function buildGrafanaDashboardUrl({ dashboardUid, vars = {} }, env = import.meta.env) {
  const config = resolveObservabilityConfig(env);
  const params = new URLSearchParams();
  Object.entries(vars).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.append(`var-${key}`, String(value));
    }
  });
  return `${config.grafanaBaseUrl}/d/${dashboardUid}/${dashboardUid}${params.toString() ? `?${params.toString()}` : ""}`;
}

export function buildPrometheusGraphUrl(expr, env = import.meta.env) {
  const config = resolveObservabilityConfig(env);
  const params = new URLSearchParams({
    "g0.expr": expr,
    "g0.tab": "0",
    "g0.range_input": "1h",
  });
  return `${config.prometheusBaseUrl}/graph?${params.toString()}`;
}

export function buildPrometheusTargetsUrl(env = import.meta.env) {
  return `${resolveObservabilityConfig(env).prometheusBaseUrl}/targets`;
}

export function buildSkyWalkingUrl(path = "/", env = import.meta.env) {
  const baseUrl = resolveObservabilityConfig(env).skywalkingBaseUrl.replace(/\/$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${baseUrl}${normalizedPath}`;
}

// frontend/vite.config.js
const apiTarget = process.env.VITE_PROXY_TARGET || "http://backend:8080";
const grafanaTarget = process.env.VITE_GRAFANA_PROXY_TARGET || "http://grafana:3000";
const prometheusTarget = process.env.VITE_PROMETHEUS_PROXY_TARGET || "http://prometheus:9090";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: "0.0.0.0",
    proxy: {
      "/api": { target: apiTarget, changeOrigin: true },
      "/grafana": { target: grafanaTarget, changeOrigin: true },
      "/prometheus": { target: prometheusTarget, changeOrigin: true },
    },
  },
  test: { environment: "jsdom" },
});
```

- [ ] **Step 4: Run the frontend tests to verify they pass**

Run:

```bash
docker compose exec frontend npm test -- src/services/api.test.js src/services/observability.test.js vite.config.test.js
```

Expected: PASS with all three targeted test files green.

- [ ] **Step 5: Commit the observability URL layer**

```bash
git add frontend/src/services/api.js \
  frontend/src/services/api.test.js \
  frontend/src/services/observability.js \
  frontend/src/services/observability.test.js \
  frontend/vite.config.js \
  frontend/vite.config.test.js
git commit -m "feat: add observability url helpers"
```

## Task 3: Expand the shell, routes, and reusable observability wrappers

**Files:**
- Modify: `frontend/src/router/index.js:1-18`
- Modify: `frontend/src/App.vue:1-22`
- Modify: `frontend/src/style.css:1-96`
- Create: `frontend/src/router/index.test.js`
- Create: `frontend/src/components/ContextHeader.vue`
- Create: `frontend/src/components/ObservabilityGrid.vue`
- Create: `frontend/src/components/ObservabilityPanel.vue`
- Create: `frontend/src/components/RiskSummary.vue`
- Create: `frontend/src/components/ObservabilityPanel.test.js`
- Test: `frontend/src/router/index.test.js`
- Test: `frontend/src/components/ObservabilityPanel.test.js`

- [ ] **Step 1: Write the failing routing and panel-wrapper tests**

```js
// frontend/src/router/index.test.js
import { describe, expect, it } from "vitest";
import router from "./index";

describe("router", () => {
  it("registers the observability routes", () => {
    const paths = router.getRoutes().map((route) => route.path);

    expect(paths).toEqual(
      expect.arrayContaining([
        "/overview",
        "/nodes",
        "/nodes/:id",
        "/services",
        "/services/:id",
        "/tracing",
        "/debug",
      ])
    );
  });
});

// frontend/src/components/ObservabilityPanel.test.js
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import ObservabilityPanel from "./ObservabilityPanel.vue";

describe("ObservabilityPanel", () => {
  it("shows the iframe after a successful load", async () => {
    const wrapper = mount(ObservabilityPanel, {
      props: {
        title: "CPU 使用率",
        source: "Grafana",
        src: "/grafana/d-solo/node-detail?viewPanel=2",
        fallbackHref: "/grafana/d/node-detail/node-detail",
      },
    });

    await wrapper.find("iframe").trigger("load");

    expect(wrapper.find(".observability-panel__frame").exists()).toBe(true);
    expect(wrapper.find(".observability-panel__error").exists()).toBe(false);
  });

  it("shows a local fallback state after an iframe error", async () => {
    const wrapper = mount(ObservabilityPanel, {
      props: {
        title: "Prometheus Targets",
        source: "Prometheus",
        src: "/prometheus/targets",
        fallbackHref: "/prometheus/targets",
      },
    });

    await wrapper.find("iframe").trigger("error");

    expect(wrapper.find(".observability-panel__error").text()).toContain("Prometheus");
    expect(wrapper.find("a").attributes("href")).toBe("/prometheus/targets");
  });
});
```

- [ ] **Step 2: Run the shell/component tests to verify they fail**

Run:

```bash
docker compose exec frontend npm test -- src/router/index.test.js src/components/ObservabilityPanel.test.js
```

Expected: FAIL because the routes and components do not exist yet.

- [ ] **Step 3: Write the minimal implementation**

```vue
<!-- frontend/src/App.vue -->
<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <strong>SCUT 观测台</strong>
        <span>Unified Monitoring</span>
      </div>
      <nav class="sidebar-nav">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" class="sidebar-link">
          <span class="sidebar-link__label">{{ item.label }}</span>
          <span class="sidebar-link__hint">{{ item.hint }}</span>
        </RouterLink>
      </nav>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
const navItems = [
  { to: "/overview", label: "总览", hint: "全局态势" },
  { to: "/nodes", label: "节点", hint: "主机与资源" },
  { to: "/services", label: "服务", hint: "应用与接口" },
  { to: "/tracing", label: "链路", hint: "SkyWalking" },
  { to: "/debug", label: "监控调试", hint: "Prometheus" },
];
</script>

<!-- frontend/src/router/index.js -->
import ServiceDetailPage from "../pages/ServiceDetailPage.vue";
import TracingPage from "../pages/TracingPage.vue";
import DebugPage from "../pages/DebugPage.vue";

routes: [
  { path: "/", redirect: "/overview" },
  { path: "/overview", component: OverviewPage },
  { path: "/nodes", component: NodesPage },
  { path: "/nodes/:id", component: NodeDetailPage, props: true },
  { path: "/services", component: ServicesPage },
  { path: "/services/:id", component: ServiceDetailPage, props: true },
  { path: "/tracing", component: TracingPage },
  { path: "/debug", component: DebugPage },
]

<!-- frontend/src/components/ObservabilityPanel.vue -->
<template>
  <article class="observability-panel">
    <header class="observability-panel__header">
      <div>
        <h3>{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="observability-panel__meta">
        <span class="observability-panel__source">{{ source }}</span>
        <a :href="fallbackHref" target="_blank" rel="noopener">打开原视图</a>
      </div>
    </header>

    <div v-if="state === 'error'" class="observability-panel__error">
      <strong>{{ title }}</strong>
      <span>{{ source }} 面板加载失败</span>
      <a :href="fallbackHref" target="_blank" rel="noopener">转到 {{ source }}</a>
    </div>

    <iframe
      v-else
      class="observability-panel__frame"
      :src="src"
      :title="title"
      :style="{ height: `${height}px` }"
      loading="lazy"
      @load="state = 'ready'"
      @error="state = 'error'"
    />
  </article>
</template>

<script setup>
import { ref } from "vue";

defineProps({
  title: { type: String, required: true },
  source: { type: String, required: true },
  src: { type: String, required: true },
  fallbackHref: { type: String, required: true },
  description: { type: String, default: "" },
  height: { type: Number, default: 320 },
});

const state = ref("loading");
</script>
```

```vue
<!-- frontend/src/components/ContextHeader.vue -->
<template>
  <header class="context-header">
    <div>
      <p class="eyebrow">{{ eyebrow }}</p>
      <h2>{{ title }}</h2>
      <p v-if="subtitle" class="context-header__subtitle">{{ subtitle }}</p>
    </div>
    <button type="button" class="ghost" @click="$emit('refresh')">刷新</button>
  </header>
</template>

<script setup>
defineEmits(["refresh"]);
defineProps({
  eyebrow: { type: String, default: "" },
  title: { type: String, required: true },
  subtitle: { type: String, default: "" },
});
</script>

<!-- frontend/src/components/ObservabilityGrid.vue -->
<template>
  <section class="panel-stack">
    <header class="panel-stack__header">
      <h3>{{ title }}</h3>
      <p v-if="description">{{ description }}</p>
    </header>
    <div class="observability-grid">
      <ObservabilityPanel v-for="panel in panels" :key="panel.title" v-bind="panel" />
    </div>
  </section>
</template>

<script setup>
import ObservabilityPanel from "./ObservabilityPanel.vue";

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: "" },
  panels: { type: Array, required: true },
});
</script>

<!-- frontend/src/components/RiskSummary.vue -->
<template>
  <section class="panel risk-summary">
    <header class="panel-stack__header">
      <h3>{{ title }}</h3>
    </header>
    <div class="cards">
      <article v-for="item in items" :key="item.label" class="card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </div>
    <div v-if="nodes.length" class="risk-summary__list">
      <h4>{{ nodeTitle }}</h4>
      <ul>
        <li v-for="node in nodes" :key="node.id">{{ node.nodeName }} · {{ node.reason || node.status }}</li>
      </ul>
    </div>
    <div v-if="services.length" class="risk-summary__list">
      <h4>{{ serviceTitle }}</h4>
      <ul>
        <li v-for="service in services" :key="service.id">{{ service.serviceName }} · {{ service.errorType || service.metricsPath || "未配置" }}</li>
      </ul>
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  items: { type: Array, required: true },
  nodes: { type: Array, default: () => [] },
  services: { type: Array, default: () => [] },
  nodeTitle: { type: String, default: "高风险节点" },
  serviceTitle: { type: String, default: "高风险服务" },
});
</script>
```

- [ ] **Step 4: Run the shell/component tests to verify they pass**

Run:

```bash
docker compose exec frontend npm test -- src/router/index.test.js src/components/ObservabilityPanel.test.js
```

Expected: PASS with both targeted files green.

- [ ] **Step 5: Commit the shell and component layer**

```bash
git add frontend/src/router/index.js \
  frontend/src/router/index.test.js \
  frontend/src/App.vue \
  frontend/src/style.css \
  frontend/src/components/ContextHeader.vue \
  frontend/src/components/ObservabilityGrid.vue \
  frontend/src/components/ObservabilityPanel.vue \
  frontend/src/components/ObservabilityPanel.test.js \
  frontend/src/components/RiskSummary.vue
git commit -m "feat: add observability shell and wrappers"
```

## Task 4: Rebuild the overview page into the main observability cockpit

**Files:**
- Modify: `frontend/src/pages/OverviewPage.vue`
- Modify: `frontend/src/services/observability.js`
- Create: `frontend/src/pages/OverviewPage.test.js`
- Test: `frontend/src/pages/OverviewPage.test.js`

- [ ] **Step 1: Write the failing overview-page test**

```js
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import OverviewPage from "./OverviewPage.vue";
import { fetchOverview } from "../services/api";

vi.mock("../services/api", () => ({
  fetchOverview: vi.fn(),
}));

describe("OverviewPage", () => {
  beforeEach(() => {
    fetchOverview.mockReset();
  });

  it("renders grouped observability sections after loading", async () => {
    fetchOverview.mockResolvedValue({
      nodes: { total: 2, online: 2, offline: 0, warning: 0 },
      services: { total: 5, healthy: 4, abnormal: 1 },
      unresolvedAlerts: 1,
      anomalies: { nodes: [], services: [] },
      quickLinks: [],
    });

    const wrapper = mount(OverviewPage);
    await flushPromises();

    expect(wrapper.text()).toContain("基础资源");
    expect(wrapper.text()).toContain("应用健康");
    expect(wrapper.text()).toContain("链路观测");
    expect(wrapper.text()).toContain("高风险节点");
  });
});
```

- [ ] **Step 2: Run the overview-page test to verify it fails**

Run:

```bash
docker compose exec frontend npm test -- src/pages/OverviewPage.test.js
```

Expected: FAIL because the new grouped sections are not rendered yet.

- [ ] **Step 3: Write the minimal overview implementation**

```js
// frontend/src/services/observability.js
export function buildOverviewPanelGroups() {
  return [
    {
      key: "infrastructure",
      title: "基础资源",
      panels: [
        {
          title: "节点在线情况",
          source: "Grafana",
          src: buildGrafanaPanelUrl({ dashboardUid: "platform-overview", panelId: 1 }),
          fallbackHref: buildGrafanaDashboardUrl({ dashboardUid: "platform-overview" }),
        },
        {
          title: "主机 CPU",
          source: "Grafana",
          src: buildGrafanaPanelUrl({ dashboardUid: "platform-overview", panelId: 2 }),
          fallbackHref: buildGrafanaDashboardUrl({ dashboardUid: "platform-overview" }),
        },
      ],
    },
    {
      key: "applications",
      title: "应用健康",
      panels: [
        {
          title: "服务存活",
          source: "Grafana",
          src: buildGrafanaPanelUrl({ dashboardUid: "platform-overview", panelId: 3 }),
          fallbackHref: buildGrafanaDashboardUrl({ dashboardUid: "platform-overview" }),
        },
        {
          title: "Prometheus Targets",
          source: "Prometheus",
          src: buildPrometheusTargetsUrl(),
          fallbackHref: buildPrometheusTargetsUrl(),
        },
      ],
    },
    {
      key: "tracing",
      title: "链路观测",
      panels: [
        {
          title: "SkyWalking 总览",
          source: "SkyWalking",
          src: buildSkyWalkingUrl("/general-service"),
          fallbackHref: buildSkyWalkingUrl("/general-service"),
        },
      ],
    },
  ];
}
```

```vue
<!-- frontend/src/pages/OverviewPage.vue -->
<template>
  <section class="page overview-page">
    <ContextHeader
      eyebrow="Overview"
      title="统一观测总览"
      subtitle="把基础资源、应用健康和链路视图收拢在一个桌面工作台里。"
      @refresh="load"
    />

    <RiskSummary
      title="风险摘要"
      :items="summaryItems"
      :nodes="overviewData.anomalies.nodes"
      :services="overviewData.anomalies.services"
    />

    <ObservabilityGrid
      v-for="group in panelGroups"
      :key="group.key"
      :title="group.title"
      :panels="group.panels"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { fetchOverview } from "../services/api";
import { buildOverviewPanelGroups } from "../services/observability";
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import RiskSummary from "../components/RiskSummary.vue";

const overviewData = reactive({
  nodes: { total: 0, online: 0, offline: 0, warning: 0 },
  services: { total: 0, healthy: 0, abnormal: 0 },
  unresolvedAlerts: 0,
  anomalies: { nodes: [], services: [] },
});
const loading = ref(false);
const error = ref("");

const summaryItems = computed(() => [
  { label: "节点总数", value: overviewData.nodes.total },
  { label: "在线节点", value: overviewData.nodes.online },
  { label: "异常服务", value: overviewData.services.abnormal },
  { label: "未处理告警", value: overviewData.unresolvedAlerts },
  { label: "高风险节点", value: overviewData.anomalies.nodes.length },
  { label: "高风险服务", value: overviewData.anomalies.services.length },
]);

const panelGroups = computed(() => buildOverviewPanelGroups());

async function load() {
  loading.value = true;
  error.value = "";
  try {
    Object.assign(overviewData, await fetchOverview());
  } catch (err) {
    error.value = err.message || "获取总览失败";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
```

- [ ] **Step 4: Run the overview-page test to verify it passes**

Run:

```bash
docker compose exec frontend npm test -- src/pages/OverviewPage.test.js
```

Expected: PASS and the grouped headings are present.

- [ ] **Step 5: Commit the overview cockpit**

```bash
git add frontend/src/pages/OverviewPage.vue \
  frontend/src/pages/OverviewPage.test.js \
  frontend/src/services/observability.js
git commit -m "feat: rebuild overview cockpit"
```

## Task 5: Turn node pages into host-level observability workstations

**Files:**
- Modify: `frontend/src/pages/NodesPage.vue`
- Modify: `frontend/src/pages/NodeDetailPage.vue`
- Modify: `frontend/src/services/observability.js`
- Create: `frontend/src/pages/NodeDetailPage.test.js`
- Test: `frontend/src/pages/NodeDetailPage.test.js`

- [ ] **Step 1: Write the failing node-detail test**

```js
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, RouterLinkStub } from "@vue/test-utils";
import NodeDetailPage from "./NodeDetailPage.vue";
import { fetchNodeDetail } from "../services/api";

vi.mock("../services/api", () => ({
  fetchNodeDetail: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ params: { id: 1 } }),
}));

describe("NodeDetailPage", () => {
  beforeEach(() => {
    fetchNodeDetail.mockReset();
  });

  it("renders tabbed observability sections", async () => {
    fetchNodeDetail.mockResolvedValue({
      id: 1,
      nodeName: "app-node",
      hostname: "app-node-host",
      ipAddress: "172.20.0.10",
      osName: "linux",
      agentVersion: "0.1.0",
      status: "ONLINE",
      statusSummary: "节点正常运行",
      lastHeartbeatAt: new Date().toISOString(),
      heartbeatTimeoutRisk: false,
      hostMetrics: null,
      highRiskServices: [],
      quickLinks: [],
      services: [],
    });

    const wrapper = mount(NodeDetailPage, {
      global: {
        stubs: { RouterLink: RouterLinkStub },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain("资源监控");
    expect(wrapper.text()).toContain("服务清单");
    expect(wrapper.text()).toContain("链路关联");
    expect(wrapper.text()).toContain("原始监控");
  });
});
```

- [ ] **Step 2: Run the node-detail test to verify it fails**

Run:

```bash
docker compose exec frontend npm test -- src/pages/NodeDetailPage.test.js
```

Expected: FAIL because the page is still a static summary page.

- [ ] **Step 3: Write the minimal node-page implementation**

```js
// frontend/src/services/observability.js
export function buildNodePanelGroups(node) {
  return {
    resources: [
      {
        title: "CPU 使用率",
        source: "Grafana",
        src: buildGrafanaPanelUrl({
          dashboardUid: "node-detail",
          panelId: 1,
          vars: { job: node.nodeName },
        }),
        fallbackHref: buildGrafanaDashboardUrl({
          dashboardUid: "node-detail",
          vars: { job: node.nodeName },
        }),
      },
      {
        title: "内存使用率",
        source: "Grafana",
        src: buildGrafanaPanelUrl({
          dashboardUid: "node-detail",
          panelId: 2,
          vars: { job: node.nodeName },
        }),
        fallbackHref: buildGrafanaDashboardUrl({
          dashboardUid: "node-detail",
          vars: { job: node.nodeName },
        }),
      },
    ],
    tracing: [
      {
        title: "SkyWalking 关联服务",
        source: "SkyWalking",
        src: buildSkyWalkingUrl("/general-service"),
        fallbackHref: buildSkyWalkingUrl("/general-service"),
      },
    ],
    raw: [
      {
        title: "Prometheus Targets",
        source: "Prometheus",
        src: buildPrometheusTargetsUrl(),
        fallbackHref: buildPrometheusTargetsUrl(),
      },
    ],
  };
}
```

```vue
<!-- frontend/src/pages/NodeDetailPage.vue -->
<template>
  <section class="page node-detail-page">
    <ContextHeader
      eyebrow="Node Detail"
      :title="node.nodeName || '节点详情'"
      :subtitle="`${node.ipAddress || '-'} · ${statusLabel}`"
      @refresh="load"
    />

    <RiskSummary
      title="节点摘要"
      :items="summaryItems"
      :services="node.highRiskServices"
      service-title="高风险服务"
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
      v-if="activeTab === 'resources'"
      title="资源监控"
      :panels="panelGroups.resources"
    />

    <section v-else-if="activeTab === 'services'" class="panel">
      <h3>服务清单</h3>
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
            <td>{{ service.serviceName }}</td>
            <td>{{ service.serviceType }}</td>
            <td>{{ service.port || "-" }}</td>
            <td>{{ service.processName || "-" }}</td>
            <td>{{ service.metricsPath || "未配置" }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <ObservabilityGrid
      v-else-if="activeTab === 'tracing'"
      title="链路关联"
      :panels="panelGroups.tracing"
    />

    <ObservabilityGrid
      v-else
      title="原始监控"
      :panels="panelGroups.raw"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchNodeDetail } from "../services/api";
import { buildNodePanelGroups } from "../services/observability";
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import RiskSummary from "../components/RiskSummary.vue";

const route = useRoute();
const activeTab = ref("resources");
const node = reactive({ services: [], highRiskServices: [] });
const tabs = [
  { key: "resources", label: "资源监控" },
  { key: "services", label: "服务清单" },
  { key: "tracing", label: "链路关联" },
  { key: "raw", label: "原始监控" },
];

const summaryItems = computed(() => [
  { label: "主机名", value: node.hostname || "-" },
  { label: "状态", value: node.statusSummary || "-" },
  { label: "最后心跳", value: node.lastHeartbeatAt || "-" },
  { label: "识别服务数", value: node.services.length },
]);

const statusLabel = computed(() => {
  if (node.status === "ONLINE") return "在线";
  if (node.status === "WARNING") return "告警";
  if (node.status === "OFFLINE") return "离线";
  return "未知";
});

const panelGroups = computed(() => buildNodePanelGroups(node));

async function load() {
  Object.assign(node, await fetchNodeDetail(route.params.id));
}

onMounted(load);
</script>
```

- [ ] **Step 4: Run the node-detail test to verify it passes**

Run:

```bash
docker compose exec frontend npm test -- src/pages/NodeDetailPage.test.js
```

Expected: PASS and the tab names are rendered.

- [ ] **Step 5: Commit the node observability slice**

```bash
git add frontend/src/pages/NodesPage.vue \
  frontend/src/pages/NodeDetailPage.vue \
  frontend/src/pages/NodeDetailPage.test.js \
  frontend/src/services/observability.js
git commit -m "feat: add node observability views"
```

## Task 6: Add service detail, tracing, and debug workspaces

**Files:**
- Modify: `frontend/src/pages/ServicesPage.vue`
- Create: `frontend/src/pages/ServiceDetailPage.vue`
- Create: `frontend/src/pages/ServiceDetailPage.test.js`
- Create: `frontend/src/pages/TracingPage.vue`
- Create: `frontend/src/pages/DebugPage.vue`
- Modify: `frontend/src/services/observability.js`
- Test: `frontend/src/pages/ServiceDetailPage.test.js`
- Test: `frontend/src/pages/ServicesPage.test.js`

- [ ] **Step 1: Write the failing service-page tests**

```js
// frontend/src/pages/ServiceDetailPage.test.js
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, RouterLinkStub } from "@vue/test-utils";
import ServiceDetailPage from "./ServiceDetailPage.vue";
import { fetchServiceDetail } from "../services/api";

vi.mock("../services/api", () => ({
  fetchServiceDetail: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ params: { id: 7 } }),
}));

describe("ServiceDetailPage", () => {
  beforeEach(() => {
    fetchServiceDetail.mockReset();
  });

  it("shows the metrics warning in the first screen and renders grouped tabs", async () => {
    fetchServiceDetail.mockResolvedValue({
      id: 7,
      serviceName: "sample-service",
      serviceType: "SPRING_BOOT",
      port: 8081,
      processName: "java",
      metricsPath: "",
      nodeId: 1,
      nodeName: "app-node",
      nodeIpAddress: "172.20.0.10",
      nodeStatus: "ONLINE",
      metricsMissing: true,
      quickLinks: [],
    });

    const wrapper = mount(ServiceDetailPage, {
      global: {
        stubs: { RouterLink: RouterLinkStub },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain("metricsPath");
    expect(wrapper.text()).toContain("运行指标");
    expect(wrapper.text()).toContain("调用链");
    expect(wrapper.text()).toContain("Prometheus 调试");
  });
});

// frontend/src/pages/ServicesPage.test.js
it("links each service row to its detail page", async () => {
  fetchServices.mockResolvedValue([
    {
      id: 1,
      serviceName: "nginx",
      serviceType: "NGINX",
      nodeId: 42,
      nodeName: "app-node",
      port: 80,
      metricsPath: " ",
    },
  ]);

  const wrapper = mount(ServicesPage, {
    global: { stubs: { RouterLink: RouterLinkStub } },
  });

  await flushPromises();

  expect(
    wrapper.findAllComponents(RouterLinkStub).some((link) => link.props("to") === "/services/1")
  ).toBe(true);
});
```

- [ ] **Step 2: Run the service-page tests to verify they fail**

Run:

```bash
docker compose exec frontend npm test -- src/pages/ServicesPage.test.js src/pages/ServiceDetailPage.test.js
```

Expected: FAIL because the service detail page does not exist yet and the service list has no detail route.

- [ ] **Step 3: Write the minimal service/tracing/debug implementation**

```js
// frontend/src/services/observability.js
export function buildServicePanelGroups(service) {
  return {
    runtime: [
      {
        title: "运行指标",
        source: "Grafana",
        src: buildGrafanaPanelUrl({
          dashboardUid: "service-detail",
          panelId: 1,
          vars: { service: service.serviceName },
        }),
        fallbackHref: buildGrafanaDashboardUrl({
          dashboardUid: "service-detail",
          vars: { service: service.serviceName },
        }),
      },
    ],
    tracing: [
      {
        title: "SkyWalking 服务视图",
        source: "SkyWalking",
        src: buildSkyWalkingUrl("/general-service"),
        fallbackHref: buildSkyWalkingUrl("/general-service"),
      },
    ],
    debug: [
      {
        title: "Prometheus Graph",
        source: "Prometheus",
        src: buildPrometheusGraphUrl(`up{job="${service.serviceName}"}`),
        fallbackHref: buildPrometheusGraphUrl(`up{job="${service.serviceName}"}`),
      },
    ],
  };
}
```

```vue
<!-- frontend/src/pages/ServiceDetailPage.vue -->
<template>
  <section class="page service-detail-page">
    <ContextHeader
      eyebrow="Service Detail"
      :title="service.serviceName || '服务详情'"
      :subtitle="`${service.serviceType || '-'} · ${service.nodeName || '-'}`"
      @refresh="load"
    />

    <div v-if="service.metricsMissing" class="warning-banner">
      当前服务未配置 metricsPath，Grafana / Prometheus 可能只能展示有限内容。
    </div>

    <RiskSummary
      title="服务摘要"
      :items="summaryItems"
      :services="[]"
      :nodes="[]"
    />

    <div class="tab-strip">
      <button v-for="tab in tabs" :key="tab.key" class="tab-button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
        {{ tab.label }}
      </button>
    </div>

    <ObservabilityGrid v-if="activeTab === 'runtime'" title="运行指标" :panels="panelGroups.runtime" />
    <ObservabilityGrid v-else-if="activeTab === 'tracing'" title="调用链" :panels="panelGroups.tracing" />
    <section v-else-if="activeTab === 'base'" class="panel">
      <h3>基础信息</h3>
      <dl class="key-value-list">
        <div>
          <dt>服务类型</dt>
          <dd>{{ service.serviceType || "-" }}</dd>
        </div>
        <div>
          <dt>所属节点</dt>
          <dd>{{ service.nodeName || "-" }}</dd>
        </div>
        <div>
          <dt>节点 IP</dt>
          <dd>{{ service.nodeIpAddress || "-" }}</dd>
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
    </section>
    <ObservabilityGrid v-else title="Prometheus 调试" :panels="panelGroups.debug" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchServiceDetail } from "../services/api";
import { buildServicePanelGroups } from "../services/observability";
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import RiskSummary from "../components/RiskSummary.vue";

const route = useRoute();
const activeTab = ref("runtime");
const service = reactive({});
const tabs = [
  { key: "runtime", label: "运行指标" },
  { key: "tracing", label: "调用链" },
  { key: "base", label: "基础信息" },
  { key: "debug", label: "Prometheus 调试" },
];

const summaryItems = computed(() => [
  { label: "所属节点", value: service.nodeName || "-" },
  { label: "节点状态", value: service.nodeStatus || "-" },
  { label: "端口", value: service.port || "-" },
  { label: "进程", value: service.processName || "-" },
  { label: "metricsPath", value: service.metricsPath || "未配置" },
]);

const panelGroups = computed(() => buildServicePanelGroups(service));

async function load() {
  Object.assign(service, await fetchServiceDetail(route.params.id));
}

onMounted(load);
</script>
```

```vue
<!-- frontend/src/pages/TracingPage.vue -->
<template>
  <section class="page">
    <ContextHeader
      eyebrow="Tracing"
      title="链路观测"
      subtitle="SkyWalking 作为一线链路入口，前端负责承接上下文。"
    />
    <ObservabilityGrid title="SkyWalking 工作区" :panels="panels" />
  </section>
</template>

<script setup>
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import { buildSkyWalkingUrl } from "../services/observability";

const panels = [
  {
    title: "SkyWalking 总览",
    source: "SkyWalking",
    src: buildSkyWalkingUrl("/general-service"),
    fallbackHref: buildSkyWalkingUrl("/general-service"),
  },
];
</script>
```

```vue
<!-- frontend/src/pages/DebugPage.vue -->
<template>
  <section class="page">
    <ContextHeader
      eyebrow="Debug"
      title="监控调试"
      subtitle="Prometheus 原始页面只负责验证，不抢总览的中心位置。"
    />
    <ObservabilityGrid title="Prometheus 调试面板" :panels="panels" />
  </section>
</template>

<script setup>
import ContextHeader from "../components/ContextHeader.vue";
import ObservabilityGrid from "../components/ObservabilityGrid.vue";
import { buildPrometheusGraphUrl, buildPrometheusTargetsUrl } from "../services/observability";

const panels = [
  {
    title: "Targets",
    source: "Prometheus",
    src: buildPrometheusTargetsUrl(),
    fallbackHref: buildPrometheusTargetsUrl(),
  },
  {
    title: "up 指标调试",
    source: "Prometheus",
    src: buildPrometheusGraphUrl("up"),
    fallbackHref: buildPrometheusGraphUrl("up"),
  },
];
</script>
```

- [ ] **Step 4: Run the service-page tests to verify they pass**

Run:

```bash
docker compose exec frontend npm test -- src/pages/ServicesPage.test.js src/pages/ServiceDetailPage.test.js
```

Expected: PASS with both the list-detail link and detail warning covered.

- [ ] **Step 5: Commit the service/tracing/debug workspace**

```bash
git add frontend/src/pages/ServicesPage.vue \
  frontend/src/pages/ServicesPage.test.js \
  frontend/src/pages/ServiceDetailPage.vue \
  frontend/src/pages/ServiceDetailPage.test.js \
  frontend/src/pages/TracingPage.vue \
  frontend/src/pages/DebugPage.vue \
  frontend/src/services/observability.js
git commit -m "feat: add service tracing and debug pages"
```

## Task 7: Enable embedding runtime config and provision Grafana dashboards

**Files:**
- Modify: `docker-compose.yml:23-176`
- Create: `infra/grafana/dashboards/platform-overview.json`
- Create: `infra/grafana/dashboards/node-detail.json`
- Create: `infra/grafana/dashboards/service-detail.json`
- Modify: `infra/grafana/dashboards/monitoring-overview.json`
- Test: `docker-compose.yml`
- Test: `infra/grafana/dashboards/*.json`

- [ ] **Step 1: Write the failing runtime/dashboard checks**

Run:

```bash
docker compose config > /tmp/monitoring-compose.rendered.yml
rg -n "GF_SECURITY_ALLOW_EMBEDDING|GF_AUTH_ANONYMOUS_ENABLED|--web.external-url|--web.route-prefix" /tmp/monitoring-compose.rendered.yml
test -f infra/grafana/dashboards/platform-overview.json
test -f infra/grafana/dashboards/node-detail.json
test -f infra/grafana/dashboards/service-detail.json
```

Expected: FAIL because the compose render does not include the embed/sub-path flags and the new dashboard files do not exist.

- [ ] **Step 2: Capture the minimal runtime/dashboard implementation**

```yaml
# docker-compose.yml
backend:
  environment:
    MONITORING_GRAFANA_BASE_URL: ${PUBLIC_SCHEME:-http}://${PUBLIC_HOST:-localhost}:${FRONTEND_PORT:-15173}/grafana
    MONITORING_PROMETHEUS_BASE_URL: ${PUBLIC_SCHEME:-http}://${PUBLIC_HOST:-localhost}:${FRONTEND_PORT:-15173}/prometheus
    MONITORING_SKYWALKING_BASE_URL: ${PUBLIC_SCHEME:-http}://${PUBLIC_HOST:-localhost}:${SKYWALKING_UI_PORT:-18082}

frontend:
  environment:
    VITE_GRAFANA_BASE_URL: /grafana
    VITE_PROMETHEUS_BASE_URL: /prometheus
    VITE_SKYWALKING_BASE_URL: ${PUBLIC_SCHEME:-http}://${PUBLIC_HOST:-localhost}:${SKYWALKING_UI_PORT:-18082}
    VITE_GRAFANA_PROXY_TARGET: http://grafana:3000
    VITE_PROMETHEUS_PROXY_TARGET: http://prometheus:9090

prometheus:
  command:
    - --config.file=/etc/prometheus/prometheus.yml
    - --web.enable-lifecycle
    - --web.external-url=${PUBLIC_SCHEME:-http}://${PUBLIC_HOST:-localhost}:${FRONTEND_PORT:-15173}/prometheus
    - --web.route-prefix=/prometheus

grafana:
  environment:
    GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-monitoring-admin}
    GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-monitoring-change-me}
    GF_USERS_ALLOW_SIGN_UP: "false"
    GF_SECURITY_ALLOW_EMBEDDING: "true"
    GF_AUTH_ANONYMOUS_ENABLED: "true"
    GF_AUTH_ANONYMOUS_ORG_ROLE: Viewer
    GF_SERVER_ROOT_URL: ${PUBLIC_SCHEME:-http}://${PUBLIC_HOST:-localhost}:${FRONTEND_PORT:-15173}/grafana
    GF_SERVER_SERVE_FROM_SUB_PATH: "true"
```

```json
// infra/grafana/dashboards/platform-overview.json
{
  "title": "Platform Overview",
  "uid": "platform-overview",
  "schemaVersion": 39,
  "refresh": "15s",
  "time": { "from": "now-6h", "to": "now" },
  "panels": [
    { "id": 1, "title": "Targets Up", "type": "timeseries", "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 }, "datasource": { "type": "prometheus", "uid": "prometheus" }, "targets": [{ "refId": "A", "expr": "sum(up)" }] },
    { "id": 2, "title": "Node CPU Rate", "type": "timeseries", "gridPos": { "x": 12, "y": 0, "w": 12, "h": 8 }, "datasource": { "type": "prometheus", "uid": "prometheus" }, "targets": [{ "refId": "A", "expr": "sum(rate(process_cpu_seconds_total[5m]))" }] },
    { "id": 3, "title": "Sample Service Uptime", "type": "timeseries", "gridPos": { "x": 0, "y": 8, "w": 12, "h": 8 }, "datasource": { "type": "prometheus", "uid": "prometheus" }, "targets": [{ "refId": "A", "expr": "process_uptime_seconds{job=\"sample-service\"}" }] }
  ]
}
```

```json
// infra/grafana/dashboards/node-detail.json
{
  "title": "Node Detail",
  "uid": "node-detail",
  "schemaVersion": 39,
  "refresh": "15s",
  "templating": {
    "list": [
      {
        "name": "job",
        "type": "query",
        "datasource": { "type": "prometheus", "uid": "prometheus" },
        "definition": "label_values(up, job)",
        "query": { "query": "label_values(up, job)" }
      }
    ]
  },
  "panels": [
    { "id": 1, "title": "CPU Rate", "type": "timeseries", "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 }, "datasource": { "type": "prometheus", "uid": "prometheus" }, "targets": [{ "refId": "A", "expr": "rate(process_cpu_seconds_total{job=~\"$job\"}[5m])" }] },
    { "id": 2, "title": "Memory", "type": "timeseries", "gridPos": { "x": 12, "y": 0, "w": 12, "h": 8 }, "datasource": { "type": "prometheus", "uid": "prometheus" }, "targets": [{ "refId": "A", "expr": "process_resident_memory_bytes{job=~\"$job\"}" }] }
  ]
}
```

```json
// infra/grafana/dashboards/service-detail.json
{
  "title": "Service Detail",
  "uid": "service-detail",
  "schemaVersion": 39,
  "refresh": "15s",
  "templating": {
    "list": [
      {
        "name": "service",
        "type": "query",
        "datasource": { "type": "prometheus", "uid": "prometheus" },
        "definition": "label_values(up, job)",
        "query": { "query": "label_values(up, job)" }
      }
    ]
  },
  "panels": [
    { "id": 1, "title": "Process Uptime", "type": "timeseries", "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 }, "datasource": { "type": "prometheus", "uid": "prometheus" }, "targets": [{ "refId": "A", "expr": "process_uptime_seconds{job=~\"$service\"}" }] },
    { "id": 2, "title": "CPU Rate", "type": "timeseries", "gridPos": { "x": 12, "y": 0, "w": 12, "h": 8 }, "datasource": { "type": "prometheus", "uid": "prometheus" }, "targets": [{ "refId": "A", "expr": "rate(process_cpu_seconds_total{job=~\"$service\"}[5m])" }] }
  ]
}
```

- [ ] **Step 3: Apply the runtime/dashboard changes**

Run:

```bash
rg -n "platform-overview|node-detail|service-detail" infra/grafana/dashboards
docker compose config > /tmp/monitoring-compose.rendered.yml
```

Expected: The grep shows all new dashboard UIDs and the rendered compose file contains the embed/sub-path settings.

- [ ] **Step 4: Re-run the runtime/dashboard checks**

Run:

```bash
docker compose config > /tmp/monitoring-compose.rendered.yml
rg -n "GF_SECURITY_ALLOW_EMBEDDING|GF_AUTH_ANONYMOUS_ENABLED|--web.external-url|--web.route-prefix" /tmp/monitoring-compose.rendered.yml
test -f infra/grafana/dashboards/platform-overview.json
test -f infra/grafana/dashboards/node-detail.json
test -f infra/grafana/dashboards/service-detail.json
```

Expected: PASS with exit code `0`.

- [ ] **Step 5: Commit the observability runtime slice**

```bash
git add docker-compose.yml \
  infra/grafana/dashboards/monitoring-overview.json \
  infra/grafana/dashboards/platform-overview.json \
  infra/grafana/dashboards/node-detail.json \
  infra/grafana/dashboards/service-detail.json
git commit -m "feat: enable embedded observability dashboards"
```

## Task 8: Verify the full stack and ship the feature branch cleanly

**Files:**
- Modify: any touched files from Tasks 1-7 as needed
- Test: backend, frontend, build, and smoke commands

- [ ] **Step 1: Bring up the full observability stack**

Run:

```bash
DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0 docker compose --profile observability --profile nodes up -d --build
```

Expected: `mysql`, `backend`, `frontend`, `prometheus`, `grafana`, `skywalking-oap`, `skywalking-ui`, `app-node`, and `middleware-node` end up healthy or running.

- [ ] **Step 2: Run the backend and frontend automated suites**

Run:

```bash
docker compose exec backend sh /workspace/infra/maven/mvn-with-mirror.sh -q test
docker compose exec frontend npm test
docker compose exec frontend npm run build
```

Expected: backend tests pass, Vitest is green, and Vite build succeeds.

- [ ] **Step 3: Run the smoke test against the full stack**

Run:

```bash
bash tests/smoke-test.sh
```

Expected: smoke script exits `0`.

- [ ] **Step 4: Perform the manual desktop verification checklist**

Check:

```text
1. /overview shows local summaries plus multiple Grafana, Prometheus, and SkyWalking panels.
2. /nodes/:id shows the tabbed host workstation and real embedded resource views.
3. /services/:id shows runtime, tracing, base info, and Prometheus debug tabs.
4. /tracing and /debug are reachable from the sidebar.
5. At least one iframe failure can be simulated by stopping Grafana or Prometheus and confirming the local fallback state remains visible.
```

Expected: all five checks pass before claiming completion.

- [ ] **Step 5: Create the final integration commit**

```bash
git status --short
git add backend frontend infra docker-compose.yml
git commit -m "feat: deliver embedded observability workspace"
```

## Self-Review

### Spec coverage

- Top-level navigation plus `/services/:id`: Tasks 3 and 6.
- Overview as dense cockpit with grouped panels: Task 4.
- Node detail as a tabbed observability station: Task 5.
- Service detail, tracing route, and debug route: Task 6.
- Minimal backend `GET /api/services/{id}`: Task 1.
- Grafana/Prometheus embedding strategy and runtime config: Tasks 2 and 7.
- Failure states and fallback links: Task 3.
- Desktop-only verification with full stack: Task 8.

### Placeholder scan

- No `TODO`, `TBD`, or “similar to above” placeholders remain.
- Every task contains exact file paths, code snippets, and commands.

### Type consistency

- Backend response names are consistent: `ServiceDetailResponse`, `fetchServiceDetail`, `buildServicePanelGroups`.
- Dashboard UIDs are consistent across backend links, frontend helpers, and Grafana provisioning: `platform-overview`, `node-detail`, `service-detail`.
