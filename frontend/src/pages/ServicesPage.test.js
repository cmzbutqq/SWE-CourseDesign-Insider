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

  it("treats whitespace-only metrics paths as unavailable and keeps node detail links", async () => {
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
          RouterLink: RouterLinkStub
        }
      }
    });

    await flushPromises();

    expect(fetchServices).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("1 个指标路径不可用");
    expect(wrapper.text()).toContain("未配置");
    expect(
      wrapper
        .findAllComponents(RouterLinkStub)
        .some((link) => link.props("to") === "/nodes/42")
    ).toBe(true);
  });
});
