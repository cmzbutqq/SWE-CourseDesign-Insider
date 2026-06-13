import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import TrendsChart from "./TrendsChart.vue";
import { fetchTrends } from "../services/api.js";

const chartMocks = vi.hoisted(() => ({
  init: vi.fn(),
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn(),
  getDom: vi.fn(),
}));

const serviceMocks = vi.hoisted(() => ({
  fetchTrends: vi.fn(),
}));

vi.mock("echarts", () => {
  chartMocks.init.mockImplementation((container) => {
    chartMocks.getDom.mockReturnValue(container);
    return {
      setOption: chartMocks.setOption,
      resize: chartMocks.resize,
      dispose: chartMocks.dispose,
      getDom: chartMocks.getDom,
    };
  });

  return {
    init: chartMocks.init,
  };
});

vi.mock("../services/api.js", () => ({
  fetchTrends: serviceMocks.fetchTrends,
}));

function buildTrend(metricName, currentValue, values, unit = "个") {
  return {
    metricName,
    currentValue,
    unit,
    values,
    timestamps: [1710000000000, 1710000060000],
  };
}

describe("TrendsChart", () => {
  beforeEach(() => {
    chartMocks.init.mockClear();
    chartMocks.setOption.mockClear();
    chartMocks.resize.mockClear();
    chartMocks.dispose.mockClear();
    chartMocks.getDom.mockClear();
    fetchTrends.mockReset();
  });

  it("hides stale anomaly decorations after a refresh error", async () => {
    fetchTrends
      .mockResolvedValueOnce({
        timeRange: "最近1小时",
        trends: [
          buildTrend("在线节点", 2, [2, 2]),
          buildTrend("离线节点", 1, [0, 1]),
          buildTrend("识别服务", 3, [3, 3]),
          buildTrend("异常服务", 0, [0, 0]),
          buildTrend("未处理告警", 0, [0, 0], "项"),
        ],
      })
      .mockRejectedValueOnce(new Error("请求失败"));

    const wrapper = mount(TrendsChart);
    await flushPromises();

    expect(wrapper.find(".anomaly-banner").exists()).toBe(true);
    expect(wrapper.find(".time-range-label").exists()).toBe(true);

    await wrapper.findAll(".range-button")[2].trigger("click");
    await flushPromises();

    expect(wrapper.find(".error-container").text()).toContain("请求失败");
    expect(wrapper.find(".anomaly-banner").exists()).toBe(false);
    expect(wrapper.find(".time-range-label").exists()).toBe(false);
  });

  it("keeps anomaly-capable metrics solid when their current value is normal", async () => {
    fetchTrends.mockResolvedValue({
      timeRange: "最近1小时",
      trends: [
        buildTrend("在线节点", 3, [2, 3]),
        buildTrend("离线节点", 0, [1, 0]),
        buildTrend("识别服务", 4, [4, 4]),
        buildTrend("异常服务", 0, [1, 0]),
        buildTrend("未处理告警", 0, [0, 0], "项"),
      ],
    });

    mount(TrendsChart);
    await flushPromises();

    const option = chartMocks.setOption.mock.calls.at(-1)[0];
    const offlineSeries = option.series.find((series) => series.name === "离线节点");

    expect(offlineSeries.lineStyle.type).toBe("solid");
    expect(offlineSeries.symbolSize).toBe(4);
    expect(offlineSeries.areaStyle).toBeUndefined();
    expect(offlineSeries.markPoint).toBeUndefined();
  });
});
