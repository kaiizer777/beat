package com.beat.dto;

import java.util.Objects;

public class RawArticle {
    private String title;
    private String url;
    private String snippet;
    private String publisher;
    private String publishedAt;
    private String fullText;
    private String fetchSource; // "tinyfish" or "jina"
    private String summaryBlurb;

    public RawArticle() {
    }

    public RawArticle(String title, String url, String snippet, String publisher, String publishedAt, String fullText, String fetchSource) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.fullText = fullText;
        this.fetchSource = fetchSource;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public String getFetchSource() {
        return fetchSource;
    }

    public void setFetchSource(String fetchSource) {
        this.fetchSource = fetchSource;
    }

    public String getSummaryBlurb() {
        return summaryBlurb;
    }

    public void setSummaryBlurb(String summaryBlurb) {
        this.summaryBlurb = summaryBlurb;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RawArticle that = (RawArticle) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "RawArticle{" +
                "title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", publisher='" + publisher + '\'' +
                ", publishedAt='" + publishedAt + '\'' +
                ", fetchSource='" + fetchSource + '\'' +
                ", fullTextLength=" + (fullText != null ? fullText.length() : 0) +
                '}';
    }
}
