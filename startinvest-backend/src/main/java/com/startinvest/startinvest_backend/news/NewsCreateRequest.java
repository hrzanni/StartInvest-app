package com.startinvest.startinvest_backend.news;

import java.time.OffsetDateTime;

public record NewsCreateRequest(
    String title,
    String content,
    String source,
    String category,
    OffsetDateTime publishedAt
) {
}
