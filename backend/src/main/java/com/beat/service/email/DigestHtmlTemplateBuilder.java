package com.beat.service.email;

import com.beat.entity.Channel;
import com.beat.entity.NewsItem;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DigestHtmlTemplateBuilder {

    private DigestHtmlTemplateBuilder() {
        // Utility class
    }

    public static String buildDigestHtml(Channel channel, String formattedDate, List<NewsItem> newsItems) {
        // D1: Build "Top Signals" TL;DR block from top 3 articles
        StringBuilder tldrItems = new StringBuilder();
        if (newsItems != null && !newsItems.isEmpty()) {
            int tldrCount = Math.min(newsItems.size(), 3);
            for (int i = 0; i < tldrCount; i++) {
                NewsItem item = newsItems.get(i);
                if (item.getTitle() != null && !item.getTitle().isBlank()) {
                    tldrItems.append("<li style=\"margin-bottom:4px;\">")
                             .append(escapeHtml(item.getTitle()))
                             .append("</li>");
                }
            }
        }
        String tldrHtml = tldrItems.length() > 0 ? """
            <div style="background:#1e293b;padding:16px 24px;margin-bottom:20px;border-radius:8px;">
              <p style="color:#94a3b8;font-size:11px;text-transform:uppercase;letter-spacing:1px;margin:0 0 8px 0;">
                Top Signals
              </p>
              <ul style="color:#f1f5f9;font-size:14px;line-height:1.8;margin:0;padding-left:20px;">
                %s
              </ul>
            </div>
            """.formatted(tldrItems.toString()) : "";

        StringBuilder itemsHtml = new StringBuilder();
        itemsHtml.append(tldrHtml);

        if (newsItems != null) {
            for (NewsItem item : newsItems) {
                String sourceTag = (item.getSourceName() != null && !item.getSourceName().isBlank())
                        ? item.getSourceName() : "Web Source";
                String title = item.getTitle() != null ? escapeHtml(item.getTitle()) : "Untitled Article";
                // D3: escapeHtml on URL before injecting into href
                String url = item.getUrl() != null ? escapeHtml(item.getUrl()) : "#";
                String blurb = item.getSummaryBlurb() != null ? escapeHtml(item.getSummaryBlurb()) : "";
                int rank = item.getRankPosition() != null ? item.getRankPosition() : 0;

                // D2: Render publishedAt as "MMM d" if available
                String pubDate = (item.getPublishedAt() != null)
                        ? DateTimeFormatter.ofPattern("MMM d").withZone(ZoneOffset.UTC).format(item.getPublishedAt())
                        : "";
                String sourceLine = pubDate.isBlank()
                        ? escapeHtml(sourceTag)
                        : escapeHtml(sourceTag) + " &middot; " + pubDate;

                itemsHtml.append("""
                    <div style="margin-bottom: 24px; padding: 20px; background-color: #ffffff; border-radius: 8px; border: 1px solid #e5e7eb; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
                        <div style="display: flex; align-items: center; margin-bottom: 8px;">
                            <span style="background-color: #3b82f6; color: #ffffff; font-size: 12px; font-weight: bold; padding: 2px 8px; border-radius: 12px; margin-right: 10px;">#%d</span>
                            <span style="color: #6b7280; font-size: 13px; font-weight: 500;">%s</span>
                        </div>
                        <h3 style="margin: 0 0 10px 0; font-size: 18px; font-weight: 600; line-height: 1.4;">
                            <a href="%s" target="_blank" style="color: #1d4ed8; text-decoration: none;">%s</a>
                        </h3>
                        <p style="margin: 0; color: #374151; font-size: 14px; line-height: 1.6; background-color: #f9fafb; padding: 12px; border-left: 3px solid #3b82f6; border-radius: 4px;">
                            %s
                        </p>
                    </div>
                """.formatted(rank, sourceLine, url, title, blurb));
            }
        }

        int itemCount = newsItems != null ? newsItems.size() : 0;
        String channelName = channel != null && channel.getName() != null ? channel.getName() : "Research";

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Beat Digest</title>
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f3f4f6; margin: 0; padding: 20px; color: #111827;">
                <div style="max-width: 680px; margin: 0 auto; background-color: #f9fafb; border-radius: 12px; overflow: hidden; border: 1px solid #e5e7eb;">
                    <!-- Header -->
                    <div style="background-color: #1e293b; padding: 28px 24px; color: #ffffff;">
                        <span style="text-transform: uppercase; letter-spacing: 1px; font-size: 12px; color: #94a3b8; font-weight: 700;">BEAT RESEARCH DIGEST</span>
                        <h1 style="margin: 6px 0 4px 0; font-size: 24px; font-weight: 700; color: #f8fafc;">%s</h1>
                        <p style="margin: 0; font-size: 14px; color: #cbd5e1;">%s &bull; %d curated stories</p>
                    </div>

                    <!-- Main Content -->
                    <div style="padding: 24px;">
                        %s
                    </div>

                    <!-- Footer -->
                    <div style="background-color: #e2e8f0; padding: 16px 24px; text-align: center; font-size: 12px; color: #64748b;">
                        <p style="margin: 0;">Automated Multi-Cron Research Digest generated by <strong>Beat</strong></p>
                    </div>
                </div>
            </body>
            </html>
        """.formatted(escapeHtml(channelName), escapeHtml(formattedDate), itemCount, itemsHtml.toString());
    }

    public static String buildMagicLinkHtml(String magicLinkUrl) {
        String safeUrl = magicLinkUrl != null ? magicLinkUrl : "#";
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Sign in to Beat</title>
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f3f4f6; margin: 0; padding: 20px; color: #111827;">
                <div style="max-width: 540px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e5e7eb; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);">
                    <div style="background-color: #1e293b; padding: 24px; text-align: center;">
                        <h1 style="margin: 0; font-size: 20px; font-weight: 700; color: #ffffff; letter-spacing: 0.5px;">BEAT</h1>
                        <p style="margin: 4px 0 0 0; font-size: 13px; color: #94a3b8;">Personalized Research &amp; News Digest</p>
                    </div>
                    <div style="padding: 32px 24px; text-align: center;">
                        <h2 style="margin: 0 0 12px 0; font-size: 18px; font-weight: 600; color: #1e293b;">Sign in to your account</h2>
                        <p style="margin: 0 0 24px 0; font-size: 14px; color: #475569; line-height: 1.5;">
                            Click the button below to authenticate with Beat. This link is valid for a single use.
                        </p>
                        <a href="%s" target="_blank" style="display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; font-weight: 600; font-size: 15px; padding: 12px 28px; border-radius: 8px; box-shadow: 0 2px 4px rgba(37, 99, 235, 0.2);">
                            Sign in to Beat
                        </a>
                        <p style="margin: 28px 0 0 0; font-size: 12px; color: #94a3b8; line-height: 1.4;">
                            If you didn't request this email, you can safely ignore it.
                        </p>
                    </div>
                    <div style="background-color: #f8fafc; padding: 16px 24px; text-align: center; font-size: 11px; color: #94a3b8; border-top: 1px solid #e2e8f0;">
                        &copy; Beat News &bull; Autonomous Intelligence Digest
                    </div>
                </div>
            </body>
            </html>
        """.formatted(safeUrl);
    }

    public static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
