import { describe, expect, it } from "vitest";
import config, {
  createProxyConfig,
  resolveProxyTargets,
} from "./vite.config.js";

describe("vite config", () => {
  it("resolves default proxy targets without ambient env overrides", () => {
    expect(resolveProxyTargets({})).toEqual({
      api: "http://backend:8080",
      grafana: "http://grafana:3000",
      prometheus: "http://prometheus:9090",
      skywalking: "http://skywalking-ui:8080",
    });
  });

  it("resolves proxy targets from explicit env overrides", () => {
    expect(
      resolveProxyTargets({
        VITE_PROXY_TARGET: "http://backend.example:18081",
        VITE_GRAFANA_PROXY_TARGET: "http://grafana.example:13000",
        VITE_PROMETHEUS_PROXY_TARGET: "http://prometheus.example:19090",
        VITE_SKYWALKING_PROXY_TARGET: "http://skywalking.example:18082",
      })
    ).toEqual({
      api: "http://backend.example:18081",
      grafana: "http://grafana.example:13000",
      prometheus: "http://prometheus.example:19090",
      skywalking: "http://skywalking.example:18082",
    });
  });

  it("creates proxy entries for backend and observability requests", () => {
    expect(
      createProxyConfig({
        VITE_PROXY_TARGET: "http://backend.example:18081",
        VITE_GRAFANA_PROXY_TARGET: "http://grafana.example:13000",
        VITE_PROMETHEUS_PROXY_TARGET: "http://prometheus.example:19090",
        VITE_SKYWALKING_PROXY_TARGET: "http://skywalking.example:18082",
      })
    ).toEqual({
      "/api": {
        target: "http://backend.example:18081",
        changeOrigin: true,
      },
      "/grafana": {
        target: "http://grafana.example:13000",
        changeOrigin: true,
      },
      "/prometheus": {
        target: "http://prometheus.example:19090",
        changeOrigin: true,
      },
      "/skywalking": {
        target: "http://skywalking.example:18082",
        changeOrigin: true,
      },
    });
  });

  it("wires the default export to the proxy helper", () => {
    expect(config.server.proxy).toEqual(createProxyConfig(process.env));
  });
});
