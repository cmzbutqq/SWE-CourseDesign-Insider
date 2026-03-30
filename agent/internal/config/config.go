package config

import (
	"os"
	"time"
)

type Config struct {
	NodeName          string
	ServerURL         string
	AgentVersion      string
	RegisterInterval  time.Duration
	HeartbeatInterval time.Duration
}

func Load() Config {
	return Config{
		NodeName:          env("AGENT_NODE_NAME", "unknown-node"),
		ServerURL:         env("AGENT_SERVER_URL", "http://localhost:18081/api"),
		AgentVersion:      env("AGENT_VERSION", "0.1.0"),
		RegisterInterval:  durationEnv("AGENT_REGISTER_INTERVAL", 15*time.Second),
		HeartbeatInterval: durationEnv("AGENT_HEARTBEAT_INTERVAL", 10*time.Second),
	}
}

func env(key, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}

func durationEnv(key string, fallback time.Duration) time.Duration {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	duration, err := time.ParseDuration(value)
	if err != nil {
		return fallback
	}
	return duration
}
