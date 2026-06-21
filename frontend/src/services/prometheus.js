export function resolvePrometheusApiBaseUrl(env = import.meta.env) {
  return `${env.VITE_PROMETHEUS_BASE_URL || "/prometheus"}/api/v1`;
}

async function fetchPrometheusJson(path, env = import.meta.env) {
  const response = await fetch(`${resolvePrometheusApiBaseUrl(env)}${path}`);
  if (!response.ok) {
    throw new Error(`Prometheus request failed: ${response.status}`);
  }

  const payload = await response.json();
  if (payload.status !== "success") {
    throw new Error(payload.error || "Prometheus request failed");
  }

  return payload.data;
}

function normalizeTarget(target) {
  return {
    job: target?.labels?.job || target?.scrapePool || "-",
    instance:
      target?.labels?.instance || target?.globalUrl || target?.scrapeUrl || "-",
    health: target?.health || "unknown",
    lastError: target?.lastError || "",
    durationMs: Math.round((target?.lastScrapeDuration || 0) * 1000),
    scrapeUrl: target?.scrapeUrl || "",
  };
}

export function summarizePrometheusTargets(payload = {}) {
  const targets = (payload.activeTargets || []).map(normalizeTarget);
  const totalTargets = targets.length;
  const upTargets = targets.filter((target) => target.health === "up").length;
  const problemTargets = targets.filter((target) => target.health !== "up").length;
  const scrapePools = new Set(targets.map((target) => target.job)).size;
  const slowestTarget = targets.reduce(
    (current, target) =>
      !current || target.durationMs > current.durationMs ? target : current,
    null
  ) || {
    job: "-",
    instance: "-",
    durationMs: 0,
    health: "unknown",
    lastError: "",
  };

  return {
    totalTargets,
    upTargets,
    problemTargets,
    scrapePools,
    slowestTarget,
    targets,
  };
}

export function fetchPrometheusTargets(env = import.meta.env) {
  return fetchPrometheusJson("/targets", env);
}
