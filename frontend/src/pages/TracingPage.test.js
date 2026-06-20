import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import TracingPage from "./TracingPage.vue";
import { fetchOverview, fetchServices } from "../services/api";

vi.mock("../services/api", () => ({
  fetchOverview: vi.fn(),
  fetchServices: vi.fn(),
}));

describe("TracingPage", () => {
  beforeEach(() => {
    fetchOverview.mockReset();
    fetchServices.mockReset();
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
              '<section><h2>{{ title }}</h2><div v-for="item in items" :key="item.title || item.label">{{ item.title || item.label }}</div></section>',
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
    expect(wrapper.text()).toContain("/actuator/prometheus");
    expect(wrapper.text()).toContain("重点服务");
    expect(wrapper.text()).toContain("sample-service");
    expect(wrapper.text()).toContain("SkyWalking 工作区");
  });
});
