package com.consumoesperto.service.jarvis;

import com.consumoesperto.config.JarvisPerformanceProperties;
import com.consumoesperto.service.JarvisContextoFinanceiroService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class JarvisContextoFinanceiroCacheService {

    private final JarvisContextoFinanceiroService delegate;
    private final JarvisPerformanceProperties props;

    private Cache<Long, String> cache;

    @PostConstruct
    void initCache() {
        cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(Math.max(30, props.getContextoCacheTtlSeconds())))
            .maximumSize(500)
            .build();
    }

    public String montarBlocoContexto(Long userId) {
        if (userId == null) {
            return "";
        }
        return cache.get(userId, id -> delegate.montarBlocoContexto(id));
    }

    public void invalidar(Long userId) {
        if (userId != null) {
            cache.invalidate(userId);
        }
    }

    public void invalidarTodos() {
        cache.invalidateAll();
    }
}
