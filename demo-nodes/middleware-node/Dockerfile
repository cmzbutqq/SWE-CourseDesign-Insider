ARG GOPROXY=https://goproxy.cn,direct
FROM golang:1.23 AS agent-builder
ARG GOPROXY
ENV GOPROXY=${GOPROXY}
WORKDIR /src/agent
COPY agent/go.mod ./
RUN go mod download
COPY agent/ ./
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/monitor-agent ./cmd/agent

FROM prom/node-exporter:v1.8.2 AS node-exporter
FROM nginx/nginx-prometheus-exporter:1.3.0 AS nginx-exporter
FROM oliver006/redis_exporter:v1.67.0 AS redis-exporter

ARG SKYWALKING_AGENT_VERSION=9.4.0
FROM eclipse-temurin:21-jre-jammy AS skywalking-agent-source
ARG SKYWALKING_AGENT_VERSION
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/skywalking \
    && curl -fsSL --retry 5 --retry-all-errors --retry-delay 2 \
    "https://archive.apache.org/dist/skywalking/java-agent/${SKYWALKING_AGENT_VERSION}/apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz" \
    -o /tmp/skywalking.tgz \
    && tar -xzf /tmp/skywalking.tgz -C /opt/skywalking --strip-components=1 \
    && rm -f /tmp/skywalking.tgz

ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
FROM maven:3.9-eclipse-temurin-21 AS middleware-builder
ARG MAVEN_MIRROR_URL
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
WORKDIR /src/demo-apps/middleware-service
COPY infra/maven/mvn-with-mirror.sh /usr/local/bin/mvn-with-mirror.sh
COPY demo-apps/middleware-service/pom.xml ./
RUN sh /usr/local/bin/mvn-with-mirror.sh -q -DskipTests dependency:go-offline
COPY demo-apps/middleware-service/ ./
RUN sh /usr/local/bin/mvn-with-mirror.sh -q -DskipTests package

FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update -o Acquire::Retries=5 && apt-get install -y --fix-missing --no-install-recommends \
    supervisor procps iproute2 curl ca-certificates nginx redis-server default-mysql-server openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY --from=agent-builder /out/monitor-agent /usr/local/bin/monitor-agent
COPY --from=node-exporter /bin/node_exporter /usr/local/bin/node_exporter
COPY --from=nginx-exporter /usr/bin/nginx-prometheus-exporter /usr/local/bin/nginx-prometheus-exporter
COPY --from=redis-exporter /redis_exporter /usr/local/bin/redis_exporter
COPY --from=middleware-builder /src/demo-apps/middleware-service/target/middleware-service-0.0.1-SNAPSHOT.jar /opt/app/middleware-service.jar
COPY --from=skywalking-agent-source /opt/skywalking /opt/skywalking
COPY demo-nodes/middleware-node/redis.conf /etc/redis/redis.conf
COPY demo-nodes/middleware-node/nginx-default.conf /etc/nginx/sites-available/default
COPY demo-nodes/middleware-node/start-middleware.sh /usr/local/bin/start-middleware.sh
COPY demo-nodes/middleware-node/supervisord.conf /etc/supervisor/conf.d/middleware-node.conf

RUN chmod +x /usr/local/bin/start-middleware.sh && \
    mkdir -p /run/mysqld /var/log/supervisor && \
    chown -R mysql:mysql /run/mysqld /var/lib/mysql

EXPOSE 80 3306 6379 8082 9100 9113 9121

CMD ["/usr/local/bin/start-middleware.sh"]
