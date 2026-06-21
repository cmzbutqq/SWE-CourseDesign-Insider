export function resolveObservabilityConfig(env = import.meta.env) {
  return {
    grafanaBaseUrl: env.VITE_GRAFANA_BASE_URL || "/grafana",
    prometheusBaseUrl: env.VITE_PROMETHEUS_BASE_URL || "/prometheus",
    skywalkingBaseUrl: env.VITE_SKYWALKING_BASE_URL || "/skywalking",
    grafanaFrom: env.VITE_GRAFANA_FROM || "now-6h",
    grafanaTo: env.VITE_GRAFANA_TO || "now",
  };
}

function appendVars(params, vars) {
  Object.entries(vars).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.append(`var-${key}`, String(value));
    }
  });
}

export function buildGrafanaPanelUrl(
  { dashboardUid, dashboardSlug = dashboardUid, panelId, vars = {}, from, to },
  env = import.meta.env
) {
  const config = resolveObservabilityConfig(env);
  const params = new URLSearchParams({
    orgId: "1",
    theme: "light",
    panelId: String(panelId),
    from: from || config.grafanaFrom,
    to: to || config.grafanaTo,
  });

  appendVars(params, vars);

  return `${config.grafanaBaseUrl}/d-solo/${dashboardUid}/${dashboardSlug}?${params.toString()}`;
}

export function buildGrafanaDashboardUrl(
  { dashboardUid, dashboardSlug = dashboardUid, vars = {} },
  env = import.meta.env
) {
  const config = resolveObservabilityConfig(env);
  const params = new URLSearchParams();

  appendVars(params, vars);

  return `${config.grafanaBaseUrl}/d/${dashboardUid}/${dashboardSlug}${
    params.toString() ? `?${params.toString()}` : ""
  }`;
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
  const baseUrl = resolveObservabilityConfig(env).skywalkingBaseUrl.replace(
    /\/$/,
    ""
  );
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;

  return `${baseUrl}${normalizedPath}`;
}

const SKYWALKING_SERVICE_OVERVIEW_PATH = "/General-Service/Services";

function buildServiceInstanceLabel(service) {
  const metricsPort = service?.metricsPort;
  if (!service?.nodeName || !metricsPort) {
    return null;
  }
  return `${service.nodeName}:${metricsPort}`;
}

function withFallback(source, src, fallbackHref, extra = {}) {
  return {
    source,
    src,
    fallbackHref,
    ...extra,
  };
}

const SKYWALKING_PANEL_PROPS = {
  height: 600,
  frameScale: 0.82,
};

const PROMETHEUS_TARGET_PANEL_PROPS = {
  height: 430,
  frameScale: 0.86,
};

const PROMETHEUS_GRAPH_PANEL_PROPS = {
  height: 430,
  frameScale: 0.88,
};

export function buildOverviewPanelGroups(env = import.meta.env) {
  const overviewDashboard = { dashboardUid: "platform-overview" };
  const overviewFallback = buildGrafanaDashboardUrl(overviewDashboard, env);

  return [
    {
      key: "infrastructure",
      title: "基础资源",
      description: "Grafana 节点视角与主机资源速览",
      panels: [
        withFallback(
          "Grafana",
          buildGrafanaPanelUrl({ ...overviewDashboard, panelId: 1 }, env),
          overviewFallback,
          { title: "节点在线情况" }
        ),
        withFallback(
          "Grafana",
          buildGrafanaPanelUrl({ ...overviewDashboard, panelId: 2 }, env),
          overviewFallback,
          { title: "CPU Rate by Job" }
        ),
        withFallback(
          "Grafana",
          buildGrafanaPanelUrl({ ...overviewDashboard, panelId: 3 }, env),
          overviewFallback,
          { title: "Resident Memory by Job" }
        ),
      ],
    },
    {
      key: "applications",
      title: "应用健康",
      description: "服务可用性与 Prometheus 原始验证入口",
      panels: [
        withFallback(
          "Grafana",
          buildGrafanaPanelUrl({ ...overviewDashboard, panelId: 4 }, env),
          overviewFallback,
          { title: "Service Uptime" }
        ),
        withFallback(
          "Prometheus",
          buildPrometheusTargetsUrl(env),
          buildPrometheusTargetsUrl(env),
          {
            title: "Prometheus Targets",
            ...PROMETHEUS_TARGET_PANEL_PROPS,
          }
        ),
        withFallback(
          "Prometheus",
          buildPrometheusGraphUrl("up", env),
          buildPrometheusGraphUrl("up", env),
          {
            title: "Prometheus Up Graph",
            ...PROMETHEUS_GRAPH_PANEL_PROPS,
          }
        ),
      ],
    },
    {
      key: "tracing",
      title: "链路观测",
      description: "SkyWalking 作为调用链入口",
      columns: 1,
      panels: [
        withFallback(
          "SkyWalking",
          buildSkyWalkingUrl(SKYWALKING_SERVICE_OVERVIEW_PATH, env),
          buildSkyWalkingUrl(SKYWALKING_SERVICE_OVERVIEW_PATH, env),
          {
            title: "SkyWalking 服务视图",
            ...SKYWALKING_PANEL_PROPS,
          }
        ),
      ],
    },
  ];
}

export function buildNodePanelGroups(node, env = import.meta.env) {
  const vars = { job: node?.nodeName || "" };
  const dashboard = { dashboardUid: "node-detail", vars };
  const fallbackHref = buildGrafanaDashboardUrl(dashboard, env);

  return {
    resources: [
      withFallback(
        "Grafana",
        buildGrafanaPanelUrl({ ...dashboard, panelId: 1 }, env),
        fallbackHref,
        { title: "Node Availability" }
      ),
      withFallback(
        "Grafana",
        buildGrafanaPanelUrl({ ...dashboard, panelId: 2 }, env),
        fallbackHref,
        { title: "CPU Rate" }
      ),
      withFallback(
        "Grafana",
        buildGrafanaPanelUrl({ ...dashboard, panelId: 3 }, env),
        fallbackHref,
        { title: "Resident Memory" }
      ),
      withFallback(
        "Grafana",
        buildGrafanaPanelUrl({ ...dashboard, panelId: 4 }, env),
        fallbackHref,
        { title: "Scrape Duration" }
      ),
    ],
    tracing: [
      withFallback(
        "SkyWalking",
        buildSkyWalkingUrl(SKYWALKING_SERVICE_OVERVIEW_PATH, env),
        buildSkyWalkingUrl(SKYWALKING_SERVICE_OVERVIEW_PATH, env),
        {
          title: "SkyWalking 服务视图",
          ...SKYWALKING_PANEL_PROPS,
        }
      ),
    ],
    raw: [
      withFallback(
        "Prometheus",
        buildPrometheusTargetsUrl(env),
        buildPrometheusTargetsUrl(env),
        {
          title: "Prometheus Targets",
          ...PROMETHEUS_TARGET_PANEL_PROPS,
        }
      ),
      withFallback(
        "Prometheus",
        buildPrometheusGraphUrl(`up{job="${node?.nodeName || ""}"}`, env),
        buildPrometheusGraphUrl(`up{job="${node?.nodeName || ""}"}`, env),
        {
          title: "Node Up Query",
          ...PROMETHEUS_GRAPH_PANEL_PROPS,
        }
      ),
    ],
  };
}

export function buildServicePanelGroups(service, env = import.meta.env) {
  const instance = buildServiceInstanceLabel(service);
  const vars = {
    service: service?.serviceName || "",
    instance,
  };
  const dashboard = { dashboardUid: "service-detail", vars };
  const fallbackHref = buildGrafanaDashboardUrl(dashboard, env);
  const selector = instance
    ? `job="${service?.serviceName || ""}",instance="${instance}"`
    : `job="${service?.serviceName || ""}"`;

  const runtimePanelsByType = {
    SPRING_BOOT: [
      withFallback(
        "Grafana",
        buildGrafanaPanelUrl({ ...dashboard, panelId: 1 }, env),
        fallbackHref,
        { title: "Service Availability" }
      ),
      withFallback(
        "Grafana",
        buildGrafanaPanelUrl({ ...dashboard, panelId: 2 }, env),
        fallbackHref,
        { title: "Process Uptime" }
      ),
      withFallback(
        "Grafana",
        buildGrafanaPanelUrl({ ...dashboard, panelId: 3 }, env),
        fallbackHref,
        { title: "CPU Rate" }
      ),
      withFallback(
        "Grafana",
        buildGrafanaPanelUrl({ ...dashboard, panelId: 4 }, env),
        fallbackHref,
        { title: "Resident Memory" }
      ),
    ],
    NGINX: [
      withFallback(
        "Prometheus",
        buildPrometheusGraphUrl(`nginx_connections_active{${selector}}`, env),
        buildPrometheusGraphUrl(`nginx_connections_active{${selector}}`, env),
        {
          title: "Nginx Active Connections",
          ...PROMETHEUS_GRAPH_PANEL_PROPS,
        }
      ),
      withFallback(
        "Prometheus",
        buildPrometheusGraphUrl(`rate(nginx_http_requests_total{${selector}}[5m])`, env),
        buildPrometheusGraphUrl(`rate(nginx_http_requests_total{${selector}}[5m])`, env),
        {
          title: "Nginx Request Rate",
          ...PROMETHEUS_GRAPH_PANEL_PROPS,
        }
      ),
    ],
    REDIS: [
      withFallback(
        "Prometheus",
        buildPrometheusGraphUrl(`redis_connected_clients{${selector}}`, env),
        buildPrometheusGraphUrl(`redis_connected_clients{${selector}}`, env),
        {
          title: "Redis Connected Clients",
          ...PROMETHEUS_GRAPH_PANEL_PROPS,
        }
      ),
      withFallback(
        "Prometheus",
        buildPrometheusGraphUrl(`rate(redis_commands_processed_total{${selector}}[5m])`, env),
        buildPrometheusGraphUrl(`rate(redis_commands_processed_total{${selector}}[5m])`, env),
        {
          title: "Redis Commands Rate",
          ...PROMETHEUS_GRAPH_PANEL_PROPS,
        }
      ),
    ],
  };

  return {
    runtime: [
      ...(runtimePanelsByType[service?.serviceType] || runtimePanelsByType.SPRING_BOOT),
    ],
    tracing: [
      withFallback(
        "SkyWalking",
        buildSkyWalkingUrl(SKYWALKING_SERVICE_OVERVIEW_PATH, env),
        buildSkyWalkingUrl(SKYWALKING_SERVICE_OVERVIEW_PATH, env),
        {
          title: "SkyWalking 服务视图",
          ...SKYWALKING_PANEL_PROPS,
        }
      ),
    ],
    debug: [
      withFallback(
        "Prometheus",
        buildPrometheusGraphUrl(`up{${selector}}`, env),
        buildPrometheusGraphUrl(`up{${selector}}`, env),
        {
          title: "Availability Query",
          ...PROMETHEUS_GRAPH_PANEL_PROPS,
        }
      ),
      withFallback(
        "Prometheus",
        buildPrometheusGraphUrl(
          `process_resident_memory_bytes{${selector}}`,
          env
        ),
        buildPrometheusGraphUrl(
          `process_resident_memory_bytes{${selector}}`,
          env
        ),
        {
          title: "Resident Memory Query",
          ...PROMETHEUS_GRAPH_PANEL_PROPS,
        }
      ),
    ],
  };
}

export function buildTracingPanels(env = import.meta.env) {
  return [
    withFallback(
      "SkyWalking",
      buildSkyWalkingUrl(SKYWALKING_SERVICE_OVERVIEW_PATH, env),
      buildSkyWalkingUrl(SKYWALKING_SERVICE_OVERVIEW_PATH, env),
      {
        title: "SkyWalking Trace 工作台",
        ...SKYWALKING_PANEL_PROPS,
      }
    ),
  ];
}

export function buildDebugPanels(env = import.meta.env) {
  return [
    withFallback(
      "Prometheus",
      buildPrometheusTargetsUrl(env),
      buildPrometheusTargetsUrl(env),
      {
        title: "Targets",
        ...PROMETHEUS_TARGET_PANEL_PROPS,
      }
    ),
    withFallback(
      "Prometheus",
      buildPrometheusGraphUrl("up", env),
      buildPrometheusGraphUrl("up", env),
      {
        title: "up 指标调试",
        ...PROMETHEUS_GRAPH_PANEL_PROPS,
      }
    ),
  ];
}
