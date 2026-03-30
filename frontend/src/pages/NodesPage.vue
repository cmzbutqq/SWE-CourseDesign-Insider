<template>
  <section class="page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Nodes</p>
        <h2>模拟节点列表</h2>
      </div>
      <button class="ghost" @click="load">刷新</button>
    </header>

    <article class="panel">
      <table class="data-table">
        <thead>
          <tr>
            <th>节点</th>
            <th>IP</th>
            <th>状态</th>
            <th>识别类型</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="node in nodes" :key="node.id">
            <td>
              <RouterLink :to="`/nodes/${node.id}`">{{ node.nodeName }}</RouterLink>
            </td>
            <td>{{ node.ipAddress }}</td>
            <td>{{ node.status }}</td>
            <td>{{ node.serviceTypes.join(", ") }}</td>
          </tr>
        </tbody>
      </table>
    </article>

    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { fetchNodes } from "../services/api";

const nodes = ref([]);
const error = ref("");

async function load() {
  error.value = "";
  try {
    nodes.value = await fetchNodes();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(load);
</script>
