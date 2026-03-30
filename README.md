# 课题二一体化监控平台

本项目是课题二的初版实现，目标是在不污染宿主机环境的前提下，利用容器化方式完成开发、运行和测试，并打通以下最小可演示闭环：

- 模拟 Linux 节点容器启动
- 节点内置 Go Agent 扫描服务并向平台注册
- Spring Boot 后端保存节点、服务和心跳数据
- Vue 3 前端展示节点和服务清单
- Prometheus + Grafana 展示指标
- SkyWalking 展示示例服务调用链

## 目录说明

- `backend/`：Spring Boot 管理后台
- `frontend/`：Vue 3 统一门户
- `agent/`：Go 统一 Agent
- `demo-apps/sample-service/`：接入 Actuator/Micrometer 和 SkyWalking 的示例应用
- `demo-nodes/`：模拟 Linux 节点镜像
- `infra/`：Prometheus、Grafana 等观测组件配置
- `tests/`：容器化冒烟验证脚本

## 推荐使用方式

项目采用“源码挂载 + 容器内运行”的方式：

- 宿主机只需要安装 `Docker` 与 `docker compose`
- 不需要在宿主机安装 `MySQL`、`Prometheus`、`Grafana`、`SkyWalking`
- 开发时通过挂载源码实现前后端热更新

## 快速开始

1. 复制环境变量模板：

   ```bash
   cp .env.example .env
   ```

2. 启动基础开发环境：

   ```bash
   DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0 docker compose up -d mysql backend frontend
   ```

3. 启动完整演示环境：

   ```bash
   DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0 docker compose --profile observability --profile nodes up -d --build
   ```

4. 访问入口：

- 前端：`http://localhost:15173`
- 后端：`http://localhost:18081/api`
- Grafana：`http://localhost:13000`
- Prometheus：`http://localhost:19090`
- SkyWalking UI：`http://localhost:18082`

## 常用命令

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f app-node
docker compose logs -f middleware-node
docker compose down -v
```

## 测试

```bash
bash tests/smoke-test.sh
```
