<template>
  <section class="trends-section">
    <div class="trends-header">
      <div>
        <h3>系统趋势分析</h3>
        <p class="trends-subtitle">展示节点在线状况与服务健康趋势，帮助判断系统是否稳定运行</p>
      </div>
      <span v-if="trendsData && !error" class="time-range-label">
        时间范围：{{ trendsData.timeRange }}
      </span>
    </div>

    <!-- 时间范围选择器 -->
    <div class="time-range-selector">
      <button
        v-for="range in timeRanges"
        :key="range.hours"
        :class="{ active: selectedHours === range.hours }"
        @click="loadTrendData(range.hours)"
        class="range-button"
      >
        {{ range.label }}
      </button>
    </div>

    <!-- 异常提示横幅 -->
    <div v-if="hasAnomalies && !isLoading && !error" class="anomaly-banner">
      ⚠ 检测到异常：{{ anomalySummary }}，请关注下方趋势图
    </div>

    <!-- 加载状态 -->
    <div v-show="isLoading" class="loading-container">
      <p class="loading-text">加载趋势数据中...</p>
      <div class="skeleton-chart"></div>
    </div>

    <!-- 错误状态 -->
    <div v-show="error && !isLoading" class="error-container">
      <p class="error-message">{{ error }}</p>
      <button @click="loadTrendData(selectedHours)" class="retry-button">重试</button>
    </div>

    <!-- 当前值卡片 -->
    <div v-if="hasData && !isLoading && !error" class="current-values">
      <div
        v-for="trend in trendsData.trends"
        :key="trend.metricName"
        :class="['metric-card', getMetricCardClass(trend)]"
      >
        <span class="metric-label">{{ metricLabel(trend.metricName) }}</span>
        <span class="metric-value">{{ trend.currentValue }}<span class="metric-unit">{{ trend.unit }}</span></span>
        <span class="metric-desc">{{ metricDesc(trend.metricName) }}</span>
      </div>
    </div>

    <!-- 图表 -->
    <div v-show="hasData && !isLoading && !error" ref="chartContainer" class="chart-container"></div>

    <!-- 图表说明 -->
    <div v-if="hasData && !isLoading && !error" class="chart-legend-note">
      <span>📌 节点在线数越高越好；离线节点、异常服务、未处理告警越低越好；识别服务通常应保持稳定</span>
    </div>

    <!-- 无数据状态 -->
    <div v-show="!hasData && !isLoading && !error" class="no-data">
      <p>暂无趋势数据，请稍候</p>
      <p class="no-data-hint">数据每分钟采集一次，新部署的系统需等待约 1 分钟后才有数据</p>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { fetchTrends } from '../services/api.js';

const timeRanges = [
  { hours: 0.25, label: '15分钟' },
  { hours: 0.5,  label: '30分钟' },
  { hours: 1,    label: '1小时' },
  { hours: 2,    label: '2小时' },
  { hours: 4,    label: '4小时' },
  { hours: 24,   label: '24小时' }
];

const selectedHours = ref(1);
const isLoading = ref(false);
const error = ref('');
const chartContainer = ref(null);
const trendsData = ref(null);
let chart = null;
const ALERT_METRIC_NAMES = new Set(['离线节点', '异常服务', '未处理告警']);

const handleWindowResize = () => { if (chart) chart.resize(); };

const hasData = computed(() => trendsData.value && trendsData.value.trends.length > 0);

// 异常判断：离线节点 > 0 或 异常服务 > 0 或 未处理告警 > 0
const hasAnomalies = computed(() => {
  if (!trendsData.value?.trends) return false;
  return trendsData.value.trends.some((trend) => isAlertingTrend(trend));
});

const anomalySummary = computed(() => {
  if (!trendsData.value?.trends) return '';
  const parts = [];
  trendsData.value.trends.forEach((trend) => {
    if (isAlertingTrend(trend)) {
      parts.push(`${trend.metricName} ${trend.currentValue}${trend.unit}`);
    }
  });
  return parts.join('、');
});

// 指标中文名映射（后端返回乱码时备用，但直接用 metricName 即可）
function metricLabel(name) {
  const map = {
    '在线节点': '在线节点',
    '离线节点': '离线节点',
    '识别服务': '识别服务',
    '异常服务': '异常服务',
    '未处理告警': '未处理告警'
  };
  return map[name] || name;
}

function metricDesc(name) {
  const map = {
    '在线节点': '当前心跳正常',
    '离线节点': '心跳超时或下线',
    '识别服务': '已扫描到的服务',
    '异常服务': '无指标路径的服务',
    '未处理告警': '需要关注的问题'
  };
  return map[name] || '';
}

function getMetricCardClass(trend) {
  if (isAlertingTrend(trend)) return 'card-danger';
  if (trend.metricName === '在线节点' && Number(trend.currentValue) > 0) return 'card-good';
  return 'card-neutral';
}

// 颜色配置
const COLORS = {
  '在线节点':   '#52C41A',
  '离线节点':   '#FF4D4F',
  '识别服务':   '#1890FF',
  '异常服务':   '#FF7A45',
  '未处理告警': '#722ED1'
};

async function loadTrendData(hours) {
  selectedHours.value = hours;
  isLoading.value = true;
  error.value = '';
  trendsData.value = null;
  try {
    const data = await fetchTrends(hours);
    trendsData.value = data;
    isLoading.value = false;
    if (!data?.trends?.length) return;
    await nextTick();
    renderChart(data);
  } catch (err) {
    trendsData.value = null;
    error.value = err.message || '加载趋势数据失败';
  } finally {
    isLoading.value = false;
  }
}

function renderChart(data) {
  const container = chartContainer.value;
  if (!data?.trends?.length || !container) return;

  if (!chart) {
    chart = echarts.init(container);
  } else if (chart.getDom() !== container) {
    chart.dispose();
    chart = echarts.init(container);
  }

  const xAxisData = data.trends[0]?.timestamps.map(ts =>
    new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  ) || [];

  const series = data.trends.map(trend => {
    const color = COLORS[trend.metricName] || '#FAAD14';
    const isAlerting = isAlertingTrend(trend);

    return {
      name: trend.metricName,
      type: 'line',
      data: trend.values.map(v => (typeof v === 'number' ? v : parseFloat(v))),
      smooth: true,
      symbol: 'circle',
      symbolSize: isAlerting ? 6 : 4,
      lineStyle: {
        width: isAlerting ? 2.5 : 2,
        color,
        type: isAlerting ? 'dashed' : 'solid'
      },
      itemStyle: { color },
      areaStyle: isAlerting ? {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: color + '33' },
            { offset: 1, color: color + '00' }
          ]
        }
      } : undefined,
      markPoint: isAlerting ? {
        data: [{ type: 'max', name: '峰值' }],
        symbol: 'pin',
        symbolSize: 30,
        itemStyle: { color }
      } : undefined
    };
  });

  const option = {
    title: {
      text: '节点与服务健康趋势',
      subtext: `数据来源：后端每分钟快照 · ${data.timeRange}`,
      left: 'center',
      textStyle: { fontSize: 14, color: '#333', fontWeight: '500' },
      subtextStyle: { fontSize: 12, color: '#999' }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(30,30,30,0.9)',
      borderColor: '#444',
      textStyle: { color: '#fff', fontSize: 13 },
      formatter(params) {
        if (!params.length) return '';
        let result = `<div style="margin-bottom:6px;color:#aaa">${params[0].axisValue}</div>`;
        params.forEach(item => {
          const desc = metricDesc(item.seriesName);
          const trend = data.trends.find(t => t.metricName === item.seriesName);
          const unit = trend?.unit || '';
          const isAlert = ['离线节点', '异常服务', '未处理告警'].includes(item.seriesName) && item.value > 0;
          result += `<div style="margin:3px 0">
            <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${item.color};margin-right:6px"></span>
            <span>${item.seriesName}</span>：
            <strong style="color:${isAlert ? '#FF4D4F' : '#fff'}">${item.value}${unit}</strong>
            <span style="color:#888;font-size:11px;margin-left:4px">${desc}</span>
          </div>`;
        });
        return result;
      }
    },
    legend: {
      data: data.trends.map(t => t.metricName),
      bottom: 0,
      textStyle: { color: '#666', fontSize: 12 },
      itemGap: 20
    },
    grid: { top: '18%', left: '4%', right: '4%', bottom: '12%', containLabel: true },
    xAxis: {
      type: 'category',
      data: xAxisData,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#e0e0e0' } },
      axisLabel: { color: '#999', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f0f0' } },
      axisLabel: { color: '#999', fontSize: 11 }
    },
    series
  };

  chart.setOption(option, true);
  chart.resize();
}

function isAlertingTrend(trend) {
  return ALERT_METRIC_NAMES.has(trend.metricName) && Number(trend.currentValue) > 0;
}

onMounted(() => {
  window.addEventListener('resize', handleWindowResize);
  loadTrendData(selectedHours.value);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleWindowResize);
  if (chart) { chart.dispose(); chart = null; }
});
</script>

<style scoped>
.trends-section {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  margin-top: 2rem;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

.trends-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.trends-header h3 {
  margin: 0 0 4px 0;
  font-size: 1.1rem;
  color: #333;
  font-weight: 500;
}

.trends-subtitle {
  margin: 0;
  font-size: 0.82rem;
  color: #999;
}

.time-range-label {
  font-size: 0.8rem;
  color: #aaa;
  white-space: nowrap;
  margin-top: 4px;
}

/* 时间选择器 */
.time-range-selector {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.range-button {
  padding: 4px 14px;
  border: 1px solid #d9d9d9;
  background: white;
  color: #666;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.2s;
}
.range-button:hover { border-color: #40a9ff; color: #40a9ff; }
.range-button.active { background: #1890ff; border-color: #1890ff; color: white; }

/* 异常横幅 */
.anomaly-banner {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 6px;
  padding: 10px 16px;
  color: #cf1322;
  font-size: 0.875rem;
  font-weight: 500;
  margin-bottom: 1rem;
}

/* 当前值卡片 */
.current-values {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 1.25rem;
}

.metric-card {
  flex: 1;
  min-width: 120px;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.card-good    { background: #f6ffed; border-color: #b7eb8f; }
.card-danger  { background: #fff2f0; border-color: #ffccc7; }
.card-neutral { background: #f5f5f5; border-color: #e8e8e8; }

.metric-label {
  font-size: 0.75rem;
  color: #666;
  font-weight: 500;
}
.metric-value {
  font-size: 1.4rem;
  font-weight: 700;
  color: #111;
  line-height: 1;
}
.metric-unit { font-size: 0.75rem; color: #999; margin-left: 2px; }
.metric-desc { font-size: 0.7rem; color: #aaa; margin-top: 2px; }

/* 图表 */
.chart-container {
  width: 100%;
  height: 360px;
  margin-top: 0.5rem;
}

.chart-legend-note {
  margin-top: 10px;
  font-size: 0.78rem;
  color: #aaa;
  text-align: center;
}

/* 加载 */
.loading-container { padding: 2rem; text-align: center; }
.loading-text { color: #999; margin-bottom: 1rem; }
.skeleton-chart {
  width: 100%;
  height: 300px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: loading 1.5s infinite;
  border-radius: 4px;
}
@keyframes loading {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 错误 */
.error-container {
  padding: 2rem;
  text-align: center;
  background: #fff5f5;
  border-radius: 4px;
  border: 1px solid #ffcccb;
}
.error-message { color: #ff4d4f; margin-bottom: 1rem; }
.retry-button {
  padding: 0.5rem 1.5rem;
  background: #ff4d4f;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}
.retry-button:hover { background: #ff7875; }

/* 无数据 */
.no-data { padding: 2rem; text-align: center; color: #999; }
.no-data-hint { font-size: 0.8rem; color: #bbb; margin-top: 6px; }
</style>
