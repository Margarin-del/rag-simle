package com.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String EMBEDDING_CACHE_PREFIX = "embedding:";
    private static final String SEARCH_CACHE_PREFIX = "search:";
    private static final int EMBEDDING_TTL = 86400; // 24 часа
    private static final int SEARCH_TTL = 3600; // 1 час

    // === Методы для эмбеддингов ===

    public void cacheEmbedding(String text, float[] embedding) {
        try {
            String key = EMBEDDING_CACHE_PREFIX + sha256(text);
            String jsonValue = objectMapper.writeValueAsString(embedding);
            redisTemplate.opsForValue().set(key, jsonValue, EMBEDDING_TTL, TimeUnit.SECONDS);
            log.info("✅ Cached embedding for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to cache embedding: {}", e.getMessage());
        }
    }

    public float[] getEmbeddingFromCache(String text) {
        try {
            String key = EMBEDDING_CACHE_PREFIX + sha256(text);
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                String jsonValue = cached.toString();
                log.info("✅ Cache hit for key: {}", key);
                return objectMapper.readValue(jsonValue, float[].class);
            }
            log.debug("Cache miss for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to get embedding from cache: {}", e.getMessage());
        }
        return null;
    }

    // === Методы для поисковых запросов ===

    public void cacheSearchResult(String key, Object result) {
        try {
            redisTemplate.opsForValue().set(key, result, SEARCH_TTL, TimeUnit.SECONDS);
            log.info("✅ Cached search result for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to cache search result: {}", e.getMessage());
        }
    }

    public Object getSearchResultFromCacheRaw(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("✅ Cache hit for key: {}", key);
                return cached;
            }
        } catch (Exception e) {
            log.error("Failed to get search result from cache: {}", e.getMessage());
        }
        return null;
    }

    public <T> T getSearchResultFromCache(String key, Class<T> type) {
        Object cached = getSearchResultFromCacheRaw(key);
        if (cached == null) {
            return null;
        }
        return objectMapper.convertValue(cached, type);
    }

    // === Вспомогательные методы ===

    public void clearCache() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushDb();
            log.info("Cache cleared");
        } catch (Exception e) {
            log.error("Failed to clear cache: {}", e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback на hashCode при очень странной среде, но логируем проблему
            log.error("SHA-256 is not available, fallback to hashCode", e);
            return Integer.toHexString(input.hashCode());
        }
    }
}