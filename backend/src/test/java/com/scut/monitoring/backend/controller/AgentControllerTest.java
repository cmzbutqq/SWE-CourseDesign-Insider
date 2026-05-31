package com.scut.monitoring.backend.controller;

import com.scut.monitoring.backend.service.NodeRegistryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NodeRegistryService nodeRegistryService;

    @Test
    void heartbeatShouldReturnNotFoundWhenNodeIsNotRegistered() throws Exception {
        doThrow(new EntityNotFoundException("Node not registered: ghost-node"))
                .when(nodeRegistryService)
                .heartbeat(any());

        mockMvc.perform(post("/api/agents/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeName": "ghost-node",
                                  "status": "ONLINE"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerShouldRejectBlankNodeName() throws Exception {
        mockMvc.perform(post("/api/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeName": " ",
                                  "hostname": "host",
                                  "ipAddress": "172.20.0.10",
                                  "osName": "linux",
                                  "agentVersion": "0.1.0",
                                  "services": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
