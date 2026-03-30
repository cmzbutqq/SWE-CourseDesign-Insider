<template>
  <section class="page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Overview</p>
        <h2>统一监控总览</h2>
      </div>
      <button class="ghost" @click="load">刷新</button>
    </header>

    <div class="cards">
      <article class="card">
        <span>节点总数</span>
        <strong>{{ overview.totalNodes }}</strong>
      </article>
      <article class="card">
        <span>服务总数</span>
        <strong>{{ overview.totalServices }}</strong>
      </article>
      <article class="card">
        <span>在线节点</span>
        <strong>{{ overview.onlineNodes }}</strong>
      </article>
    </div>

    <article class="panel">
      <h3>观测入口</h3>
      <ul class="link-list">
        <li v-for="link in overview.quickLinks" :key="link">{{ link }}</li>
      </ul>
    </article>

    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { fetchOverview } from "../services/api";

const overview = reactive({
  totalNodes: 0,
  totalServices: 0,
  onlineNodes: 0,
  quickLinks: []
});
const error = ref("");

async function load() {
  error.value = "";
  try {
    Object.assign(overview, await fetchOverview());
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(load);
</script>
