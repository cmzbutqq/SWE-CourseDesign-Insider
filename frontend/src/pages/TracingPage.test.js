import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import TracingPage from "./TracingPage.vue";
import {
  fetchOverview,
  fetchServices,
  fetchTracingSummary,
} from "../services/api";

vi.mock("../services/api", () => ({
  fetchOverview: vi.fn(),
  fetchServices: vi.fn(),
  fetchTracingSummary: vi.fn(),
}));

describe("TracingPage", () => {
  beforeEach(() => {
    fetchOverview.mockReset();
    fetchServices.mockReset();
    fetchTracingSummary.mockReset();
  });

  it("renders local tracing summaries before the embedded SkyWalking workspace", async () => {
    fetchOverview.mockResolvedValue({
      nodes: { total: 2, online: 2, offline: 0, warning: 0 },
      services: { total: 6, healthy: 3, abnormal: 3 },
      unresolvedAlerts: 3,
      anomalies: { nodes: [], services: [{ id: 9, serviceName: "mysql" }] },
      quickLinks: [],
    });
    fetchServices.mockResolvedValue([
      {
        id: 7,
        serviceName: "sample-service",
        serviceType: "SPRING_BOOT",
        port: 8081,
        processName: "java",
        metricsPath: "/actuator/prometheus",
        nodeId: 1,
        nodeName: "app-node",
      },
    ]);
    fetchTracingSummary.mockResolvedValue({
      serviceNames: ["sample-service", "middleware-service"],
      traces: [
        {
          traceId: "trace-1",
          endpoints: ["GET:/api/demo-chain", "GET:/api/middleware/profile"],
          durationMs: 128,
          startTime: "2026-06-21 11:00",
          error: false,
        },
      ],
      latestTrace: {
        traceId: "trace-1",
        entryService: "sample-service",
        entryEndpoint: "GET:/api/demo-chain",
        serviceChain: ["sample-service", "middleware-service"],
        dependencyChain: ["mysql", "redis", "nginx"],
        spanCount: 8,
        durationMs: 128,
        startTime: "2026-06-21 11:00",
        error: false,
      },
    });

    const wrapper = mount(TracingPage, {
      global: {
        stubs: {
          ContextHeader: {
            props: ["title", "description", "eyebrow"],
            template:
              '<header><span>{{ eyebrow }}</span><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>',
          },
          RiskSummary: {
            props: ["title", "items"],
            template:
              '<section><h2>{{ title }}</h2><div v-for="item in items" :key="item.title || item.label">{{ item.title || item.label }} {{ item.value ?? "" }} {{ item.detail ?? "" }}</div></section>',
          },
          ObservabilityGrid: {
            props: ["groups"],
            template:
              '<section><div v-for="group in groups" :key="group.key"><h3>{{ group.title }}</h3></div></section>',
          },
        },
      },
    });

    await flushPromises();

    expect(wrapper.text()).toContain("链路摘要");
    expect(wrapper.text()).toContain("优先关注业务 trace");
    expect(wrapper.text()).toContain("最近业务 Trace");
    expect(wrapper.text()).toContain("sample-service");
    expect(wrapper.text()).toContain("middleware-service");
    expect(wrapper.text()).toContain("mysql");
    expect(wrapper.text()).toContain("/api/demo-chain");
    expect(wrapper.text()).toContain("重点服务");
    expect(wrapper.text()).toContain("sample-service");
    expect(wrapper.text()).toContain("SkyWalking 工作区");
  });

  it("falls back to inferred dependency chain when latest trace preview is unavailable", async () => {
    fetchOverview.mockResolvedValue({
      nodes: { total: 2, online: 2, offline: 0, warning: 0 },
      services: { total: 7, healthy: 6, abnormal: 1 },
      unresolvedAlerts: 1,
      anomalies: { nodes: [], services: [{ id: 9, serviceName: "mysql" }] },
      quickLinks: [],
    });
    fetchServices.mockResolvedValue([
      {
        id: 7,
        serviceName: "sample-service",
        serviceType: "SPRING_BOOT",
        port: 8081,
        processName: "java",
        metricsPath: "/actuator/prometheus",
        nodeId: 1,
        nodeName: "app-node",
      },
      {
        id: 8,
        serviceName: "middleware-service",
        serviceType: "SPRING_BOOT",
        port: 8082,
        processName: "java",
        metricsPath: "/actuator/prometheus",
        nodeId: 2,
        nodeName: "middleware-node",
      },
      {
        id: 9,
        serviceName: "mysql",
        serviceType: "MYSQL",
        port: 3306,
        processName: "mysqld",
        metricsPath: null,
        nodeId: 2,
        nodeName: "middleware-node",
      },
      {
        id: 10,
        serviceName: "redis",
        serviceType: "REDIS",
        port: 6379,
        processName: "redis-server",
        metricsPath: "/metrics",
        nodeId: 2,
        nodeName: "middleware-node",
      },
      {
        id: 11,
        serviceName: "nginx",
        serviceType: "NGINX",
        port: 80,
        processName: "nginx",
        metricsPath: "/metrics",
        nodeId: 2,
        nodeName: "middleware-node",
      },
    ]);
    fetchTracingSummary.mockResolvedValue({
      serviceNames: ["sample-service", "middleware-service"],
      traces: [
        {
          traceId: "trace-2",
          endpoints: ["GET:/api/demo-chain", "GET:/api/middleware/profile"],
          durationMs: 88,
          startTime: "2026-06-21 12:00",
          error: false,
        },
      ],
      latestTrace: null,
    });

    const wrapper = mount(TracingPage, {
      global: {
        stubs: {
          ContextHeader: {
            props: ["title", "description", "eyebrow"],
            template:
              '<header><span>{{ eyebrow }}</span><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>',
          },
          RiskSummary: {
            props: ["title", "items"],
            template:
              '<section><h2>{{ title }}</h2><div v-for="item in items" :key="item.title || item.label">{{ item.title || item.label }} {{ item.value ?? "" }} {{ item.detail ?? "" }}</div></section>',
          },
          ObservabilityGrid: {
            props: ["groups"],
            template:
              '<section><div v-for="group in groups" :key="group.key"><h3>{{ group.title }}</h3></div></section>',
          },
        },
      },
    });

    await flushPromises();

    expect(wrapper.text()).toContain("最新完整调用链");
    expect(wrapper.text()).toContain("sample-service -> middleware-service");
    expect(wrapper.text()).toContain("mysql -> redis -> nginx");
  });
});
