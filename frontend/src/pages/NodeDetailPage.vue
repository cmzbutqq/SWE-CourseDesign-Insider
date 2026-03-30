<template>
  <section class="page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Node Detail</p>
        <h2>{{ node.nodeName || "节点详情" }}</h2>
      </div>
      <button class="ghost" @click="load">刷新</button>
    </header>

    <div class="cards">
      <article class="card">
        <span>主机名</span>
        <strong>{{ node.hostname || "-" }}</strong>
      </article>
      <article class="card">
        <span>系统</span>
        <strong>{{ node.osName || "-" }}</strong>
      </article>
      <article class="card">
        <span>Agent</span>
        <strong>{{ node.agentVersion || "-" }}</strong>
      </article>
    </div>

    <article class="panel">
      <h3>识别到的服务</h3>
      <table class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>类型</th>
            <th>端口</th>
            <th>进程</th>
            <th>指标路径</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="service in node.services" :key="service.id">
            <td>{{ service.serviceName }}</td>
            <td>{{ service.serviceType }}</td>
            <td>{{ service.port }}</td>
            <td>{{ service.processName }}</td>
            <td>{{ service.metricsPath || "-" }}</td>
          </tr>
        </tbody>
      </table>
    </article>

    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchNodeDetail } from "../services/api";

const route = useRoute();
const error = ref("");
const node = reactive({
  nodeName: "",
  hostname: "",
  osName: "",
  agentVersion: "",
  services: []
});

async function load() {
  error.value = "";
  try {
    Object.assign(node, await fetchNodeDetail(route.params.id));
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(load);
</script>
