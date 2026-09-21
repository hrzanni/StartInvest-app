package com.startinvest.startinvest_backend.news;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity 
@Table (name = "news")
public class News {

    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column (nullable = false, length = 200)
    private String title;

    @Column (nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column (length = 120)
    private String source;

    @Column (nullable = false, length = 20)
    private String category;

    @Column (name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;


    public News(){}

    public News(Long id, String title, String content, String source, String category, OffsetDateTime publishedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.source = source;
        this.category = category;
        this.publishedAt = publishedAt;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
