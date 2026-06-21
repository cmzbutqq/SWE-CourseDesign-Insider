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
      anomalies: {
        nodes: [{ id: 1, nodeName: "app-node" }],
        services: [{ id: 7, serviceName: "sample-service" }],
      },
      quickLinks: [],
    });

    const wrapper = mount(OverviewPage, {
      global: {
        stubs: {
          ContextHeader: {
            props: ["title", "description", "eyebrow"],
            template:
              '<header><span>{{ eyebrow }}</span><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>',
          },
          RiskSummary: {
            props: ["title", "items", "variant"],
            template:
              '<section><h2>{{ title }}</h2><div v-for="item in items" :key="item.title || item.label">{{ item.title || item.label }}</div></section>',
          },
          ObservabilityGrid: {
            props: ["groups"],
            template:
              '<section><div v-for="group in groups" :key="group.key"><h3>{{ group.title }}</h3></div></section>',
          },
          TrendsChart: {
            template: "<div>TrendsChart</div>",
          },
        },
      },
    });

    await flushPromises();

    expect(wrapper.text()).toContain("基础资源");
    expect(wrapper.text()).toContain("应用健康");
    expect(wrapper.text()).toContain("链路观测");
    expect(wrapper.text()).toContain("高风险节点");
  });
});
