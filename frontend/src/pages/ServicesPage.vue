<template>
  <section class="page services-page">
    <ContextHeader
      eyebrow="Services"
      title="服务观测索引"
      description="把服务清单、metrics 配置状态、节点归属和详情入口收拢在同一个工作面。"
    >
      <template #actions>
        <button class="ghost" :disabled="loading" @click="load">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </template>
    </ContextHeader>

    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="loading && services.length === 0" class="panel loading-panel">
      <p>正在加载服务清单...</p>
    </section>

    <template v-else>
      <section class="panel summary-panel" v-if="services.length > 0">
        <div class="summary-stat">
          <span>服务总数</span>
          <strong>{{ services.length }}</strong>
        </div>
        <div class="summary-stat">
          <span>指标完整</span>
          <strong>{{ services.length - noMetricsCount }}</strong>
        </div>
        <div class="summary-stat warning">
          <span>未配置抓取路径</span>
          <strong>{{ noMetricsCount }}</strong>
        </div>
      </section>

      <section
        v-for="(group, type) in groupedServices"
        :key="type"
        class="page-section"
      >
        <div class="section-heading">
          <div>
            <h3>{{ type }}</h3>
            <p>{{ group.length }} 个服务，{{ unhealthyCount(group) }} 个抓取路径缺失。</p>
          </div>
        </div>
        <article class="panel">
          <table class="data-table">
            <thead>
              <tr>
                <th>服务</th>
                <th>节点</th>
                <th>端口</th>
                <th>指标端口</th>
                <th>进程</th>
                <th>抓取路径</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="service in group" :key="service.id">
                <td>
                  <RouterLink class="table-link" :to="`/services/${service.id}`">
                    {{ service.serviceName }}
                  </RouterLink>
                </td>
                <td>
                  <RouterLink
                    v-if="service.nodeId"
                    class="table-link secondary"
                    :to="`/nodes/${service.nodeId}`"
                  >
                    {{ service.nodeName }}
                  </RouterLink>
                  <span v-else>{{ service.nodeName || "-" }}</span>
                </td>
                <td>{{ service.port || "-" }}</td>
                <td>{{ service.metricsPort ?? "-" }}</td>
                <td>{{ service.processName || "-" }}</td>
                <td>
                  <span v-if="hasMetricsPath(service)" class="status-pill success">
                    {{ service.metricsPath.trim() }}
                  </span>
                  <span v-else class="status-pill warning">未配置</span>
                </td>
                <td class="actions-cell">
                  <RouterLink class="inline-link" :to="`/services/${service.id}`">
                    服务详情
                  </RouterLink>
                  <RouterLink
                    v-if="service.nodeId"
                    class="inline-link"
                    :to="`/nodes/${service.nodeId}`"
                  >
                    节点详情
                  </RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <article v-if="!loading && services.length === 0" class="panel empty-state">
        <p>暂无服务数据，请先确认 Agent 注册与服务发现流程已完成。</p>
      </article>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import ContextHeader from "../components/ContextHeader.vue";
import { fetchServices } from "../services/api";

const services = ref([]);
const loading = ref(false);
const error = ref("");

const groupedServices = computed(() => {
  const groups = {};
  for (const service of services.value) {
    const type = service.serviceType || "OTHER";
    if (!groups[type]) {
      groups[type] = [];
    }
    groups[type].push(service);
  }
  return groups;
});

const noMetricsCount = computed(() =>
  services.value.filter((service) => !hasMetricsPath(service)).length
);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    services.value = await fetchServices();
  } catch (err) {
    error.value = err.message || "获取服务清单失败";
  } finally {
    loading.value = false;
  }
}

function hasMetricsPath(service) {
  return Boolean(service.metricsPath?.trim());
}

function unhealthyCount(group) {
  return group.filter((service) => !hasMetricsPath(service)).length;
}

onMounted(load);
</script>

<style scoped>
.summary-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.summary-stat span {
  display: block;
  margin-bottom: 6px;
  color: #5b6472;
  font-size: 13px;
}

.summary-stat strong {
  font-size: 28px;
  color: #101828;
}

.summary-stat.warning strong {
  color: #b54708;
}

.section-heading {
  margin-bottom: 12px;
}

.section-heading h3 {
  margin: 0 0 4px;
}

.section-heading p {
  margin: 0;
  color: #5b6472;
  font-size: 14px;
}

.loading-panel,
.empty-state {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5b6472;
}

.table-link {
  color: #0f6cbd;
  font-weight: 500;
}

.table-link.secondary {
  font-weight: 400;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
}

.status-pill.success {
  background: #ecfdf3;
  color: #027a48;
}

.status-pill.warning {
  background: #fffaeb;
  color: #b54708;
}

.actions-cell {
  white-space: nowrap;
}

.inline-link {
  margin-right: 12px;
  color: #0f6cbd;
}
</style>
