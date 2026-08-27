package com.beat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TinyFishFetchClientTest {

    @Mock
    private RestTemplate restTemplate;

    private TinyFishFetchClient client;

    @BeforeEach
    void setUp() {
        client = new TinyFishFetchClient("test-key", "test-jina-key");
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    @Test
    void fetchContent_succeedsWithTextFieldInResponse() {
        String jsonResponse = """
            {
              "results": [
                {
                  "url": "https://example.com/article",
                  "text": "This is a detailed extracted article content with enough length to be considered usable content. It discusses recent advancements in artificial intelligence models and coding benchmarks across multiple development teams. It satisfies all minimum length requirements."
                }
              ]
            }
            """;

        when(restTemplate.exchange(eq("https://api.fetch.tinyfish.ai"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        TinyFishFetchClient.FetchResult result = client.fetchContent("https://example.com/article");

        assertNotNull(result);
        assertEquals("tinyfish", result.getSource());
        assertTrue(result.getContent().contains("detailed extracted article content"));
    }

    @Test
    void fetchContent_succeedsWithContentFieldInResponse() {
        String jsonResponse = """
            {
              "results": [
                {
                  "url": "https://example.com/article",
                  "content": "This is a detailed extracted article content with enough length to be considered usable content. It discusses recent advancements in artificial intelligence models and coding benchmarks across multiple development teams. It satisfies all minimum length requirements."
                }
              ]
            }
            """;

        when(restTemplate.exchange(eq("https://api.fetch.tinyfish.ai"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        TinyFishFetchClient.FetchResult result = client.fetchContent("https://example.com/article");

        assertNotNull(result);
        assertEquals("tinyfish", result.getSource());
        assertTrue(result.getContent().contains("detailed extracted article content"));
    }
}
