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
      metricsPort: 8081,
      nodeId: 1,
      nodeName: "app-node",
      nodeIpAddress: "172.20.0.10",
      nodeStatus: "ONLINE",
      metricsMissing: true,
      quickLinks: [],
    });

    const wrapper = mount(ServiceDetailPage, {
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

    expect(wrapper.text()).toContain("当前服务缺少可抓取指标端点");
    expect(wrapper.text()).toContain("服务摘要");
    expect(wrapper.text()).toContain("运行指标");
    expect(wrapper.text()).toContain("调用链");
    expect(wrapper.text()).toContain("Prometheus 调试");
  });
});
