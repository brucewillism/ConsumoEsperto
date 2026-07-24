package com.consumoesperto.controller;

import com.consumoesperto.model.ai.AiTraceStatus;
import com.consumoesperto.service.ai.AITaskType;
import com.consumoesperto.service.ai.AiRouterService;
import com.consumoesperto.service.ai.analytics.AiRouterAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai/router")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class AdminAiRouterController {

    private final AiRouterService aiRouterService;
    private final AiRouterAdminService adminService;

    /** Legado — métricas agregadas em memória. */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(aiRouterService.metricsSnapshot());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
        @RequestParam(required = false) String modelo,
        @RequestParam(required = false) AITaskType taskType,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) AiTraceStatus status,
        @RequestParam(required = false) Boolean fallback
    ) {
        var filter = AiRouterAdminService.filterFromParams(desde, ate, modelo, taskType, userId, status, fallback);
        return ResponseEntity.ok(adminService.dashboard(filter));
    }

    @GetMapping("/traces")
    public ResponseEntity<List<Map<String, Object>>> traces(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
        @RequestParam(required = false) String modelo,
        @RequestParam(required = false) AITaskType taskType,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) AiTraceStatus status,
        @RequestParam(required = false) Boolean fallback,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        var filter = AiRouterAdminService.filterFromParams(desde, ate, modelo, taskType, userId, status, fallback);
        return ResponseEntity.ok(adminService.traces(filter, limit, offset));
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> models(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
        @RequestParam(required = false) String modelo,
        @RequestParam(required = false) AITaskType taskType,
        @RequestParam(required = false) Long userId
    ) {
        var filter = AiRouterAdminService.filterFromParams(desde, ate, modelo, taskType, userId, null, null);
        return ResponseEntity.ok(adminService.models(filter));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<Map<String, Object>>> recommendations() {
        return ResponseEntity.ok(adminService.recommendations());
    }
}
