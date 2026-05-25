package metrics

import (
	"bufio"
	"os"
	"os/exec"
	"scut-monitoring/agent/internal/types"
	"strconv"
	"strings"
	"time"
)

func Collect() types.HeartbeatPayload {
	payload := types.HeartbeatPayload{
		Status: "ONLINE",
	}

	if cpu := collectCPU(); cpu >= 0 {
		payload.CPUUsage = &cpu
	}

	if usage, total, used := collectMemory(); usage >= 0 {
		payload.MemoryUsage = &usage
		payload.MemoryTotalMb = &total
		payload.MemoryUsedMb = &used
	}

	if usage, total, used := collectDisk(); usage >= 0 {
		payload.DiskUsage = &usage
		payload.DiskTotalGb = &total
		payload.DiskUsedGb = &used
	}

	if rx, tx := collectNetwork(); rx >= 0 {
		payload.NetworkRxMbps = &rx
		payload.NetworkTxMbps = &tx
	}

	return payload
}

func collectCPU() float64 {
	idle1, total1 := readCPUStats()
	time.Sleep(100 * time.Millisecond)
	idle2, total2 := readCPUStats()

	if total2-total1 == 0 {
		return -1
	}
	return (1.0 - float64(idle2-idle1)/float64(total2-total1)) * 100.0
}

func readCPUStats() (idle, total uint64) {
	file, err := os.Open("/proc/stat")
	if err != nil {
		return 0, 0
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	if !scanner.Scan() {
		return 0, 0
	}
	fields := strings.Fields(scanner.Text())
	if len(fields) < 5 || fields[0] != "cpu" {
		return 0, 0
	}

	var sum uint64
	for i, f := range fields[1:] {
		v, err := strconv.ParseUint(f, 10, 64)
		if err != nil {
			continue
		}
		sum += v
		if i == 3 {
			idle = v
		}
	}
	return idle, sum
}

func collectMemory() (usagePercent float64, totalMb, usedMb int64) {
	file, err := os.Open("/proc/meminfo")
	if err != nil {
		return -1, -1, -1
	}
	defer file.Close()

	var memTotal, memAvailable int64
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		val, err := strconv.ParseInt(fields[1], 10, 64)
		if err != nil {
			continue
		}
		if strings.HasPrefix(line, "MemTotal:") {
			memTotal = val
		} else if strings.HasPrefix(line, "MemAvailable:") {
			memAvailable = val
		}
	}

	if memTotal == 0 {
		return -1, -1, -1
	}

	memUsedKB := memTotal - memAvailable
	return float64(memUsedKB) / float64(memTotal) * 100.0, memTotal / 1024, memUsedKB / 1024
}

func collectDisk() (usagePercent float64, totalGb, usedGb int64) {
	output, err := exec.Command("df", "-B1", "/").Output()
	if err != nil {
		return -1, -1, -1
	}

	scanner := bufio.NewScanner(strings.NewReader(string(output)))
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) < 6 || fields[0] == "Filesystem" {
			continue
		}
		total, err1 := strconv.ParseInt(fields[1], 10, 64)
		used, err2 := strconv.ParseInt(fields[2], 10, 64)
		if err1 != nil || err2 != nil || total == 0 {
			continue
		}
		return float64(used) / float64(total) * 100.0, total / (1024 * 1024 * 1024), used / (1024 * 1024 * 1024)
	}
	return -1, -1, -1
}

var (
	netDevPath          = "/proc/net/dev"
	nowFunc             = time.Now
	prevNetworkCounters map[string]networkCounter
	prevNetworkSampleAt time.Time
)

type networkCounter struct {
	rxBytes uint64
	txBytes uint64
}

func collectNetwork() (rxMbps, txMbps float64) {
	file, err := os.Open(netDevPath)
	if err != nil {
		return -1, -1
	}
	defer file.Close()

	currentCounters := make(map[string]networkCounter)
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		colonIdx := strings.Index(line, ":")
		if colonIdx < 0 {
			continue
		}
		interfaceName := strings.TrimSpace(line[:colonIdx])
		if interfaceName == "lo" {
			continue
		}
		fields := strings.Fields(line[colonIdx+1:])
		if len(fields) < 10 {
			continue
		}
		rx, err1 := strconv.ParseUint(fields[0], 10, 64)
		tx, err2 := strconv.ParseUint(fields[8], 10, 64)
		if err1 != nil || err2 != nil {
			continue
		}
		currentCounters[interfaceName] = networkCounter{rxBytes: rx, txBytes: tx}
	}

	sampledAt := nowFunc()
	if prevNetworkSampleAt.IsZero() {
		prevNetworkCounters = currentCounters
		prevNetworkSampleAt = sampledAt
		return -1, -1
	}

	elapsedSeconds := sampledAt.Sub(prevNetworkSampleAt).Seconds()
	if elapsedSeconds <= 0 || networkBaselineChanged(prevNetworkCounters, currentCounters) {
		prevNetworkCounters = currentCounters
		prevNetworkSampleAt = sampledAt
		return -1, -1
	}

	var rxDelta, txDelta uint64
	for interfaceName, current := range currentCounters {
		previous := prevNetworkCounters[interfaceName]
		rxDelta += current.rxBytes - previous.rxBytes
		txDelta += current.txBytes - previous.txBytes
	}

	rxMbps = float64(rxDelta) * 8.0 / 1000000.0 / elapsedSeconds
	txMbps = float64(txDelta) * 8.0 / 1000000.0 / elapsedSeconds
	prevNetworkCounters = currentCounters
	prevNetworkSampleAt = sampledAt
	return rxMbps, txMbps
}

func networkBaselineChanged(previous, current map[string]networkCounter) bool {
	if len(previous) != len(current) {
		return true
	}
	for interfaceName, currentCounter := range current {
		previousCounter, ok := previous[interfaceName]
		if !ok || currentCounter.rxBytes < previousCounter.rxBytes || currentCounter.txBytes < previousCounter.txBytes {
			return true
		}
	}
	return false
}
