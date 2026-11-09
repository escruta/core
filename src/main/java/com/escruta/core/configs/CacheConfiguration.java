package com.escruta.core.configs;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfiguration {
    public static final String NOTEBOOK_CONTEXT = "notebookContext";
    public static final String EXAMPLE_QUESTIONS = "exampleQuestions";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                new CaffeineCache(
                        NOTEBOOK_CONTEXT,
                        Caffeine.newBuilder().maximumSize(2_000).expireAfterWrite(Duration.ofMinutes(10)).build()
                ), new CaffeineCache(
                        EXAMPLE_QUESTIONS,
                        Caffeine.newBuilder().maximumSize(5_000).expireAfterWrite(Duration.ofHours(6)).build()
                )
        ));
        return cacheManager;
    }
}
