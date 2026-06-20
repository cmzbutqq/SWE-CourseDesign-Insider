import { createRouter, createWebHistory } from "vue-router";
import OverviewPage from "../pages/OverviewPage.vue";
import NodesPage from "../pages/NodesPage.vue";
import NodeDetailPage from "../pages/NodeDetailPage.vue";
import ServicesPage from "../pages/ServicesPage.vue";
import ServiceDetailPage from "../pages/ServiceDetailPage.vue";
import TracingPage from "../pages/TracingPage.vue";
import DebugPage from "../pages/DebugPage.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      redirect: "/overview",
    },
    {
      path: "/overview",
      component: OverviewPage,
      meta: {
        title: "系统总览",
        description: "平台健康、风险摘要和多源观测面板。",
      },
    },
    {
      path: "/nodes",
      component: NodesPage,
      meta: {
        title: "节点视图",
        description: "节点筛选、状态扫描与主机入口。",
      },
    },
    {
      path: "/nodes/:id",
      component: NodeDetailPage,
      props: true,
      meta: {
        title: "节点详情",
        description: "资源、服务、链路和原始监控聚合到一个桌面工作面。",
      },
    },
    {
      path: "/services",
      component: ServicesPage,
      meta: {
        title: "服务视图",
        description: "服务清单、metrics 配置状态和 drill-down 入口。",
      },
    },
    {
      path: "/services/:id",
      component: ServiceDetailPage,
      props: true,
      meta: {
        title: "服务详情",
        description: "运行指标、调用链和 Prometheus 调试入口。",
      },
    },
    {
      path: "/tracing",
      component: TracingPage,
      meta: {
        title: "链路追踪",
        description: "SkyWalking 服务视图直接嵌入前台。",
      },
    },
    {
      path: "/debug",
      component: DebugPage,
      meta: {
        title: "调试面板",
        description: "Prometheus 原始 targets 与 query 验证入口。",
      },
    },
  ],
});

export default router;
