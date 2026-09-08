package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.client.EdithApiModels;
import com.consumoesperto.edith.client.EdithHttpClient;
import com.consumoesperto.model.EdithTaskLink;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class EdithSseService {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    private final EdithProperties properties;
    private final EdithIntegrationService integrationService;
    private final EdithHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "edith-sse");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long usuarioId, String taskId) {
        return subscribe(usuarioId, taskId, null);
    }

    public SseEmitter subscribe(Long usuarioId, String taskId, String lastEventId) {
        EdithTaskLink link = integrationService.requireTaskOwned(usuarioId, taskId);
        SseEmitter emitter = new SseEmitter(properties.getSse().getEmitterTimeoutMs());
        String key = usuarioId + ":" + taskId;
        activeEmitters.put(key, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(key));
        emitter.onTimeout(() -> {
            activeEmitters.remove(key);
            emitter.complete();
        });
        emitter.onError(e -> activeEmitters.remove(key));

        executor.submit(() -> relayFromEdith(usuarioId, taskId, link, emitter, key, lastEventId));
        return emitter;
    }

    private void relayFromEdith(Long usuarioId, String taskId, EdithTaskLink link, SseEmitter emitter, String key, String lastEventId) {
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicInteger seq = new AtomicInteger(0);
        try {
            sendEvent(emitter, seq, link.getStatus() != null ? link.getStatus() : "QUEUED", Map.of("taskId", taskId));

            EdithApiModels.TaskResponse current = integrationService.fetchTask(usuarioId, taskId);
            if (current != null && TERMINAL.contains(EdithIntegrationService.normalizeStatus(current.getStatus()))) {
                completeTerminal(emitter, seq, taskId, current);
                done.set(true);
                return;
            }

            httpClient.streamTaskEvents(taskId, lastEventId, event -> {
                if (!activeEmitters.containsKey(key) || done.get()) {
                    return;
                }
                String status = EdithIntegrationService.normalizeStatus(event.resolvedStatus());
                integrationService.updateTaskStatus(link, status);
                try {
                    sendEvent(emitter, seq, status, payloadFromEvent(taskId, event));
                    if (TERMINAL.contains(status)) {
                        if ("COMPLETED".equals(status)) {
                            EdithApiModels.TaskResponse task = integrationService.fetchTask(usuarioId, taskId);
                            String result = task != null && task.getResult() != null ? task.getResult() : "";
                            Map<String, Object> donePayload = new LinkedHashMap<>();
                            donePayload.put("taskId", taskId);
                            donePayload.put("result", result);
                            sendEvent(emitter, seq, "COMPLETED", donePayload);
                        }
                        emitter.complete();
                        activeEmitters.remove(key);
                        done.set(true);
                    }
                } catch (IOException e) {
                    done.set(true);
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.warn("edith_sse_error task_id={} error={}", taskId, e.getClass().getSimpleName());
            try {
                sendEvent(emitter, "FAILED", Map.of("taskId", taskId, "error", "EDITH_UNAVAILABLE"));
            } catch (IOException ignored) {
                // ignore
            }
            emitter.completeWithError(e);
        } finally {
            activeEmitters.remove(key);
        }
    }

    private void completeTerminal(
        SseEmitter emitter,
        AtomicInteger seq,
        String taskId,
        EdithApiModels.TaskResponse task
    ) throws IOException {
        String status = EdithIntegrationService.normalizeStatus(task.getStatus());
        sendEvent(emitter, seq, status, Map.of("taskId", taskId));
        if ("COMPLETED".equals(status)) {
            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("taskId", taskId);
            donePayload.put("result", task.getResult() != null ? task.getResult() : "");
            sendEvent(emitter, seq, "COMPLETED", donePayload);
        }
        emitter.complete();
    }

    private static Map<String, Object> payloadFromEvent(String taskId, EdithApiModels.TaskEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", taskId);
        if (event.getType() != null) {
            data.put("type", event.getType());
        }
        if (event.getData() != null) {
            Object delta = firstPresent(event.getData(), "delta", "token", "chunk", "partial");
            if (delta != null) {
                data.put("delta", String.valueOf(delta));
            }
            Object result = event.getData().get("result");
            if (result != null) {
                data.put("result", String.valueOf(result));
            }
        }
        return data;
    }

    private static Object firstPresent(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void sendEvent(SseEmitter emitter, String status, Map<String, Object> data) throws IOException {
        sendEvent(emitter, new AtomicInteger(0), status, data);
    }

    private void sendEvent(SseEmitter emitter, AtomicInteger seq, String status, Map<String, Object> data) throws IOException {
        Map<String, Object> payload = Map.of("status", status, "data", data);
        emitter.send(SseEmitter.event()
            .id(String.valueOf(seq.incrementAndGet()))
            .name("edith")
            .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
    }
}
