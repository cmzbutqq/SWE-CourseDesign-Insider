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

export function fetchNodes(filters = {}) {
  const params = new URLSearchParams();
  
  if (filters.status) params.append('status', filters.status);
  if (filters.keyword) params.append('keyword', filters.keyword);
  if (filters.serviceType) params.append('serviceType', filters.serviceType);
  if (filters.sortBy) params.append('sortBy', filters.sortBy);
  
  const url = params.toString() ? `/nodes?${params.toString()}` : '/nodes';
  return fetchJson(url);
}

export function fetchNodeDetail(id) {
  return fetchJson(`/nodes/${id}`);
}

export function fetchServices() {
  return fetchJson("/services");
}

export function fetchTrends(hours = 1) {
  return fetchJson(`/trends?hours=${hours}`);
}
