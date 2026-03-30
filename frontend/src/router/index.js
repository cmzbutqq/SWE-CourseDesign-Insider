import { createRouter, createWebHistory } from "vue-router";
import OverviewPage from "../pages/OverviewPage.vue";
import NodesPage from "../pages/NodesPage.vue";
import NodeDetailPage from "../pages/NodeDetailPage.vue";
import ServicesPage from "../pages/ServicesPage.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/overview" },
    { path: "/overview", component: OverviewPage },
    { path: "/nodes", component: NodesPage },
    { path: "/nodes/:id", component: NodeDetailPage, props: true },
    { path: "/services", component: ServicesPage }
  ]
});

export default router;
