package com.famelsvg.reservasalas.room;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.famelsvg.reservasalas.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

class CacheErrorHandlerTest {

    private final CacheErrorHandler handler = new CacheConfig().errorHandler();
    private final Cache cache = mock(Cache.class);
    private final RuntimeException redisDown = new RuntimeException("Redis fora do ar");

    @Test
    void neverPropagatesRedisFailures() {
        assertThatCode(() -> {
                    handler.handleCacheGetError(redisDown, cache, "k");
                    handler.handleCachePutError(redisDown, cache, "k", "v");
                    handler.handleCacheEvictError(redisDown, cache, "k");
                    handler.handleCacheClearError(redisDown, cache);
                })
                .doesNotThrowAnyException();
    }
}
