ARG GOPROXY=https://goproxy.cn,direct
FROM golang:1.23 AS agent-builder
ARG GOPROXY
ENV GOPROXY=${GOPROXY}
WORKDIR /src/agent
COPY agent/go.mod ./
RUN go mod download
COPY agent/ ./
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/monitor-agent ./cmd/agent

ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
FROM maven:3.9-eclipse-temurin-21 AS sample-builder
ARG MAVEN_MIRROR_URL
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
WORKDIR /src/demo-apps/sample-service
COPY infra/maven/mvn-with-mirror.sh /usr/local/bin/mvn-with-mirror.sh
COPY demo-apps/sample-service/pom.xml ./
RUN sh /usr/local/bin/mvn-with-mirror.sh -q -DskipTests dependency:go-offline
COPY demo-apps/sample-service/ ./
RUN sh /usr/local/bin/mvn-with-mirror.sh -q -DskipTests package

FROM prom/node-exporter:v1.8.2 AS node-exporter

ARG SKYWALKING_AGENT_VERSION=9.4.0
FROM eclipse-temurin:21-jre-jammy AS skywalking-agent-source
ARG SKYWALKING_AGENT_VERSION=9.4.0
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates tar \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL "https://archive.apache.org/dist/skywalking/java-agent/${SKYWALKING_AGENT_VERSION}/apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz" \
    -o /tmp/skywalking-agent.tgz \
    && mkdir -p /skywalking-agent \
    && tar -xzf /tmp/skywalking-agent.tgz -C /skywalking-agent --strip-components=1 \
    && rm /tmp/skywalking-agent.tgz

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends \
    supervisor procps iproute2 curl wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/app
COPY --from=agent-builder /out/monitor-agent /usr/local/bin/monitor-agent
COPY --from=sample-builder /src/demo-apps/sample-service/target/sample-service-0.0.1-SNAPSHOT.jar /opt/app/sample-service.jar
COPY --from=node-exporter /bin/node_exporter /usr/local/bin/node_exporter
COPY --from=skywalking-agent-source /skywalking-agent /opt/skywalking
COPY demo-nodes/app-node/supervisord.conf /etc/supervisor/conf.d/app-node.conf
COPY demo-nodes/app-node/traffic-generator.sh /usr/local/bin/traffic-generator.sh

RUN chmod +x /usr/local/bin/traffic-generator.sh

EXPOSE 8081 9100

CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/conf.d/app-node.conf"]
