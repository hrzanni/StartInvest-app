package com.startinvest.startinvest_backend.news;

import java.time.OffsetDateTime;

public record NewsResponse(
    Long id,
    String title,
    String content,
    String source,
    String category,
    OffsetDateTime publishedAt
) {
    public static NewsResponse from(News news) {
        return new NewsResponse(
            news.getId(),
            news.getTitle(),
            news.getContent(),
            news.getSource(),
            news.getCategory(),
            news.getPublishedAt()
        );
    }    
}
