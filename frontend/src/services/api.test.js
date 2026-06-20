import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchServiceDetail, resolveApiBaseUrl } from "./api";

describe("api service helpers", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("uses the relative api path by default", () => {
    expect(resolveApiBaseUrl({})).toBe("/api");
  });

  it("allows overriding the api base url via env", () => {
    expect(
      resolveApiBaseUrl({ VITE_API_BASE_URL: "http://example.com/api" })
    ).toBe("http://example.com/api");
  });

  it("requests service detail by id", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: true,
      json: async () => ({ id: 7 }),
    });

    await fetchServiceDetail(7);

    expect(fetchMock).toHaveBeenCalledWith("/api/services/7");
  });

  it("surfaces fetchJson errors when service detail responds with a failure", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: false,
      status: 503,
    });

    await expect(fetchServiceDetail(7)).rejects.toThrow("Request failed: 503");
  });
});
