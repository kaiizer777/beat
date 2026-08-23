package com.beat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"internal.secret=default_internal_secret_key"})
@AutoConfigureMockMvc
class InternalChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testRunDueChannelsWithoutSecret_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/run-due-channels")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    void testRunDueChannelsWithWrongSecret_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/run-due-channels")
                        .header("X-Internal-Secret", "wrong_secret")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    void testRunDueChannelsWithValidSecretHeader_ReturnsOk() throws Exception {
        mockMvc.perform(post("/api/internal/run-due-channels")
                        .header("X-Internal-Secret", "default_internal_secret_key")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));
    }

    @Test
    void testRunDueChannelsWithAltSecretHeader_ReturnsOk() throws Exception {
        mockMvc.perform(post("/api/internal/run-due-channels")
                        .header("X-Shared-Secret", "default_internal_secret_key")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));
    }
}
