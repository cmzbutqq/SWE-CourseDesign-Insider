package discovery

import (
	"testing"

	"github.com/stretchr/testify/require"

	"scut-monitoring/agent/internal/types"
)

func TestDiscoverShouldRecognizeSupportedServices(t *testing.T) {
	processOutput := `
101 java /usr/bin/java -jar /opt/sample-service.jar
102 node_exporter /usr/local/bin/node_exporter
103 nginx nginx: master process nginx -g daemon off;
104 mysqld /usr/sbin/mysqld
105 redis-server /usr/bin/redis-server *:6379
`
	portOutput := `
LISTEN 0 4096 *:8081 *:* users:(("java",pid=101,fd=123))
LISTEN 0 4096 *:9100 *:* users:(("node_exporter",pid=102,fd=4))
LISTEN 0 4096 *:80 *:* users:(("nginx",pid=103,fd=6))
LISTEN 0 4096 *:3306 *:* users:(("mysqld",pid=104,fd=10))
LISTEN 0 4096 *:6379 *:* users:(("redis-server",pid=105,fd=7))
`

	services := Discoverer{
		ProcessOutput: processOutput,
		PortOutput:    portOutput,
	}.Discover()

	require.Len(t, services, 5)
	require.Contains(t, services, types.DiscoveredService{
		ServiceName: "sample-service",
		ServiceType: "SPRING_BOOT",
		Port:        8081,
		ProcessName: "java",
		MetricsPath: "/actuator/prometheus",
	})
	require.Contains(t, services, types.DiscoveredService{
		ServiceName: "mysql",
		ServiceType: "MYSQL",
		Port:        3306,
		ProcessName: "mysqld",
	})
}

func TestDiscoverShouldDeduplicateRepeatedProcesses(t *testing.T) {
	processOutput := `
103 nginx nginx: master process nginx -g daemon off;
104 nginx nginx: worker process
`
	portOutput := `LISTEN 0 4096 *:80 *:* users:(("nginx",pid=103,fd=6))`

	services := Discoverer{
		ProcessOutput: processOutput,
		PortOutput:    portOutput,
	}.Discover()

	require.Len(t, services, 1)
	require.Equal(t, "NGINX", services[0].ServiceType)
	require.Equal(t, 80, services[0].Port)
}
