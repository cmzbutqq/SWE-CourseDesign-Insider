package discovery

import (
	"regexp"
	"sort"
	"strconv"
	"strings"

	"scut-monitoring/agent/internal/types"
)

type Process struct {
	PID     string
	Command string
	Args    string
}

type Discoverer struct {
	ProcessOutput string
	PortOutput    string
}

var (
	portPattern = regexp.MustCompile(`:(\d+)`)
	pidPattern  = regexp.MustCompile(`pid=(\d+)`)
)

func (d Discoverer) Discover() []types.DiscoveredService {
	processes := parseProcesses(d.ProcessOutput)
	ports := parsePorts(d.PortOutput)
	services := make([]types.DiscoveredService, 0)

	for _, process := range processes {
		service, ok := classify(process, ports)
		if ok {
			services = append(services, service)
		}
	}

	sort.Slice(services, func(i, j int) bool {
		if services[i].ServiceType == services[j].ServiceType {
			return services[i].ServiceName < services[j].ServiceName
		}
		return services[i].ServiceType < services[j].ServiceType
	})
	return unique(services)
}

func parseProcesses(output string) []Process {
	lines := strings.Split(strings.TrimSpace(output), "\n")
	processes := make([]Process, 0, len(lines))
	for _, line := range lines {
		if strings.TrimSpace(line) == "" {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		command := fields[1]
		args := strings.Join(fields[2:], " ")
		processes = append(processes, Process{PID: fields[0], Command: command, Args: args})
	}
	return processes
}

func parsePorts(output string) map[string]int {
	ports := make(map[string]int)
	for _, line := range strings.Split(output, "\n") {
		portMatches := portPattern.FindStringSubmatch(line)
		pidMatches := pidPattern.FindStringSubmatch(line)
		if len(portMatches) < 2 || len(pidMatches) < 2 {
			continue
		}
		port, err := strconv.Atoi(portMatches[1])
		if err != nil {
			continue
		}
		ports[pidMatches[1]] = port
	}
	return ports
}

func classify(process Process, ports map[string]int) (types.DiscoveredService, bool) {
	command := strings.ToLower(process.Command)
	args := strings.ToLower(process.Args)
	switch {
	case command == "java" && strings.Contains(args, "middleware-service"):
		return types.DiscoveredService{
			ServiceName: "middleware-service",
			ServiceType: "SPRING_BOOT",
			Port:        pickPort(ports[process.PID], 8082),
			ProcessName: process.Command,
			MetricsPath: "/actuator/prometheus",
			MetricsPort: 8082,
		}, true
	case command == "java" && (strings.Contains(args, "sample-service") || strings.Contains(args, "spring")):
		return types.DiscoveredService{
			ServiceName: "sample-service",
			ServiceType: "SPRING_BOOT",
			Port:        pickPort(ports[process.PID], 8081),
			ProcessName: process.Command,
			MetricsPath: "/actuator/prometheus",
			MetricsPort: 8081,
		}, true
	case strings.Contains(command, "nginx"):
		return types.DiscoveredService{
			ServiceName: "nginx",
			ServiceType: "NGINX",
			Port:        pickPort(ports[process.PID], 80),
			ProcessName: process.Command,
			MetricsPath: "/metrics",
			MetricsPort: 9113,
		}, true
	case strings.Contains(command, "mysqld") || strings.Contains(command, "mariadbd"):
		return types.DiscoveredService{
			ServiceName: "mysql",
			ServiceType: "MYSQL",
			Port:        pickPort(ports[process.PID], 3306),
			ProcessName: process.Command,
		}, true
	case strings.Contains(command, "redis-server"):
		return types.DiscoveredService{
			ServiceName: "redis",
			ServiceType: "REDIS",
			Port:        pickPort(ports[process.PID], 6379),
			ProcessName: process.Command,
			MetricsPath: "/metrics",
			MetricsPort: 9121,
		}, true
	case strings.Contains(command, "node_exporter"):
		return types.DiscoveredService{
			ServiceName: "node-exporter",
			ServiceType: "NODE_EXPORTER",
			Port:        pickPort(ports[process.PID], 9100),
			ProcessName: process.Command,
			MetricsPath: "/metrics",
			MetricsPort: 9100,
		}, true
	default:
		return types.DiscoveredService{}, false
	}
}

func pickPort(port int, fallback int) int {
	if port == 0 {
		return fallback
	}
	return port
}

func unique(services []types.DiscoveredService) []types.DiscoveredService {
	seen := make(map[string]bool)
	result := make([]types.DiscoveredService, 0, len(services))
	for _, service := range services {
		key := service.ServiceType + ":" + strconv.Itoa(service.Port)
		if seen[key] {
			continue
		}
		seen[key] = true
		result = append(result, service)
	}
	return result
}
