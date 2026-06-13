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

FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    supervisor procps iproute2 curl ca-certificates nginx redis-server default-mysql-server \
    && rm -rf /var/lib/apt/lists/*

COPY --from=agent-builder /out/monitor-agent /usr/local/bin/monitor-agent
COPY --from=node-exporter /bin/node_exporter /usr/local/bin/node_exporter
COPY demo-nodes/middleware-node/redis.conf /etc/redis/redis.conf
COPY demo-nodes/middleware-node/nginx-default.conf /etc/nginx/sites-available/default
COPY demo-nodes/middleware-node/start-middleware.sh /usr/local/bin/start-middleware.sh
COPY demo-nodes/middleware-node/supervisord.conf /etc/supervisor/conf.d/middleware-node.conf

RUN chmod +x /usr/local/bin/start-middleware.sh && \
    mkdir -p /run/mysqld /var/log/supervisor && \
    chown -R mysql:mysql /run/mysqld /var/lib/mysql

EXPOSE 80 3306 6379 9100

CMD ["/usr/local/bin/start-middleware.sh"]
