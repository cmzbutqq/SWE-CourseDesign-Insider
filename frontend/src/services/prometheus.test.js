import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchPrometheusTargets,
  resolvePrometheusApiBaseUrl,
  summarizePrometheusTargets,
} from "./prometheus";

describe("prometheus service helpers", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("uses the proxied prometheus api path by default", () => {
    expect(resolvePrometheusApiBaseUrl({})).toBe("/prometheus/api/v1");
  });

  it("requests the targets endpoint through the frontend proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: true,
      json: async () => ({ status: "success", data: { activeTargets: [] } }),
    });

    await fetchPrometheusTargets();

    expect(fetchMock).toHaveBeenCalledWith("/prometheus/api/v1/targets");
  });

  it("summarizes health counts and the slowest scrape target", () => {
    const summary = summarizePrometheusTargets({
      activeTargets: [
        {
          labels: { job: "app-node", instance: "app-node:9100" },
          scrapePool: "app-node",
          health: "up",
          lastScrapeDuration: 0.052,
          lastError: "",
          scrapeUrl: "http://app-node:9100/metrics",
        },
        {
          labels: { job: "sample-service", instance: "app-node:8081" },
          scrapePool: "sample-service",
          health: "down",
          lastScrapeDuration: 0.11,
          lastError: "context deadline exceeded",
          scrapeUrl: "http://app-node:8081/actuator/prometheus",
        },
      ],
    });

    expect(summary.totalTargets).toBe(2);
    expect(summary.upTargets).toBe(1);
    expect(summary.problemTargets).toBe(1);
    expect(summary.scrapePools).toBe(2);
    expect(summary.slowestTarget.job).toBe("sample-service");
    expect(summary.slowestTarget.durationMs).toBe(110);
    expect(summary.targets[1].lastError).toContain("deadline");
  });
});
