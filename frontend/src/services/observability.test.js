import { describe, expect, it } from "vitest";
import {
  buildOverviewPanelGroups,
  buildGrafanaDashboardUrl,
  buildGrafanaPanelUrl,
  buildPrometheusGraphUrl,
  buildPrometheusTargetsUrl,
  buildTracingPanels,
  buildSkyWalkingUrl,
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

    expect(url).toContain("/grafana/d-solo/node-detail/node-detail?");
    expect(url).toContain("panelId=4");
    expect(url).not.toContain("viewPanel=");
    expect(url).toContain("var-job=app-node");
  });

  it("builds Grafana dashboard urls while omitting empty vars and encoding reserved characters", () => {
    const url = buildGrafanaDashboardUrl(
      {
        dashboardUid: "service-detail",
        vars: {
          service: "orders/api?region=cn north",
          empty: "",
          missing: null,
        },
      },
      {
        VITE_GRAFANA_BASE_URL: "/grafana",
      }
    );

    const params = new URL(url, "http://localhost").searchParams;

    expect(url).toContain("/grafana/d/service-detail/service-detail?");
    expect(params.get("var-service")).toBe("orders/api?region=cn north");
    expect(url).toContain("var-service=orders%2Fapi%3Fregion%3Dcn+north");
    expect(params.has("var-empty")).toBe(false);
    expect(params.has("var-missing")).toBe(false);
  });

  it("builds Prometheus graph urls on the proxied sub-path", () => {
    const expr =
      'sum(rate(http_server_requests_seconds_count{job="app-node",uri="/orders?id=7"}[5m]))';
    const url = buildPrometheusGraphUrl(expr, {
      VITE_PROMETHEUS_BASE_URL: "/prometheus",
    });
    const params = new URL(url, "http://localhost").searchParams;

    expect(url).toContain("/prometheus/graph?");
    expect(params.get("g0.expr")).toBe(expr);
    expect(url).toContain(
      "g0.expr=sum%28rate%28http_server_requests_seconds_count%7Bjob%3D%22app-node%22%2Curi%3D%22%2Forders%3Fid%3D7%22%7D%5B5m%5D%29%29"
    );
  });

  it("builds the proxied Prometheus targets url", () => {
    expect(
      buildPrometheusTargetsUrl({
        VITE_PROMETHEUS_BASE_URL: "/prometheus",
      })
    ).toBe("/prometheus/targets");
  });

  it("builds normalized SkyWalking urls from the configured base url", () => {
    expect(
      buildSkyWalkingUrl("General-Service/Services", {
        VITE_SKYWALKING_BASE_URL: "http://skywalking.example:18082/",
      })
    ).toBe("http://skywalking.example:18082/General-Service/Services");
  });

  it("defaults SkyWalking to its direct absolute origin", () => {
    expect(resolveObservabilityConfig({}).skywalkingBaseUrl).toBe(
      "http://localhost:18082"
    );
  });

  it("marks the overview tracing group as full width", () => {
    const tracingGroup = buildOverviewPanelGroups({}).find(
      (group) => group.key === "tracing"
    );

    expect(tracingGroup.columns).toBe(1);
    expect(tracingGroup.panels).toHaveLength(1);
  });

  it("builds tracing panels with a trace-oriented title", () => {
    const panels = buildTracingPanels({});

    expect(panels).toHaveLength(1);
    expect(panels[0].title).toBe("SkyWalking Trace 工作台");
  });
});
