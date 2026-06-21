import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, RouterLinkStub } from "@vue/test-utils";
import ServicesPage from "./ServicesPage.vue";
import { fetchServices } from "../services/api";

vi.mock("../services/api", () => ({
  fetchServices: vi.fn()
}));

describe("ServicesPage", () => {
  beforeEach(() => {
    fetchServices.mockReset();
  });

  it("treats whitespace-only scrape paths as unavailable and links each row to service detail", async () => {
    fetchServices.mockResolvedValue([
      {
        id: 1,
        serviceName: "nginx",
        serviceType: "NGINX",
        nodeId: 42,
        nodeName: "app-node",
        port: 80,
        metricsPath: " "
      }
    ]);

    const wrapper = mount(ServicesPage, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
          ContextHeader: {
            props: ["title", "description", "eyebrow"],
            template: "<header><h1>{{ title }}</h1><slot name='actions' /></header>",
          },
        }
      }
    });

    await flushPromises();

    expect(fetchServices).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("1 个抓取路径缺失");
    expect(wrapper.text()).toContain("未配置");
    expect(
      wrapper
        .findAllComponents(RouterLinkStub)
        .some((link) => link.props("to") === "/services/1")
    ).toBe(true);
    expect(
      wrapper
        .findAllComponents(RouterLinkStub)
        .some((link) => link.props("to") === "/nodes/42")
    ).toBe(true);
  });
});
