import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import DebugPage from "./DebugPage.vue";
import { fetchPrometheusTargets } from "../services/prometheus";

vi.mock("../services/prometheus", () => ({
  fetchPrometheusTargets: vi.fn(),
  summarizePrometheusTargets: (data) => ({
    totalTargets: data.activeTargets.length,
    upTargets: data.activeTargets.filter((target) => target.health === "up").length,
    problemTargets: data.activeTargets.filter((target) => target.health !== "up").length,
    scrapePools: data.activeTargets.length,
    slowestTarget: {
      job: data.activeTargets[0]?.labels?.job || "-",
      durationMs: 53,
    },
    targets: data.activeTargets.map((target) => ({
      job: target.labels.job,
      instance: target.labels.instance,
      health: target.health,
      lastError: target.lastError,
      durationMs: 53,
    })),
  }),
}));

describe("DebugPage", () => {
  beforeEach(() => {
    fetchPrometheusTargets.mockReset();
  });

  it("renders a local prometheus summary ahead of the raw debug panels", async () => {
    fetchPrometheusTargets.mockResolvedValue({
      activeTargets: [
        {
          labels: { job: "sample-service", instance: "app-node:8081" },
          health: "up",
          lastError: "",
        },
      ],
    });

    const wrapper = mount(DebugPage, {
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

    expect(wrapper.text()).toContain("Prometheus 摘要");
    expect(wrapper.text()).toContain("采集对象");
    expect(wrapper.text()).toContain("sample-service");
    expect(wrapper.text()).toContain("Prometheus 调试面板");
  });
});
