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
        stubs: {
          RouterLink: RouterLinkStub,
          ContextHeader: {
            props: ["title", "description", "eyebrow"],
            template: "<header><h1>{{ title }}</h1><p>{{ description }}</p><slot name='actions' /></header>",
          },
          RiskSummary: {
            props: ["title", "items"],
            template: "<section><h2>{{ title }}</h2></section>",
          },
          ObservabilityGrid: {
            props: ["groups"],
            template:
              "<section><div v-for='group in groups' :key='group.key'><h3>{{ group.title }}</h3><span>{{ group.panels.length }}</span></div></section>",
          },
        },
      },
    });

    await flushPromises();

    expect(wrapper.text()).toContain("资源监控");
    expect(wrapper.text()).toContain("服务清单");
    expect(wrapper.text()).toContain("链路关联");
    expect(wrapper.text()).toContain("原始监控");
  });
});
