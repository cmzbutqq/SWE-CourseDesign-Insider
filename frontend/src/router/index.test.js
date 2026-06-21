import { describe, expect, it } from "vitest";
import router from "./index.js";

function getRoute(path) {
  return router.getRoutes().find((route) => route.path === path);
}

describe("router", () => {
  it("redirects root traffic into the overview workspace", () => {
    expect(getRoute("/").redirect).toBe("/overview");
  });

  it("registers list and detail routes for the desktop workspace", () => {
    const paths = router.getRoutes().map((route) => route.path);

    expect(paths).toEqual(
      expect.arrayContaining([
        "/overview",
        "/nodes",
        "/nodes/:id",
        "/services",
        "/services/:id",
        "/tracing",
        "/debug",
      ])
    );
  });

  it("passes route params into the detail pages as props", () => {
    expect(getRoute("/nodes/:id").props).toEqual({ default: true });
    expect(getRoute("/services/:id").props).toEqual({ default: true });
  });

  it("resolves dynamic paths for node and service detail workspaces", () => {
    const nodeTarget = router.resolve("/nodes/node-a");
    const serviceTarget = router.resolve("/services/svc-a");

    expect(nodeTarget.params).toEqual({ id: "node-a" });
    expect(serviceTarget.params).toEqual({ id: "svc-a" });
  });
});
