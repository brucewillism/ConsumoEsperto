package com.consumoesperto.service.jarvis;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JarvisPipelineTrace {

    private final Long userId;
    private final String canal;
    private final long startNs;
    private String route = "UNKNOWN";
    private final Map<String, Long> stages = new LinkedHashMap<>();

    JarvisPipelineTrace(Long userId, String canal, long startNs) {
        this.userId = userId;
        this.canal = canal;
        this.startNs = startNs;
    }

    public void route(String route) {
        this.route = route;
    }

    public void record(String stage, long durationMs) {
        stages.put(stage, durationMs);
    }

    public long totalMs() {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    public Long userId() {
        return userId;
    }

    public String canal() {
        return canal;
    }

    public String route() {
        return route;
    }

    public Map<String, Long> stages() {
        return stages;
    }
}
