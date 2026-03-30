const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:18081/api";

async function fetchJson(path) {
  const response = await fetch(`${API_BASE_URL}${path}`);
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchOverview() {
  return fetchJson("/overview");
}

export function fetchNodes() {
  return fetchJson("/nodes");
}

export function fetchNodeDetail(id) {
  return fetchJson(`/nodes/${id}`);
}

export function fetchServices() {
  return fetchJson("/services");
}
