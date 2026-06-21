package types

type DiscoveredService struct {
	ServiceName string `json:"serviceName"`
	ServiceType string `json:"serviceType"`
	Port        int    `json:"port"`
	ProcessName string `json:"processName"`
	MetricsPath string `json:"metricsPath,omitempty"`
	MetricsPort int    `json:"metricsPort,omitempty"`
}

type RegisterPayload struct {
	NodeName      string              `json:"nodeName"`
	Hostname      string              `json:"hostname"`
	IPAddress     string              `json:"ipAddress"`
	OSName        string              `json:"osName"`
	AgentVersion  string              `json:"agentVersion"`
	Services      []DiscoveredService `json:"services"`
}

type HeartbeatPayload struct {
	NodeName      string   `json:"nodeName"`
	Status        string   `json:"status"`
	CPUUsage      *float64 `json:"cpuUsage,omitempty"`
	MemoryUsage   *float64 `json:"memoryUsage,omitempty"`
	MemoryTotalMb *int64   `json:"memoryTotalMb,omitempty"`
	MemoryUsedMb  *int64   `json:"memoryUsedMb,omitempty"`
	DiskUsage     *float64 `json:"diskUsage,omitempty"`
	DiskTotalGb   *int64   `json:"diskTotalGb,omitempty"`
	DiskUsedGb    *int64   `json:"diskUsedGb,omitempty"`
	NetworkRxMbps *float64 `json:"networkRxMbps,omitempty"`
	NetworkTxMbps *float64 `json:"networkTxMbps,omitempty"`
}
