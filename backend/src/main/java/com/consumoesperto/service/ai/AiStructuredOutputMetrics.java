package com.consumoesperto.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class AiStructuredOutputMetrics {

    private final AtomicLong validas = new AtomicLong();
    private final AtomicLong corrigidas = new AtomicLong();
    private final AtomicLong rejeitadas = new AtomicLong();

    public void record(AiStructuredOutputStatus status) {
        switch (status) {
            case VALID -> validas.incrementAndGet();
            case CORRECTED -> corrigidas.incrementAndGet();
            case REJECTED, NEEDS_CONFIRMATION -> rejeitadas.incrementAndGet();
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("respostasValidas", validas.get());
        out.put("respostasCorrigidas", corrigidas.get());
        out.put("respostasRejeitadas", rejeitadas.get());
        return out;
    }
}
