<template>
  <div class="app-shell">
    <aside class="workspace-sidebar">
      <div class="workspace-brand">
        <p class="workspace-brand__eyebrow">SCUT Unified Monitoring Platform</p>
        <h1>一体化监控工作台</h1>
        <p class="workspace-brand__summary">
          面向桌面排障的统一壳子，收口总览、节点、服务、链路与原始调试入口。
        </p>
      </div>

      <nav class="workspace-nav" aria-label="Primary navigation">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="workspace-nav__link"
        >
          <span class="workspace-nav__index">{{ item.code }}</span>
          <span class="workspace-nav__body">
            <strong>{{ item.label }}</strong>
            <small>{{ item.hint }}</small>
          </span>
        </RouterLink>
      </nav>

      <section class="workspace-sidebar__section">
        <div class="section-header section-header--compact">
          <div>
            <h3 class="section-title">固定端口</h3>
          </div>
        </div>
        <dl class="sidebar-metrics">
          <div>
            <dt>Frontend</dt>
            <dd>15173</dd>
          </div>
          <div>
            <dt>Backend</dt>
            <dd>18081</dd>
          </div>
          <div>
            <dt>MySQL</dt>
            <dd>13306</dd>
          </div>
        </dl>
      </section>
    </aside>

    <main class="workspace-main">
      <header class="workspace-topbar">
        <div>
          <p class="eyebrow">Operations Workspace</p>
          <h2>{{ currentView.title }}</h2>
          <p class="workspace-topbar__description">
            {{ currentView.description }}
          </p>
        </div>

        <div class="workspace-topbar__badges">
          <span class="badge badge-info">Desktop</span>
          <span class="badge badge-neutral">Docker Compose</span>
        </div>
      </header>

      <section class="workspace-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();

const navItems = [
  {
    to: "/overview",
    code: "OV",
    label: "总览",
    hint: "平台健康与告警入口",
  },
  {
    to: "/nodes",
    code: "ND",
    label: "节点",
    hint: "节点筛选与主机状态",
  },
  {
    to: "/services",
    code: "SV",
    label: "服务",
    hint: "服务分组与详情入口",
  },
  {
    to: "/tracing",
    code: "TR",
    label: "追踪",
    hint: "SkyWalking 链路视图",
  },
  {
    to: "/debug",
    code: "DG",
    label: "调试",
    hint: "Prometheus 原始查询",
  },
];

const currentView = computed(() => ({
  title: route.meta?.title || "一体化监控工作台",
  description:
    route.meta?.description || "容器化监控、链路和调试入口统一收口。",
}));
</script>
