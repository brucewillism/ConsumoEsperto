package com.consumoesperto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Leitura de eventos Google Calendar (escopo calendar.readonly) para o protocolo Cronos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");
    private static final List<String> KEYWORDS = List.of(
        "viagem", "ferias", "férias", "aniversario", "aniversário", "casamento"
    );

    private final UsuarioRepository usuarioRepository;
    private final RestTemplate restTemplate;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${consumoesperto.google.calendar.redirect-uri:http://localhost:8081/api/integracoes/google-calendar/oauth2/callback}")
    private String calendarRedirectUri;

    public Optional<String> obterRefreshToken(Long usuarioId) {
        return usuarioRepository.findById(usuarioId).map(Usuario::getGoogleCalendarRefreshToken).filter(s -> s != null && !s.isBlank());
    }

    /** Troca código OAuth (fluxo calendar) por tokens e persiste refresh no utilizador. */
    public void gravarRefreshTokenAPartirDoCodigo(Long usuarioId, String authorizationCode) {
        String refresh = exchangeCodeForRefreshToken(authorizationCode);
        Usuario u = usuarioRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        u.setGoogleCalendarRefreshToken(refresh);
        u.setGoogleCalendarLinkedAt(java.time.LocalDateTime.now());
        usuarioRepository.save(u);
    }

    private String exchangeCodeForRefreshToken(String code) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", code);
            form.add("client_id", googleClientId);
            form.add("client_secret", googleClientSecret);
            form.add("redirect_uri", calendarRedirectUri);
            form.add("grant_type", "authorization_code");
            ResponseEntity<String> response = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token",
                new HttpEntity<>(form, headers),
                String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Resposta inválida do Google OAuth");
            }
            JsonNode json = MAPPER.readTree(response.getBody());
            String refresh = json.path("refresh_token").asText("");
            if (refresh.isBlank()) {
                throw new IllegalStateException(
                    "Google não devolveu refresh_token. Refaça a vinculação com prompt=consent e access_type=offline.");
            }
            return refresh;
        } catch (Exception e) {
            log.warn("[CRONOS] Falha ao trocar código por tokens: {}", e.getMessage());
            throw new IllegalStateException("Não foi possível vincular o Google Calendar.", e);
        }
    }

    private String accessTokenFromRefresh(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", googleClientId);
        form.add("client_secret", googleClientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        ResponseEntity<String> response = restTemplate.postForEntity(
            "https://oauth2.googleapis.com/token",
            new HttpEntity<>(form, headers),
            String.class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Falha ao obter access token do Google Calendar");
        }
        try {
            JsonNode json = MAPPER.readTree(response.getBody());
            return json.path("access_token").asText("");
        } catch (Exception e) {
            throw new IllegalStateException("Token Google Calendar inválido ou expirado. Vincule novamente.", e);
        }
    }

    public String buildCalendarAuthorizationUrl(String state) {
        String scope = "https://www.googleapis.com/auth/calendar.readonly";
        return "https://accounts.google.com/o/oauth2/v2/auth"
            + "?client_id=" + urlEnc(googleClientId)
            + "&redirect_uri=" + urlEnc(calendarRedirectUri)
            + "&response_type=code"
            + "&scope=" + urlEnc(scope)
            + "&access_type=offline&prompt=consent&state=" + urlEnc(state);
    }

    private static String urlEnc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /**
     * Eventos relevantes entre amanhã e +{@code dias} dias (exclusivo do passado).
     */
    public List<EventoAgendaRelevante> listarEventosRelevantes(Long usuarioId, int dias) {
        Optional<String> refresh = obterRefreshToken(usuarioId);
        if (refresh.isEmpty()) {
            return List.of();
        }
        String access = accessTokenFromRefresh(refresh.get());
        ZonedDateTime inicio = ZonedDateTime.now(BR).truncatedTo(java.time.temporal.ChronoUnit.DAYS).plusDays(1);
        ZonedDateTime fim = inicio.plusDays(dias);
        String timeMin = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(inicio);
        String timeMax = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(fim);
        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events"
            + "?singleEvents=true&orderBy=startTime&timeMin=" + urlEnc(timeMin)
            + "&timeMax=" + urlEnc(timeMax);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(access);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
        List<EventoAgendaRelevante> out = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(resp.getBody());
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return out;
            }
            for (JsonNode it : items) {
                String summary = it.path("summary").asText("");
                if (!matchesKeyword(summary)) {
                    continue;
                }
                String id = it.path("id").asText("");
                JsonNode start = it.path("start");
                LocalDate dia = parseEventDate(start);
                if (dia == null) {
                    continue;
                }
                long dUntil = ChronoUnit.DAYS.between(LocalDate.now(BR), dia);
                if (dUntil < 1 || dUntil > dias) {
                    continue;
                }
                out.add(new EventoAgendaRelevante(id, summary.trim(), dia));
            }
        } catch (Exception e) {
            log.warn("[CRONOS] Falha ao ler eventos: {}", e.getMessage());
        }
        return out;
    }

    private static LocalDate parseEventDate(JsonNode start) {
        if (start == null || start.isMissingNode()) {
            return null;
        }
        String day = start.path("date").asText("");
        if (!day.isBlank()) {
            return LocalDate.parse(day);
        }
        String dateTime = start.path("dateTime").asText("");
        if (dateTime.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateTime).atZone(BR).toLocalDate();
        } catch (Exception e) {
            try {
                return ZonedDateTime.parse(dateTime).withZoneSameInstant(BR).toLocalDate();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static boolean matchesKeyword(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String n = normalize(text);
        for (String k : KEYWORDS) {
            if (n.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT)
            .replace('á', 'a').replace('à', 'a').replace('â', 'a').replace('ã', 'a')
            .replace('é', 'e').replace('ê', 'e')
            .replace('í', 'i')
            .replace('ó', 'o').replace('ô', 'o').replace('õ', 'o')
            .replace('ú', 'u').replace('ç', 'c');
        return s;
    }

    public record EventoAgendaRelevante(String idGoogle, String titulo, LocalDate dataInicio) {
    }
}
