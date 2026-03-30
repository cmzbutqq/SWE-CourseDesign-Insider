package system

import (
	"bytes"
	"os"
	"os/exec"
	"strings"
)

type Info struct {
	Hostname string
	IP       string
	OS       string
}

func Collect() Info {
	hostname, _ := os.Hostname()
	return Info{
		Hostname: hostname,
		IP:       detectIP(),
		OS:       detectOS(),
	}
}

func detectIP() string {
	output := run("sh", "-c", "hostname -i | awk '{print $1}'")
	if output == "" {
		return "127.0.0.1"
	}
	return output
}

func detectOS() string {
	content, err := os.ReadFile("/etc/os-release")
	if err != nil {
		return "linux"
	}
	for _, line := range strings.Split(string(content), "\n") {
		if strings.HasPrefix(line, "PRETTY_NAME=") {
			return strings.Trim(line[len("PRETTY_NAME="):], "\"")
		}
	}
	return "linux"
}

func RunCommand(name string, args ...string) string {
	return run(name, args...)
}

func run(name string, args ...string) string {
	cmd := exec.Command(name, args...)
	var stdout bytes.Buffer
	cmd.Stdout = &stdout
	if err := cmd.Run(); err != nil {
		return ""
	}
	return strings.TrimSpace(stdout.String())
}
