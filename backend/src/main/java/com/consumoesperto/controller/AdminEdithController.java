package com.consumoesperto.controller;

import com.consumoesperto.edith.EdithAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/edith")
@RequiredArgsConstructor
public class AdminEdithController {

    private final EdithAdminService edithAdminService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(edithAdminService.adminStatus());
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> toggle(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body != null ? body.get("enabled") : null;
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Campo enabled obrigatório"));
        }
        return ResponseEntity.ok(edithAdminService.setEnabled(enabled));
    }
}
