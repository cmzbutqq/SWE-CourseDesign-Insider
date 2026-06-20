package com.scut.monitoring.backend.controller;

import com.scut.monitoring.backend.dto.QuickLinkDTO;
import com.scut.monitoring.backend.dto.ServiceDetailResponse;
import com.scut.monitoring.backend.service.NodeRegistryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PortalController.class)
class PortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NodeRegistryService nodeRegistryService;

    @Test
    void trendsShouldReturnBadRequestWhenServiceRejectsHours() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid hours"))
                .when(nodeRegistryService)
                .getTrends(anyDouble());

        mockMvc.perform(get("/api/trends").param("hours", "1e308"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nodeDetailShouldReturnNotFoundWhenNodeDoesNotExist() throws Exception {
        doThrow(new EntityNotFoundException("Node not found: 999"))
                .when(nodeRegistryService)
                .getNode(999L);

        mockMvc.perform(get("/api/nodes/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceDetailShouldReturnNotFoundWhenServiceDoesNotExist() throws Exception {
        doThrow(new EntityNotFoundException("Service not found: 999"))
                .when(nodeRegistryService)
                .getService(999L);

        mockMvc.perform(get("/api/services/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceDetailShouldReturnJsonResponse() throws Exception {
        when(nodeRegistryService.getService(7L)).thenReturn(new ServiceDetailResponse(
                7L,
                "sample-service",
                "SPRING_BOOT",
                8081,
                "java",
                null,
                42L,
                "app-node",
                "172.20.0.10",
                "WARNING",
                true,
                List.of(
                        new QuickLinkDTO("Grafana", "http://localhost:15173/grafana/d/service-detail/service-detail?var-service=sample-service&var-instance=app-node%3A8081"),
                        new QuickLinkDTO("Prometheus", "http://localhost:15173/prometheus/graph?g0.expr=up%7Bjob%3D%22sample-service%22%2Cinstance%3D%22app-node%3A8081%22%7D")
                )
        ));

        mockMvc.perform(get("/api/services/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.serviceName").value("sample-service"))
                .andExpect(jsonPath("$.serviceType").value("SPRING_BOOT"))
                .andExpect(jsonPath("$.port").value(8081))
                .andExpect(jsonPath("$.processName").value("java"))
                .andExpect(jsonPath("$.metricsPath").doesNotExist())
                .andExpect(jsonPath("$.nodeId").value(42))
                .andExpect(jsonPath("$.nodeName").value("app-node"))
                .andExpect(jsonPath("$.nodeIpAddress").value("172.20.0.10"))
                .andExpect(jsonPath("$.nodeStatus").value("WARNING"))
                .andExpect(jsonPath("$.metricsMissing").value(true))
                .andExpect(jsonPath("$.quickLinks[0].name").value("Grafana"))
                .andExpect(jsonPath("$.quickLinks[1].name").value("Prometheus"));
    }
}
