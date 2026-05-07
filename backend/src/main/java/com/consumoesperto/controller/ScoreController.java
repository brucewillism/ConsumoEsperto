package com.consumoesperto.controller;

import com.consumoesperto.dto.UsuarioScoreDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/score")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class ScoreController {

    private final ScoreService scoreService;

    @GetMapping
    public ResponseEntity<UsuarioScoreDTO> obter(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(scoreService.obter(user.getId()));
    }

    @GetMapping("/historico")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> historico(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(scoreService.historico(user.getId()));
    }
}
