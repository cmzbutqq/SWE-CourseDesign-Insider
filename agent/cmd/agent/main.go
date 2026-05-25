package main

import (
	"log"
	"time"

	"scut-monitoring/agent/internal/config"
	"scut-monitoring/agent/internal/discovery"
	"scut-monitoring/agent/internal/httpclient"
	"scut-monitoring/agent/internal/system"
	"scut-monitoring/agent/internal/types"
)

func main() {
	cfg := config.Load()
	client := httpclient.New(cfg.ServerURL)

	register := func() {
		info := system.Collect()
		services := discovery.Discoverer{
			ProcessOutput: system.RunCommand("sh", "-c", "ps -eo pid,comm,args --no-headers"),
			PortOutput:    system.RunCommand("sh", "-c", "ss -ltnp"),
		}.Discover()
		if len(services) == 0 {
			log.Printf("no services discovered on %s; registering node without services", cfg.NodeName)
		}

		payload := types.RegisterPayload{
			NodeName:     cfg.NodeName,
			Hostname:     info.Hostname,
			IPAddress:    info.IP,
			OSName:       info.OS,
			AgentVersion: cfg.AgentVersion,
			Services:     services,
		}

		if err := client.Post("/agents/register", payload); err != nil {
			log.Printf("register failed: %v", err)
		} else {
			log.Printf("registered node %s with %d services", cfg.NodeName, len(services))
		}
	}

	heartbeat := func() {
		payload := types.HeartbeatPayload{
			NodeName: cfg.NodeName,
			Status:   "ONLINE",
		}
		if err := client.Post("/agents/heartbeat", payload); err != nil {
			log.Printf("heartbeat failed: %v", err)
		}
	}

	register()
	heartbeat()

	registerTicker := time.NewTicker(cfg.RegisterInterval)
	heartbeatTicker := time.NewTicker(cfg.HeartbeatInterval)
	defer registerTicker.Stop()
	defer heartbeatTicker.Stop()

	for {
		select {
		case <-registerTicker.C:
			register()
		case <-heartbeatTicker.C:
			heartbeat()
		}
	}
}
