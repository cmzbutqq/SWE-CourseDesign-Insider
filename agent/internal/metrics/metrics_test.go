package metrics

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestCollectNetworkCalculatesMbpsPerSecond(t *testing.T) {
	restore := useNetworkFixture(t, "eth0: 1000000 0 0 0 0 0 0 0 2000000 0 0 0 0 0 0 0\n", time.Unix(100, 0))
	defer restore()

	if rx, tx := collectNetwork(); rx >= 0 || tx >= 0 {
		t.Fatalf("first sample = (%v, %v), want unavailable baseline", rx, tx)
	}

	writeNetworkFixture(t, "eth0: 2250000 0 0 0 0 0 0 0 4500000 0 0 0 0 0 0 0\n")
	fakeNow = func() time.Time { return time.Unix(110, 0) }

	rx, tx := collectNetwork()
	if rx != 1.0 {
		t.Fatalf("rx Mbps = %v, want 1.0", rx)
	}
	if tx != 2.0 {
		t.Fatalf("tx Mbps = %v, want 2.0", tx)
	}
}

func TestCollectNetworkResetsBaselineWhenCountersRollBack(t *testing.T) {
	restore := useNetworkFixture(t, "eth0: 5000 0 0 0 0 0 0 0 7000 0 0 0 0 0 0 0\n", time.Unix(100, 0))
	defer restore()

	collectNetwork()

	writeNetworkFixture(t, "eth0: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0\n")
	fakeNow = func() time.Time { return time.Unix(110, 0) }

	if rx, tx := collectNetwork(); rx >= 0 || tx >= 0 {
		t.Fatalf("rollback sample = (%v, %v), want unavailable baseline reset", rx, tx)
	}

	writeNetworkFixture(t, "eth0: 2000 0 0 0 0 0 0 0 4500 0 0 0 0 0 0 0\n")
	fakeNow = func() time.Time { return time.Unix(120, 0) }

	rx, tx := collectNetwork()
	if rx != 0.0008 {
		t.Fatalf("rx Mbps after reset = %v, want 0.0008", rx)
	}
	if tx != 0.002 {
		t.Fatalf("tx Mbps after reset = %v, want 0.002", tx)
	}
}

func TestCollectNetworkResetsBaselineWhenAnyInterfaceRollsBack(t *testing.T) {
	restore := useNetworkFixture(t, ""+
		"eth0: 2000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0\n"+
		"eth1: 1000 0 0 0 0 0 0 0 1000 0 0 0 0 0 0 0\n", time.Unix(100, 0))
	defer restore()

	collectNetwork()

	writeNetworkFixture(t, ""+
		"eth0: 1000 0 0 0 0 0 0 0 1000 0 0 0 0 0 0 0\n"+
		"eth1: 4000 0 0 0 0 0 0 0 4000 0 0 0 0 0 0 0\n")
	fakeNow = func() time.Time { return time.Unix(110, 0) }

	if rx, tx := collectNetwork(); rx >= 0 || tx >= 0 {
		t.Fatalf("per-interface rollback sample = (%v, %v), want unavailable baseline reset", rx, tx)
	}
}

var networkFixturePath string
var fakeNow func() time.Time

func useNetworkFixture(t *testing.T, content string, now time.Time) func() {
	t.Helper()

	dir := t.TempDir()
	networkFixturePath = filepath.Join(dir, "dev")
	writeNetworkFixture(t, content)

	originalPath := netDevPath
	originalNow := nowFunc
	originalCounters := prevNetworkCounters
	originalAt := prevNetworkSampleAt

	netDevPath = networkFixturePath
	fakeNow = func() time.Time { return now }
	nowFunc = func() time.Time { return fakeNow() }
	prevNetworkCounters = nil
	prevNetworkSampleAt = time.Time{}

	return func() {
		netDevPath = originalPath
		nowFunc = originalNow
		prevNetworkCounters = originalCounters
		prevNetworkSampleAt = originalAt
	}
}

func writeNetworkFixture(t *testing.T, content string) {
	t.Helper()
	if err := os.WriteFile(networkFixturePath, []byte("Inter-| Receive | Transmit\n face |bytes\n"+content), 0o600); err != nil {
		t.Fatal(err)
	}
}
