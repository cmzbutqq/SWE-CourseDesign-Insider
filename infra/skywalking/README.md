# SkyWalking 说明

SkyWalking 在 `docker-compose.yml` 中直接使用官方 `OAP` 与 `UI` 镜像启动：

- `skywalking-oap`：接收 Java Agent 上报
- `skywalking-ui`：用于查看链路和拓扑

`app-node` 中的示例 Spring Boot 服务会在启动时通过 `-javaagent` 参数接入 SkyWalking。
