package com.consumoesperto.edith.client;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adapter HTTP — contrato E.D.I.T.H. SDK 0.4.1 ({@code /api/v1/integrations/*}).
 */
@Component
@Slf4j
public class EdithHttpClient {

    private final EdithProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient javaHttpClient;

    public EdithHttpClient(EdithProperties properties, RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofMillis(properties.getRequestTimeoutMs());
        this.restTemplate = builder
            .setConnectTimeout(timeout)
            .setReadTimeout(timeout)
            .build();
        this.javaHttpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
    }

    public boolean isConfigured() {
        return properties.isEnabled()
            && properties.getBaseUrl() != null
            && !properties.getBaseUrl().isBlank()
            && properties.getApiKey() != null
            && !properties.getApiKey().isBlank();
    }

    public EdithApiModels.ConversationResponse createConversation(String title) {
        EdithApiModels.CreateConversationRequest body = new EdithApiModels.CreateConversationRequest();
        if (title != null && !title.isBlank()) {
            body.setTitle(title);
        }
        return exchange(HttpMethod.POST, EdithApiModels.Paths.CONVERSATIONS, body, null,
            EdithApiModels.ConversationResponse.class);
    }

    public EdithApiModels.MessageSubmission sendMessage(String conversationId, EdithApiModels.MessageSendRequest body) {
        Map<String, String> extra = null;
        if (body != null && body.getClientRequestId() != null && !body.getClientRequestId().isBlank()) {
            extra = Map.of("Idempotency-Key", body.getClientRequestId());
        }
        return exchange(HttpMethod.POST, EdithApiModels.Paths.conversationMessages(conversationId), body, extra,
            EdithApiModels.MessageSubmission.class);
    }

    public List<EdithApiModels.ConversationMessage> listMessages(String conversationId) {
        EdithApiModels.ConversationMessage[] arr = exchange(
            HttpMethod.GET,
            EdithApiModels.Paths.conversationMessages(conversationId),
            null,
            null,
            EdithApiModels.ConversationMessage[].class
        );
        return arr != null ? Arrays.asList(arr) : List.of();
    }

    public EdithApiModels.TaskResponse getTask(String taskId) {
        return exchange(HttpMethod.GET, EdithApiModels.Paths.task(taskId), null, null,
            EdithApiModels.TaskResponse.class);
    }

    public void streamTaskEvents(String taskId, String lastEventId, Consumer<EdithApiModels.TaskEvent> consumer) {
        if (!isConfigured()) {
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "E.D.I.T.H. não configurada");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + EdithApiModels.Paths.taskEvents(taskId)))
                .timeout(Duration.ofMillis(properties.getSse().getEmitterTimeoutMs()))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("X-API-Key", properties.getApiKey())
                .header("Accept", "text/event-stream");
            if (lastEventId != null && !lastEventId.isBlank()) {
                builder.header("Last-Event-ID", lastEventId);
            }
            HttpRequest request = builder.GET().build();
            HttpResponse<java.io.InputStream> response = javaHttpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE,
                    "SSE E.D.I.T.H. HTTP " + response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder data = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        data.append(line.substring(5).trim());
                    } else if (line.isBlank() && data.length() > 0) {
                        EdithApiModels.TaskEvent event = objectMapper.readValue(data.toString(),
                            EdithApiModels.TaskEvent.class);
                        consumer.accept(event);
                        data.setLength(0);
                    }
                }
            }
        } catch (EdithException e) {
            throw e;
        } catch (Exception e) {
            log.warn("edith_sse_upstream_error task_id={} error={}", taskId, e.getClass().getSimpleName());
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "SSE E.D.I.T.H. indisponível");
        }
    }

    public ResponseEntity<String> healthProbe() {
        try {
            HttpHeaders headers = authHeaders(null);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            return restTemplate.exchange(baseUrl() + EdithApiModels.Paths.HEALTH, HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("unavailable");
        }
    }

    private <T> T exchange(HttpMethod method, String path, Object body, Map<String, String> extraHeaders, Class<T> type) {
        if (!isConfigured()) {
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "E.D.I.T.H. não configurada");
        }
        long start = System.nanoTime();
        try {
            HttpHeaders headers = authHeaders(extraHeaders);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<?> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);
            ResponseEntity<T> response = restTemplate.exchange(baseUrl() + path, method, entity, type);
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            log.info("edith_transport method={} path={} status={} latency_ms={}",
                method, path, response.getStatusCodeValue(), latencyMs);
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.warn("edith_transport_error path={} error=connection", path);
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "E.D.I.T.H. indisponível");
        } catch (HttpStatusCodeException e) {
            log.warn("edith_transport_error path={} status={}", path, e.getStatusCode().value());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new EdithException(EdithErrorCode.EDITH_AUTH_FAILED, "Falha de autenticação com E.D.I.T.H.");
            }
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new EdithException(EdithErrorCode.EDITH_RATE_LIMITED, "Limite de taxa E.D.I.T.H.");
            }
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE,
                "E.D.I.T.H. retornou erro HTTP " + e.getStatusCode().value());
        } catch (EdithException e) {
            throw e;
        } catch (Exception e) {
            log.warn("edith_transport_error path={} error={}", path, e.getClass().getSimpleName());
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "E.D.I.T.H. indisponível");
        }
    }

    private HttpHeaders authHeaders(Map<String, String> extra) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getApiKey());
        headers.set("X-API-Key", properties.getApiKey());
        if (extra != null) {
            extra.forEach(headers::set);
        }
        return headers;
    }

    private String baseUrl() {
        String url = properties.getBaseUrl().trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
