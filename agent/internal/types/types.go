package types

type DiscoveredService struct {
	ServiceName string `json:"serviceName"`
	ServiceType string `json:"serviceType"`
	Port        int    `json:"port"`
	ProcessName string `json:"processName"`
	MetricsPath string `json:"metricsPath,omitempty"`
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
	NodeName string `json:"nodeName"`
	Status   string `json:"status"`
}
