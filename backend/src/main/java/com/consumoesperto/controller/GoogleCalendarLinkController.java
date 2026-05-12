package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.GoogleCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2 incremental para Google Calendar (read-only). O login Google padrão não inclui este escopo.
 */
@RestController
@RequestMapping("/api/integracoes/google-calendar")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class GoogleCalendarLinkController {

    private static final ConcurrentHashMap<String, Long> OAUTH_STATE_USER = new ConcurrentHashMap<>();

    private final GoogleCalendarService googleCalendarService;

    @Value("${app.frontend-base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @GetMapping("/iniciar")
    public ResponseEntity<Map<String, String>> iniciar(@AuthenticationPrincipal UserPrincipal user) {
        String state = UUID.randomUUID().toString();
        OAUTH_STATE_USER.put(state, user.getId());
        String url = googleCalendarService.buildCalendarAuthorizationUrl(state);
        return ResponseEntity.ok(Map.of("authorizationUrl", url));
    }

    @GetMapping("/oauth2/callback")
    public RedirectView callback(
        @RequestParam("code") String code,
        @RequestParam("state") String state
    ) {
        Long userId = OAUTH_STATE_USER.remove(state);
        if (userId == null) {
            return new RedirectView(frontendBaseUrl + "/perfil?calendar=erro_estado");
        }
        try {
            googleCalendarService.gravarRefreshTokenAPartirDoCodigo(userId, code);
            return new RedirectView(frontendBaseUrl + "/perfil?calendar=ok");
        } catch (Exception e) {
            return new RedirectView(frontendBaseUrl + "/perfil?calendar=erro_troca");
        }
    }
}
