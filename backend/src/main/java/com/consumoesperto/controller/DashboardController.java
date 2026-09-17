package com.consumoesperto.controller;

import com.consumoesperto.dashboard.DashboardViewDTO;
import com.consumoesperto.dashboard.DashboardViewMode;
import com.consumoesperto.dashboard.DashboardViewService;
import com.consumoesperto.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class DashboardController {

    private final DashboardViewService dashboardViewService;

    /**
     * Sem {@code view} devolve {@code GENERAL} (compatível com consumidores antigos).
     * A UI envia explicitamente {@code MONTHLY} ou {@code GENERAL}.
     */
    @GetMapping
    public ResponseEntity<DashboardViewDTO> obter(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(name = "view", required = false) String view
    ) {
        DashboardViewMode mode = DashboardViewMode.from(view);
        return ResponseEntity.ok(dashboardViewService.montar(user.getId(), mode));
    }

    @GetMapping("/preferencia")
    public ResponseEntity<Map<String, Object>> lerPreferencia(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(dashboardViewService.lerPreferencia(user.getId()));
    }

    @PutMapping("/preferencia")
    public ResponseEntity<Map<String, String>> preferencia(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody Map<String, String> body
    ) {
        DashboardViewMode mode = DashboardViewMode.from(body == null ? null : body.get("viewMode"));
        DashboardViewMode saved = dashboardViewService.persistirPreferencia(user.getId(), mode);
        return ResponseEntity.ok(Map.of("viewMode", saved.name()));
    }
}
