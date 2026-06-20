import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import ObservabilityPanel from "./ObservabilityPanel.vue";

describe("ObservabilityPanel", () => {
  it("renders the iframe immediately and clears the loading state after load", async () => {
    const wrapper = mount(ObservabilityPanel, {
      props: {
        title: "CPU 使用率",
        description: "最近一小时节点 CPU 概况",
        source: "Grafana",
        src: "/grafana/d-solo/node-overview",
        fallbackHref: "/grafana/d/node-overview",
      },
    });

    expect(wrapper.find(".observability-panel").exists()).toBe(true);
    expect(wrapper.find(".source-badge").text()).toContain("Grafana");
    expect(wrapper.find("iframe").attributes("src")).toBe(
      "/grafana/d-solo/node-overview"
    );
    expect(wrapper.find(".panel-loading").text()).toContain("加载中");

    await wrapper.find("iframe").trigger("load");

    expect(wrapper.find(".panel-loading").exists()).toBe(false);
    expect(wrapper.find(".panel-fallback").exists()).toBe(false);
  });

  it("shows a local fallback with source context after iframe errors", async () => {
    const wrapper = mount(ObservabilityPanel, {
      props: {
        title: "节点目标列表",
        description: "Prometheus targets 视图",
        source: "Prometheus",
        src: "/prometheus/targets",
        fallbackHref: "/prometheus/targets",
      },
    });

    await wrapper.find("iframe").trigger("error");

    expect(wrapper.find(".panel-loading").exists()).toBe(false);
    expect(wrapper.find(".panel-fallback").text()).toContain("Prometheus");
    expect(wrapper.find(".panel-fallback").text()).toContain("无法在当前工作台加载");
    expect(wrapper.find(".panel-link").attributes("href")).toBe(
      "/prometheus/targets"
    );
  });

  it("supports scaled iframe viewports for dense third-party panels", () => {
    const wrapper = mount(ObservabilityPanel, {
      props: {
        title: "链路视图",
        source: "SkyWalking",
        src: "/skywalking/services",
        frameScale: 0.8,
        height: 480,
      },
    });

    expect(wrapper.find(".observability-frame-shell").attributes("style")).toContain(
      "--observability-frame-scale: 0.8"
    );
    expect(wrapper.find(".observability-frame-shell").attributes("style")).toContain(
      "height: 480px"
    );
    expect(
      wrapper.find(".observability-frame-viewport").attributes("style")
    ).toContain("transform: scale(0.8)");
  });
});
