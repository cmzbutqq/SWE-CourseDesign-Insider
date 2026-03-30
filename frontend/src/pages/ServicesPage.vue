<template>
  <section class="page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Services</p>
        <h2>服务清单</h2>
      </div>
      <button class="ghost" @click="load">刷新</button>
    </header>

    <article class="panel">
      <table class="data-table">
        <thead>
          <tr>
            <th>服务</th>
            <th>类型</th>
            <th>节点</th>
            <th>端口</th>
            <th>指标</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="service in services" :key="service.id">
            <td>{{ service.serviceName }}</td>
            <td>{{ service.serviceType }}</td>
            <td>{{ service.nodeName }}</td>
            <td>{{ service.port }}</td>
            <td>{{ service.metricsPath || "-" }}</td>
          </tr>
        </tbody>
      </table>
    </article>

    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { fetchServices } from "../services/api";

const services = ref([]);
const error = ref("");

async function load() {
  error.value = "";
  try {
    services.value = await fetchServices();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(load);
</script>
