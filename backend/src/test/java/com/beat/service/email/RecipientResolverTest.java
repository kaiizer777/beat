package com.beat.service.email;

import com.beat.entity.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecipientResolverTest {

    private JdbcTemplate mockJdbcTemplate;
    private RecipientResolver recipientResolver;

    @BeforeEach
    void setUp() {
        mockJdbcTemplate = mock(JdbcTemplate.class);
        recipientResolver = new RecipientResolver(mockJdbcTemplate, "kaizerxdev@gmail.com");
    }

    @Test
    void resolveRecipientEmail_returnsDatabaseEmail_whenUserFound() {
        when(mockJdbcTemplate.query(anyString(), any(RowMapper.class), eq("user_456")))
                .thenReturn(List.of("registered_user@example.com"));

        Channel channel = new Channel("user_456", "Tech News", "Tech", 5, LocalTime.of(8, 0), "UTC", true);

        String email = recipientResolver.resolveRecipientEmail(channel);

        assertEquals("registered_user@example.com", email);
        verify(mockJdbcTemplate).query(eq("SELECT email FROM \"User\" WHERE id = ?"), any(RowMapper.class), eq("user_456"));
    }

    @Test
    void resolveRecipientEmail_fallsBackToDefault_whenDatabaseReturnsEmpty() {
        when(mockJdbcTemplate.query(anyString(), any(RowMapper.class), eq("user_nonexistent")))
                .thenReturn(List.of());

        Channel channel = new Channel("user_nonexistent", "Tech News", "Tech", 5, LocalTime.of(8, 0), "UTC", true);

        String email = recipientResolver.resolveRecipientEmail(channel);

        assertEquals("kaizerxdev@gmail.com", email);
    }

    @Test
    void resolveRecipientEmail_fallsBackToDefault_whenDatabaseThrowsException() {
        when(mockJdbcTemplate.query(anyString(), any(RowMapper.class), eq("user_err")))
                .thenThrow(new RuntimeException("DB Connection Timeout"));

        Channel channel = new Channel("user_err", "Tech News", "Tech", 5, LocalTime.of(8, 0), "UTC", true);

        String email = recipientResolver.resolveRecipientEmail(channel);

        assertEquals("kaizerxdev@gmail.com", email);
    }

    @Test
    void resolveRecipientEmail_fallsBackToDefault_whenChannelOrUserIdNull() {
        assertEquals("kaizerxdev@gmail.com", recipientResolver.resolveRecipientEmail(null));

        Channel channelWithNullUserId = new Channel(null, "Tech News", "Tech", 5, LocalTime.of(8, 0), "UTC", true);
        assertEquals("kaizerxdev@gmail.com", recipientResolver.resolveRecipientEmail(channelWithNullUserId));

        Channel channelWithBlankUserId = new Channel("   ", "Tech News", "Tech", 5, LocalTime.of(8, 0), "UTC", true);
        assertEquals("kaizerxdev@gmail.com", recipientResolver.resolveRecipientEmail(channelWithBlankUserId));

        verifyNoInteractions(mockJdbcTemplate);
    }
}
