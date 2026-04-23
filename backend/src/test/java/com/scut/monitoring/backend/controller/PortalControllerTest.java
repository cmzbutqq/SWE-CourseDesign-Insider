package com.scut.monitoring.backend.controller;

import com.scut.monitoring.backend.service.NodeRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
