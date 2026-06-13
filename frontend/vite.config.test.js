import { describe, expect, it } from "vitest";
import config from "./vite.config.js";

describe("vite config", () => {
  it("proxies api requests to the backend container", () => {
    expect(config.server.proxy["/api"].target).toBe("http://backend:8080");
    expect(config.server.proxy["/api"].changeOrigin).toBe(true);
  });
});
