package com.beat.service.email;

import com.beat.entity.Channel;
import com.beat.entity.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DigestHtmlTemplateBuilderTest {

    @Test
    void buildDigestHtml_withValidItems_containsTitlesBlurbsSourceTagsAndRankNumbers() {
        Channel channel = new Channel("user_123", "AI & Robotics", "AI news", 5, LocalTime.of(8, 0), "UTC", true);

        NewsItem item1 = new NewsItem();
        item1.setTitle("Autonomous AI Agents Released");
        item1.setSummaryBlurb("New framework allows multi-agent orchestration seamlessly.");
        item1.setSourceName("TechPulse");
        item1.setUrl("https://techpulse.dev/news/agents");
        item1.setRankPosition(1);

        NewsItem item2 = new NewsItem();
        item2.setTitle("Breakthrough in Quantum Chips");
        item2.setSummaryBlurb("Novel semiconductor design cuts error rates.");
        item2.setSourceName("Hardware Daily");
        item2.setUrl("https://hardwaredaily.com/chips");
        item2.setRankPosition(2);

        String html = DigestHtmlTemplateBuilder.buildDigestHtml(channel, "Mon, Aug 24, 2026", List.of(item1, item2));

        assertNotNull(html);
        assertTrue(html.contains("Autonomous AI Agents Released"), "Should contain item 1 title");
        assertTrue(html.contains("New framework allows multi-agent orchestration seamlessly."), "Should contain item 1 blurb");
        assertTrue(html.contains("TechPulse"), "Should contain item 1 source tag");
        assertTrue(html.contains("#1"), "Should contain rank #1");
        assertTrue(html.contains("https://techpulse.dev/news/agents"), "Should contain item 1 url");

        assertTrue(html.contains("Breakthrough in Quantum Chips"), "Should contain item 2 title");
        assertTrue(html.contains("Novel semiconductor design cuts error rates."), "Should contain item 2 blurb");
        assertTrue(html.contains("Hardware Daily"), "Should contain item 2 source tag");
        assertTrue(html.contains("#2"), "Should contain rank #2");

        assertTrue(html.contains("AI &amp; Robotics"), "Should contain escaped channel name");
        assertTrue(html.contains("Mon, Aug 24, 2026"), "Should contain formatted date");
        assertTrue(html.contains("2 curated stories"), "Should contain story count");
    }

    @Test
    void buildDigestHtml_withEmptyList_returnsValidHtmlWith0CuratedStories() {
        Channel channel = new Channel("user_123", "Cybersecurity", "Security alerts", 5, LocalTime.of(8, 0), "UTC", true);

        String html = DigestHtmlTemplateBuilder.buildDigestHtml(channel, "Mon, Aug 24, 2026", List.of());

        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("</html>"));
        assertTrue(html.contains("0 curated stories"), "Should indicate 0 curated stories");
        assertTrue(html.contains("Cybersecurity"), "Should include channel name");
    }

    @Test
    void buildDigestHtml_withNullItems_doesNotCrash() {
        Channel channel = new Channel("user_123", "FinTech", "Finance", 5, LocalTime.of(8, 0), "UTC", true);

        assertDoesNotThrow(() -> {
            String html = DigestHtmlTemplateBuilder.buildDigestHtml(channel, "Mon, Aug 24, 2026", null);
            assertNotNull(html);
            assertTrue(html.contains("0 curated stories"));
            assertTrue(html.contains("FinTech"));
        });
    }

    @Test
    void buildDigestHtml_withNullChannel_usesFallbackChannelName() {
        NewsItem item = new NewsItem();
        item.setTitle("General Article");
        item.setRankPosition(1);

        String html = DigestHtmlTemplateBuilder.buildDigestHtml(null, "Mon, Aug 24, 2026", List.of(item));

        assertNotNull(html);
        assertTrue(html.contains("Research"), "Should fall back to default channel name 'Research'");
        assertTrue(html.contains("General Article"));
    }

    @Test
    void buildMagicLinkHtml_containsMagicLinkUrl() {
        String magicLinkUrl = "https://beat.app/api/auth/callback?token=secure_jwt_token_987";

        String html = DigestHtmlTemplateBuilder.buildMagicLinkHtml(magicLinkUrl);

        assertNotNull(html);
        assertTrue(html.contains("https://beat.app/api/auth/callback?token=secure_jwt_token_987"));
        assertTrue(html.contains("Sign in to Beat"));
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    void buildMagicLinkHtml_withNullUrl_handlesGracefully() {
        String html = DigestHtmlTemplateBuilder.buildMagicLinkHtml(null);

        assertNotNull(html);
        assertTrue(html.contains("href=\"#\""));
        assertTrue(html.contains("Sign in to Beat"));
    }

    @Test
    void escapeHtml_handlesSpecialCharacters() {
        assertEquals("&lt;div&gt;Hello &amp; &#39;World&#39; &quot;Test&quot;&lt;/div&gt;",
                DigestHtmlTemplateBuilder.escapeHtml("<div>Hello & 'World' \"Test\"</div>"));
        assertEquals("", DigestHtmlTemplateBuilder.escapeHtml(null));
        assertEquals("Plain text without specials", DigestHtmlTemplateBuilder.escapeHtml("Plain text without specials"));
    }
}
