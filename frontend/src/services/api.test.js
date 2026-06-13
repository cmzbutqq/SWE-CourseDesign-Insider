import { describe, expect, it } from "vitest";
import { resolveApiBaseUrl } from "./api";

describe("resolveApiBaseUrl", () => {
  it("uses the relative api path by default", () => {
    expect(resolveApiBaseUrl({})).toBe("/api");
  });

  it("allows overriding the api base url via env", () => {
    expect(
      resolveApiBaseUrl({ VITE_API_BASE_URL: "http://example.com/api" })
    ).toBe("http://example.com/api");
  });
});
