package com.beat;

import com.beat.dto.ChannelRequest;
import com.beat.entity.Channel;
import com.beat.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private com.beat.repository.DigestRunRepository digestRunRepository;

    @Autowired
    private com.beat.repository.NewsItemRepository newsItemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        newsItemRepository.deleteAll();
        digestRunRepository.deleteAll();
        channelRepository.deleteAll();
    }


    @Test
    void testCreateAndGetChannelSuccess() throws Exception {
        ChannelRequest request = new ChannelRequest(
                "AI/ML",
                "artificial intelligence and machine learning developments",
                20,
                LocalTime.of(8, 0),
                "Asia/Kolkata",
                true
        );

        String json = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/channels")
                        .with(jwt().jwt(jwt -> jwt.subject("test_user_id")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("AI/ML")))
                .andExpect(jsonPath("$.articleCount", is(20)))
                .andExpect(jsonPath("$.timezone", is("Asia/Kolkata")));

        mockMvc.perform(get("/api/channels")
                        .with(jwt().jwt(jwt -> jwt.subject("test_user_id"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("AI/ML")));
    }

    @Test
    void testCreateChannelInvalidTimezone() throws Exception {
        ChannelRequest request = new ChannelRequest(
                "AI/ML",
                "artificial intelligence news",
                15,
                LocalTime.of(10, 0),
                "Invalid/Timezone_String",
                true
        );

        mockMvc.perform(post("/api/channels")
                        .with(jwt().jwt(jwt -> jwt.subject("test_user_id")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    void testCreateChannelInvalidArticleCount() throws Exception {
        ChannelRequest request = new ChannelRequest(
                "AI/ML",
                "artificial intelligence news",
                30, // max is 25
                LocalTime.of(10, 0),
                "Asia/Kolkata",
                true
        );

        mockMvc.perform(post("/api/channels")
                        .with(jwt().jwt(jwt -> jwt.subject("test_user_id")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    void testUpdateAndDeleteChannel() throws Exception {
        Channel channel = new Channel("test_user_id", "Initial", "Topic", 10, LocalTime.of(9, 0), "UTC", true);
        Channel saved = channelRepository.save(channel);

        ChannelRequest updateRequest = new ChannelRequest(
                "Updated Name",
                "Updated Topic",
                15,
                LocalTime.of(12, 30),
                "America/New_York",
                false
        );

        mockMvc.perform(put("/api/channels/" + saved.getId())
                        .with(jwt().jwt(jwt -> jwt.subject("test_user_id")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.timezone", is("America/New_York")))
                .andExpect(jsonPath("$.isActive", is(false)));

        mockMvc.perform(delete("/api/channels/" + saved.getId())
                        .with(jwt().jwt(jwt -> jwt.subject("test_user_id"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/channels/" + saved.getId())
                        .with(jwt().jwt(jwt -> jwt.subject("test_user_id"))))
                .andExpect(status().isNotFound());
    }
}

