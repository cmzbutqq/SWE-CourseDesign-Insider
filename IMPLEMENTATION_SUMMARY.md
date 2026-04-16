# 首页优化实现总结

## ✅ 已完成的功能

### 第一层：KPI 状态卡片区

**新建文件：** [frontend/src/components/KPICards.vue](frontend/src/components/KPICards.vue)

实现了 5 张核心指标卡片：
- **在线节点**：显示在线数 / 总数，状态色根据离线情况动态变化
- **异常节点**：展示告警或高负载节点数
- **已识别服务**：服务总数（蓝色中性色）
- **异常服务**：故障服务数，红色高亮
- **未处理告警**：系统告警统计

**特点：**
- 左边框色对应状态色（绿、橙、红、灰）
- 状态徽章实时显示"运行正常"、"需关注"、"存在异常"等文案
- 卡片悬停有动画效果
- 显示最后更新时间

### 第二层：异常前置聚焦区

**新建文件：** [frontend/src/components/AnomalyPanel.vue](frontend/src/components/AnomalyPanel.vue)

**仅当异常存在时动态显示**：
- **异常节点快览**：列出离线/高负载节点，显示异常原因和持续时长
- **异常服务快览**：列出不可用服务及所属节点
- 每条异常条目有直接的详情跳转链接

**设计特点：**
- 红色边框和浅红背景，视觉高度突出
- 正常状态下完全隐藏（无空状态噪音）
- 异常项目从上到下按持续时长排序
- 响应式布局

### 第三层：全局状态地图 / 节点服务列表

**改造文件：** [frontend/src/pages/OverviewPage.vue](frontend/src/pages/OverviewPage.vue)

- 下移至第二屏，不影响首屏体验
- 保留了快捷链接（Prometheus、Grafana、SkyWalking）
- 新增两个导航按钮直达"所有节点"和"所有服务"详情页

---

## 📊 后端数据结构

### DTO 类 (新建)

| 文件 | 功能 |
|-----|------|
| [CounterGroupDTO.java](backend/src/main/java/com/scut/monitoring/backend/dto/CounterGroupDTO.java) | 数值分组统计（total, online, offline, warning, healthy, abnormal） |
| [AnomalyNodeDTO.java](backend/src/main/java/com/scut/monitoring/backend/dto/AnomalyNodeDTO.java) | 异常节点详情（id, nodeName, status, reason, duration 等） |
| [AnomalyServiceDTO.java](backend/src/main/java/com/scut/monitoring/backend/dto/AnomalyServiceDTO.java) | 异常服务详情（id, serviceName, status, errorType, nodeName） |
| [AnomaliesDTO.java](backend/src/main/java/com/scut/monitoring/backend/dto/AnomaliesDTO.java) | 聚合异常信息（nodes[], services[]） |

### 改造现有 API

**改造文件：** [OverviewResponse.java](backend/src/main/java/com/scut/monitoring/backend/dto/OverviewResponse.java)

新结构：
```java
record OverviewResponse(
    CounterGroupDTO nodes,              // 节点统计
    CounterGroupDTO services,           // 服务统计
    long unresolvedAlerts,              // 未处理告警数
    AnomaliesDTO anomalies,             // 异常列表
    List<String> quickLinks
)
```

**改造文件：** [NodeRegistryService.java](backend/src/main/java/com/scut/monitoring/backend/service/NodeRegistryService.java)

新增方法：
- `buildAnomalies()`：聚合离线节点和故障服务
- `calculateDurationSeconds()`：计算异常持续时长

---

## 🎨 状态色与文案规范

**新建文件：** [frontend/src/utils/status.js](frontend/src/utils/utils/status.js) - 统一状态色常量

```
✅ 正常    #52C41A（绿）   "运行正常"
⚠️ 警告    #FAAD14（橙）   "需关注"
❌ 异常    #FF4D4F（红）   "存在异常"
⊗ 离线    #8C8C8C（灰）   "失联"
```

---

## 🔄 实时更新机制

在 [OverviewPage.vue](frontend/src/pages/OverviewPage.vue) 中实现：

- **自动轮询**：15 秒间隔（POLL_INTERVAL = 15000ms）
- **轮询控制**：右上角 ▶/⏸ 按钮可开启/暂停
- **骨架屏**：首次加载时用骨架屏占位，防止布局抖动
- **自动更新时间**：卡片显示最后更新时间，每分钟刷新一次

**轮询工作流程：**
```
onMounted
  |
  ├─ 调用 load() 获取初始数据
  |
  └─ 如果 autoRefresh=true
      └─ startPolling() 每 15 秒调用一次 load()
      
onUnmounted
  └─ stopPolling() 清理定时器
```

---

## 📝 API 返回示例

```json
{
  "nodes": {
    "total": 12,
    "online": 10,
    "offline": 2,
    "warning": 1
  },
  "services": {
    "total": 47,
    "healthy": 44,
    "abnormal": 3
  },
  "unresolvedAlerts": 5,
  "anomalies": {
    "nodes": [
      {
        "id": 5,
        "nodeName": "node-offline-1",
        "status": "OFFLINE",
        "reason": "OFFLINE",
        "lastSeenAt": "2026-04-14T10:30:00Z",
        "cpuUsage": null,
        "memoryUsage": null,
        "durationSeconds": 3600
      }
    ],
    "services": []
  },
  "quickLinks": [
    "Prometheus: http://localhost:19090",
    "Grafana: http://localhost:13000",
    "SkyWalking: http://localhost:18082"
  ]
}
```

---

## 🚀 后续优化建议

### 短期（已就绪）

- [ ] 集成 Prometheus 查询，获取实时 CPU / 内存使用率
- [ ] 在 ManagedNode 添加 `cpuUsage`, `memoryUsage`, `loadAverage` 字段
- [ ] 实现服务健康检查接口，填充异常服务列表
- [ ] 添加告警数据聚合（从告警系统查询未处理告警）

### 中期

- [ ] 实现趋势迷你图（sparkline）展示近 7 天数据曲线
- [ ] 添加拓扑图或蜂巢图替代纯表格
- [ ] 支持"异常优先"排序，问题节点自动置顶
- [ ] WebSocket 实时推送替代轮询

### 长期

- [ ] 异常告警详情面板（点击进入查看根因分析）
- [ ] 自定义 KPI 卡片配置（用户可自由组合展示指标）
- [ ] 告警规则編辑与阈值配置

---

## 🔧 开发/测试步骤

1. **后端编译**
   ```bash
   cd backend
   mvn clean package
   ```

2. **启动后端**
   ```bash
   java -jar target/backend-xxx.jar
   ```

3. **验证 API**
   ```bash
   curl http://localhost:18081/api/overview
   ```

4. **前端开发**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

5. **浏览器访问**
   ```
   http://localhost:5173
   ```

---

## 📁 文件清单

**后端新增/改造：**
- ✅ `backend/src/main/java/com/scut/monitoring/backend/dto/CounterGroupDTO.java` (新建)
- ✅ `backend/src/main/java/com/scut/monitoring/backend/dto/AnomalyNodeDTO.java` (新建)
- ✅ `backend/src/main/java/com/scut/monitoring/backend/dto/AnomalyServiceDTO.java` (新建)
- ✅ `backend/src/main/java/com/scut/monitoring/backend/dto/AnomaliesDTO.java` (新建)
- ✅ `backend/src/main/java/com/scut/monitoring/backend/dto/OverviewResponse.java` (改造)
- ✅ `backend/src/main/java/com/scut/monitoring/backend/service/NodeRegistryService.java` (改造)

**前端新增/改造：**
- ✅ `frontend/src/utils/status.js` (新建)
- ✅ `frontend/src/components/KPICards.vue` (新建)
- ✅ `frontend/src/components/AnomalyPanel.vue` (新建)
- ✅ `frontend/src/pages/OverviewPage.vue` (完全改造)

---

**实现完成时间：** 2026-04-14  
**优先级完成度：** 优先级 100% ✅ | 其次级 0% | 最后级 0%
