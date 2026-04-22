<template>
  <section class="trends-section">
    <h3>系统趋势分析</h3>
    
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

    <!-- 图表 - 始终渲染，通过 v-show 控制显示 -->
    <div v-show="hasData && !isLoading && !error" ref="chartContainer" class="chart-container"></div>

    <!-- 无数据状态 -->
    <div v-show="!hasData && !isLoading && !error" class="no-data">
      <p>暂无趋势数据，请稍候</p>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { fetchTrends } from '../services/api.js';

// 时间范围选项
const timeRanges = [
  { hours: 0.25, label: '15分钟' },
  { hours: 0.5, label: '30分钟' },
  { hours: 1, label: '1小时' },
  { hours: 2, label: '2小时' },
  { hours: 4, label: '4小时' },
  { hours: 24, label: '24小时' }
];

const selectedHours = ref(1);
const isLoading = ref(false);
const error = ref('');
const chartContainer = ref(null);
const trendsData = ref(null);
let chart = null;
const handleWindowResize = () => {
  if (chart) {
    chart.resize();
  }
};

const hasData = computed(() => trendsData.value && trendsData.value.trends.length > 0);

// 颜色配置（与status.js保持一致）
const colors = {
  online: '#52C41A',      // 健康 - 绿色
  offline: '#8C8C8C',     // 离线 - 灰色
  warning: '#FAAD14',     // 警告 - 橙色
  critical: '#FF4D4F',    // 严重 - 红色
  blue: '#1890FF',        // 蓝色 - 用于识别服务
  purple: '#722ED1'       // 紫色 - 用于未处理告警
};

// 加载趋势数据
async function loadTrendData(hours) {
  selectedHours.value = hours;
  isLoading.value = true;
  error.value = '';
  
  try {
    // 直接传递 hours，不要用 Math.ceil 以保持低于1小时的校准
    const data = await fetchTrends(hours);
    trendsData.value = data;
    isLoading.value = false;

    if (!data?.trends?.length) {
      return;
    }

    // 等待DOM更新完成后再初始化图表，避免 display:none 时计算到 0 尺寸
    await nextTick();
    renderChart(data);
  } catch (err) {
    error.value = err.message || '加载趋势数据失败';
    console.error('Trends load error:', err);
  } finally {
    isLoading.value = false;
  }
}

// 绘制图表
function renderChart(data) {
  const container = chartContainer.value;
  if (!data?.trends?.length || !container) {
    return;
  }

  if (!chart) {
    chart = echarts.init(container);
  } else if (chart.getDom() !== container) {
    chart.dispose();
    chart = echarts.init(container);
  }

  // 准备图表系列数据
  const series = data.trends.map((trend) => {
    const metricColor = getColorForMetric(trend.metricName);
    return {
      name: trend.metricName,
      type: 'line',
      data: trend.values.map(v => (typeof v === 'number' ? v : parseFloat(v))),
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: {
        width: 2,
        color: metricColor
      },
      itemStyle: {
        color: metricColor
      }
    };
  });

  // 准备X轴标签（时间）
  const xAxisData = data.trends[0]?.timestamps.map(ts => {
    const date = new Date(ts);
    return date.toLocaleTimeString('zh-CN', { 
      hour: '2-digit', 
      minute: '2-digit' 
    });
  }) || [];

  const option = {
    title: {
      text: `${data.timeRange} 的系统趋势`,
      left: 'center',
      textStyle: {
        fontSize: 14,
        color: '#333'
      }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(50, 50, 50, 0.9)',
      borderColor: '#333',
      textStyle: {
        color: '#fff'
      },
      formatter: (params) => {
        if (!params.length) return '';
        
        let result = params[0].axisValue + '<br>';
        params.forEach(item => {
          result += `<span style="color:${item.color}">● ${item.seriesName}: ${item.value}${getTrendUnit(item.seriesName, data.trends)}</span><br>`;
        });
        return result;
      }
    },
    legend: {
      data: data.trends.map(t => t.metricName),
      bottom: 0,
      textStyle: {
        color: '#666'
      }
    },
    grid: {
      top: '15%',
      left: '5%',
      right: '5%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: xAxisData,
      boundaryGap: false,
      axisLine: {
        lineStyle: {
          color: '#e0e0e0'
        }
      },
      axisLabel: {
        color: '#999'
      }
    },
    yAxis: {
      type: 'value',
      splitLine: {
        lineStyle: {
          color: '#f0f0f0'
        }
      },
      axisLine: {
        lineStyle: {
          color: '#e0e0e0'
        }
      },
      axisLabel: {
        color: '#999'
      }
    },
    series: series
  };

  chart.setOption(option, true);
  chart.resize();
}

// 根据指标名称获取颜色
function getColorForMetric(metricName) {
  const colorMap = {
    '在线节点': colors.online,
    '离线节点': colors.offline,
    '识别服务': colors.blue,
    '异常服务': colors.critical,
    '未处理告警': colors.purple
  };
  return colorMap[metricName] || colors.warning;
}

// 获取指标单位
function getTrendUnit(metricName, trends) {
  const trend = trends.find(t => t.metricName === metricName);
  return trend ? trend.unit : '';
}

// 生命周期
onMounted(() => {
  window.addEventListener('resize', handleWindowResize);
  loadTrendData(selectedHours.value);
});

// 清理
onUnmounted(() => {
  window.removeEventListener('resize', handleWindowResize);
  if (chart) {
    chart.dispose();
    chart = null;
  }
});
</script>

<style scoped>
.trends-section {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  margin-top: 2rem;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.trends-section > h3 {
  margin: 0 0 1.5rem 0;
  font-size: 1.1rem;
  color: #333;
  font-weight: 500;
}

.time-range-selector {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
}

.range-button {
  padding: 0.5rem 1rem;
  border: 1px solid #d9d9d9;
  background: white;
  color: #666;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.range-button:hover {
  border-color: #40a9ff;
  color: #40a9ff;
}

.range-button.active {
  background: #1890ff;
  border-color: #1890ff;
  color: white;
}

.chart-container {
  width: 100%;
  height: 400px;
  margin-top: 1rem;
}

.loading-container {
  padding: 2rem;
  text-align: center;
}

.loading-text {
  color: #999;
  margin-bottom: 1rem;
}

.skeleton-chart {
  width: 100%;
  height: 300px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: loading 1.5s infinite;
  border-radius: 4px;
}

@keyframes loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.error-container {
  padding: 2rem;
  text-align: center;
  background: #fff5f5;
  border-radius: 4px;
  border: 1px solid #ffcccb;
}

.error-message {
  color: #ff4d4f;
  margin-bottom: 1rem;
}

.retry-button {
  padding: 0.5rem 1.5rem;
  background: #ff4d4f;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background 0.2s;
}

.retry-button:hover {
  background: #ff7875;
}

.no-data {
  padding: 2rem;
  text-align: center;
  color: #999;
}
</style>
