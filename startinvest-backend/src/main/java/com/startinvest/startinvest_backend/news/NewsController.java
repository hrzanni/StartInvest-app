package com.startinvest.startinvest_backend.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController 
@RequestMapping ("/api/news")
public class NewsController {
    
    private final NewsRepository newsRepository;

    public NewsController(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    @GetMapping 
    public Page<NewsResponse> list(
        @RequestParam (required = false) String category, Pageable pageable) {
            Page<News> page = (category != null)
                ? newsRepository.findByCategory(category, pageable)
                : newsRepository.findAll(pageable);
            
                return page.map(NewsResponse::from);
        } 
    
    @PostMapping 
    public NewsResponse create(@RequestBody NewsCreateRequest request) {
        News news = new News(
            null, 
            request.title(), 
            request.content(), 
            request.source(), 
            request.category(), 
            request.publishedAt()
        );
        News saved = newsRepository.save(news);
        return NewsResponse.from(saved);
    }
}
    
