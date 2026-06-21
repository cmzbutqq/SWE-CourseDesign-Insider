import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export function resolveProxyTargets(env = process.env) {
  return {
    api: env.VITE_PROXY_TARGET || "http://backend:8080",
    grafana: env.VITE_GRAFANA_PROXY_TARGET || "http://grafana:3000",
    prometheus:
      env.VITE_PROMETHEUS_PROXY_TARGET || "http://prometheus:9090",
    skywalking:
      env.VITE_SKYWALKING_PROXY_TARGET || "http://skywalking-ui:8080",
  };
}

export function createProxyConfig(env = process.env) {
  const targets = resolveProxyTargets(env);

  return {
    "/api": {
      target: targets.api,
      changeOrigin: true
    },
    "/grafana": {
      target: targets.grafana,
      changeOrigin: true
    },
    "/prometheus": {
      target: targets.prometheus,
      changeOrigin: true
    },
    "/skywalking": {
      target: targets.skywalking,
      changeOrigin: true
    }
  };
}

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: "0.0.0.0",
    proxy: createProxyConfig()
  },
  test: {
    environment: "jsdom"
  }
});
