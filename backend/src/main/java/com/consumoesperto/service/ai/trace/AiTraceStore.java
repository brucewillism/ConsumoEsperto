package com.consumoesperto.service.ai.trace;

import com.consumoesperto.dto.ai.AiTraceFilterDTO;
import com.consumoesperto.model.ai.AiTrace;
import com.consumoesperto.service.ai.AiRouterMetrics;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class AiTraceStore {

    private final Deque<AiTrace> traces = new ArrayDeque<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile int maxSize = 10_000;

    public void setMaxSize(int maxSize) {
        this.maxSize = Math.max(500, maxSize);
        trim();
    }

    public void add(AiTrace trace) {
        lock.writeLock().lock();
        try {
            traces.addFirst(trace);
            trim();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void patchStructuredOutput(String traceId, Boolean valido, Boolean corrigido) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        lock.writeLock().lock();
        try {
            Deque<AiTrace> updated = new ArrayDeque<>();
            for (AiTrace t : traces) {
                if (traceId.equals(t.getTraceId())) {
                    updated.add(rebuildWithStructured(t, valido, corrigido));
                } else {
                    updated.add(t);
                }
            }
            traces.clear();
            traces.addAll(updated);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static AiTrace rebuildWithStructured(AiTrace t, Boolean valido, Boolean corrigido) {
        return AiTrace.builder()
            .traceId(t.getTraceId())
            .userId(t.getUserId())
            .taskType(t.getTaskType())
            .modeloEscolhido(t.getModeloEscolhido())
            .modeloPreferencial(t.getModeloPreferencial())
            .modeloFallback(t.getModeloFallback())
            .inicioExecucao(t.getInicioExecucao())
            .fimExecucao(t.getFimExecucao())
            .duracaoMs(t.getDuracaoMs())
            .tokensEntrada(t.getTokensEntrada())
            .tokensSaida(t.getTokensSaida())
            .custoEstimadoUsd(t.getCustoEstimadoUsd())
            .temperatura(t.getTemperatura())
            .tentativas(t.getTentativas())
            .fallbackUtilizado(t.isFallbackUtilizado())
            .structuredOutputValido(valido)
            .structuredOutputCorrigido(corrigido)
            .status(t.getStatus())
            .erro(t.getErro())
            .tentativasModelos(t.getTentativasModelos())
            .build();
    }

    public List<AiTrace> find(AiTraceFilterDTO filter, int limit, int offset) {
        lock.readLock().lock();
        try {
            List<AiTrace> out = new ArrayList<>();
            int skipped = 0;
            for (AiTrace t : traces) {
                if (!matches(t, filter)) {
                    continue;
                }
                if (skipped < offset) {
                    skipped++;
                    continue;
                }
                out.add(t);
                if (out.size() >= Math.max(1, limit)) {
                    break;
                }
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<AiTrace> snapshotAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(traces);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return traces.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void trim() {
        while (traces.size() > maxSize) {
            traces.removeLast();
        }
    }

    private boolean matches(AiTrace t, AiTraceFilterDTO f) {
        if (f == null) {
            return true;
        }
        if (f.getDesde() != null && t.getInicioExecucao().isBefore(f.getDesde())) {
            return false;
        }
        if (f.getAte() != null && t.getInicioExecucao().isAfter(f.getAte())) {
            return false;
        }
        if (f.getModelo() != null && !f.getModelo().isBlank()) {
            String m = f.getModelo().trim();
            if (t.getModeloEscolhido() == null || !t.getModeloEscolhido().equalsIgnoreCase(m)) {
                if (t.getModeloPreferencial() == null || !t.getModeloPreferencial().equalsIgnoreCase(m)) {
                    return false;
                }
            }
        }
        if (f.getTaskType() != null && t.getTaskType() != f.getTaskType()) {
            return false;
        }
        if (f.getUserId() != null && !f.getUserId().equals(t.getUserId())) {
            return false;
        }
        if (f.getStatus() != null && t.getStatus() != f.getStatus()) {
            return false;
        }
        if (f.getFallback() != null && t.isFallbackUtilizado() != f.getFallback()) {
            return false;
        }
        return true;
    }

    static String displayModel(String providerName) {
        if (providerName == null) {
            return "";
        }
        try {
            return AiRouterMetrics.displayName(
                com.consumoesperto.service.AiProviderType.valueOf(providerName.toUpperCase())
            );
        } catch (Exception e) {
            return providerName;
        }
    }
}
