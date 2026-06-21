package com.scut.monitoring.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scut.monitoring.backend.dto.LatestTracePreviewDTO;
import com.scut.monitoring.backend.dto.TraceSummaryItemDTO;
import com.scut.monitoring.backend.dto.TracingSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SkyWalkingQueryService {
    private static final DateTimeFormatter SKYWALKING_QUERY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TRACE_DISPLAY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final RestClient restClient;

    public SkyWalkingQueryService(
            @Value("${monitoring.skywalking.oap-url:http://skywalking-oap:12800}") String skywalkingOapUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(skywalkingOapUrl)
                .build();
    }

    public TracingSummaryResponse loadTracingSummary() {
        JsonNode response = restClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildSummaryQueryBody())
                .retrieve()
                .body(JsonNode.class);

        JsonNode data = response == null ? null : response.path("data");
        List<TraceSummaryItemDTO> traces = readTraces(data == null ? null : data.path("queryBasicTraces").path("traces"));
        return new TracingSummaryResponse(
                readServiceNames(data == null ? null : data.path("listServices")),
                traces,
                loadLatestTracePreview(traces)
        );
    }

    private String buildSummaryQueryBody() {
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofHours(1));
        String query = """
                query TracingSummary {
                  listServices(layer: "GENERAL") {
                    name
                  }
                  queryBasicTraces(condition: {
                    queryDuration: {
                      start: "%s"
                      end: "%s"
                      step: MINUTE
                    }
                    traceState: ALL
                    queryOrder: BY_START_TIME
                    paging: {
                      pageNum: 1
                      pageSize: 8
                    }
                  }) {
                    traces {
                      traceIds
                      endpointNames
                      duration
                      start
                      isError
                    }
                  }
                }
                """.formatted(
                SKYWALKING_QUERY_TIME_FORMATTER.format(start),
                SKYWALKING_QUERY_TIME_FORMATTER.format(end)
        );
        return "{\"query\":%s}".formatted(quote(query));
    }

    private List<String> readServiceNames(JsonNode node) {
        List<String> results = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return results;
        }
        for (JsonNode item : node) {
            String name = item.path("name").asText("");
            if (!name.isBlank()) {
                results.add(name);
            }
        }
        return results;
    }

    private List<TraceSummaryItemDTO> readTraces(JsonNode node) {
        Map<String, TraceSummaryItemDTO> groupedTraces = new LinkedHashMap<>();
        if (node == null || !node.isArray()) {
            return List.of();
        }
        int fallbackIndex = 0;
        for (JsonNode item : node) {
            List<String> traceIds = readStringList(item.path("traceIds"));
            TraceSummaryItemDTO trace = new TraceSummaryItemDTO(
                    traceIds.isEmpty() ? "" : traceIds.get(0),
                    readStringList(item.path("endpointNames")),
                    item.path("duration").isMissingNode() ? null : item.path("duration").asInt(),
                    formatTraceStart(item.path("start").asText("")),
                    item.path("isError").asBoolean(false)
            );
            String traceKey = trace.traceId().isBlank() ? "trace-" + fallbackIndex++ : trace.traceId();
            groupedTraces.merge(traceKey, trace, this::mergeTraceSummary);
        }
        List<TraceSummaryItemDTO> allTraces = new ArrayList<>(groupedTraces.values());
        List<TraceSummaryItemDTO> businessTraces = allTraces.stream()
                .filter(this::isBusinessTrace)
                .toList();
        return businessTraces.isEmpty() ? allTraces : businessTraces;
    }

    private TraceSummaryItemDTO mergeTraceSummary(TraceSummaryItemDTO existing, TraceSummaryItemDTO incoming) {
        List<String> endpoints = new ArrayList<>(existing.endpoints());
        boolean incomingLooksLikeParent = existing.durationMs() == null
                || (incoming.durationMs() != null && incoming.durationMs() > existing.durationMs());
        List<String> newEndpoints = incoming.endpoints().stream()
                .filter(endpoint -> !endpoints.contains(endpoint))
                .toList();
        if (incomingLooksLikeParent) {
            for (int index = newEndpoints.size() - 1; index >= 0; index--) {
                endpoints.add(0, newEndpoints.get(index));
            }
        } else {
            endpoints.addAll(newEndpoints);
        }
        Integer durationMs = existing.durationMs();
        if (durationMs == null || (incoming.durationMs() != null && incoming.durationMs() > durationMs)) {
            durationMs = incoming.durationMs();
        }
        String startTime = existing.startTime().isBlank() ? incoming.startTime() : existing.startTime();
        return new TraceSummaryItemDTO(
                existing.traceId().isBlank() ? incoming.traceId() : existing.traceId(),
                endpoints,
                durationMs,
                startTime,
                existing.error() || incoming.error()
        );
    }

    private LatestTracePreviewDTO loadLatestTracePreview(List<TraceSummaryItemDTO> traces) {
        TraceSummaryItemDTO latestBusinessTrace = traces.stream()
                .filter(this::isBusinessTrace)
                .findFirst()
                .orElse(null);
        if (latestBusinessTrace == null || latestBusinessTrace.traceId().isBlank()) {
            return null;
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildTraceDetailQueryBody(latestBusinessTrace.traceId()))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode spans = response == null
                    ? null
                    : response.path("data").path("queryTrace").path("spans");
            return buildLatestTracePreview(latestBusinessTrace, spans);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildTraceDetailQueryBody(String traceId) {
        String query = """
                query TraceDetail($traceId: ID!) {
                  queryTrace(traceId: $traceId) {
                    spans {
                      serviceCode
                      endpointName
                      type
                      peer
                      component
                      layer
                    }
                  }
                }
                """;
        return """
                {"query":%s,"variables":{"traceId":%s}}
                """.formatted(quote(query), quote(traceId));
    }

    private LatestTracePreviewDTO buildLatestTracePreview(TraceSummaryItemDTO summary, JsonNode spansNode) {
        List<String> serviceChain = new ArrayList<>();
        List<String> dependencyChain = new ArrayList<>();
        int spanCount = 0;

        if (spansNode != null && spansNode.isArray()) {
            for (JsonNode span : spansNode) {
                spanCount++;
                String serviceCode = span.path("serviceCode").asText("").trim();
                String endpointName = span.path("endpointName").asText("").trim();
                String type = span.path("type").asText("").trim();
                String peer = span.path("peer").asText("").trim();
                String component = span.path("component").asText("").trim();
                String layer = span.path("layer").asText("").trim();

                if ("Entry".equalsIgnoreCase(type) && "Http".equalsIgnoreCase(layer) && !serviceCode.isBlank()) {
                    appendUnique(serviceChain, serviceCode);
                }

                if ("Exit".equalsIgnoreCase(type)) {
                    String dependency = resolveDependencyLabel(endpointName, peer, component, layer);
                    if (dependency != null) {
                        appendUnique(dependencyChain, dependency);
                    }
                }
            }
        }

        String entryService = serviceChain.isEmpty() ? null : serviceChain.get(0);
        String entryEndpoint = summary.endpoints().stream()
                .filter(this::isBusinessEndpoint)
                .findFirst()
                .orElse(summary.endpoints().isEmpty() ? "" : summary.endpoints().get(0));

        return new LatestTracePreviewDTO(
                summary.traceId(),
                entryService,
                entryEndpoint,
                serviceChain,
                dependencyChain,
                spanCount == 0 ? null : spanCount,
                summary.durationMs(),
                summary.startTime(),
                summary.error()
        );
    }

    private String resolveDependencyLabel(String endpointName, String peer, String component, String layer) {
        String endpoint = endpointName.toLowerCase();
        String peerText = peer.toLowerCase();
        String componentText = component.toLowerCase();
        String layerText = layer.toLowerCase();

        if (layerText.equals("database") || componentText.contains("mysql") || peerText.contains(":3306")) {
            return "mysql";
        }
        if (layerText.equals("cache") || componentText.contains("redis") || componentText.contains("lettuce") || peerText.contains(":6379")) {
            return "redis";
        }
        if (layerText.equals("http") && (peerText.endsWith(":80") || endpoint.equals("/"))) {
            return "nginx";
        }
        return null;
    }

    private void appendUnique(List<String> values, String candidate) {
        if (candidate == null || candidate.isBlank() || values.contains(candidate)) {
            return;
        }
        values.add(candidate);
    }

    private List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private boolean isBusinessTrace(TraceSummaryItemDTO trace) {
        return trace.endpoints().stream()
                .map(String::trim)
                .filter(endpoint -> !endpoint.isBlank())
                .anyMatch(this::isBusinessEndpoint);
    }

    private boolean isBusinessEndpoint(String endpoint) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.contains("/actuator/prometheus")) {
            return false;
        }
        return normalized.matches("^[A-Z]+:/.*");
    }

    private String formatTraceStart(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        try {
            return TRACE_DISPLAY_TIME_FORMATTER.format(Instant.ofEpochMilli(Long.parseLong(rawValue)));
        } catch (NumberFormatException ignored) {
            return rawValue;
        }
    }

    private String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "") + "\"";
    }
}
