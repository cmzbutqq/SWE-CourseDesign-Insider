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

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends \
    supervisor procps iproute2 curl wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/app
COPY --from=agent-builder /out/monitor-agent /usr/local/bin/monitor-agent
COPY --from=sample-builder /src/demo-apps/sample-service/target/sample-service-0.0.1-SNAPSHOT.jar /opt/app/sample-service.jar
COPY --from=node-exporter /bin/node_exporter /usr/local/bin/node_exporter
COPY demo-nodes/app-node/supervisord.conf /etc/supervisor/conf.d/app-node.conf

RUN mkdir -p /opt/skywalking && \
    curl -fsSL --retry 3 https://archive.apache.org/dist/skywalking/java-agent/9.4.0/apache-skywalking-java-agent-9.4.0.tgz -o /tmp/skywalking.tgz && \
    tar -xzf /tmp/skywalking.tgz -C /opt/skywalking --strip-components=1 && \
    rm -f /tmp/skywalking.tgz

EXPOSE 8081 9100

CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/conf.d/app-node.conf"]
