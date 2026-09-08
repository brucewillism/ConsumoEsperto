package com.consumoesperto.controller;

import com.consumoesperto.edith.ConsumoRuntimeHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/runtime-health")
@RequiredArgsConstructor
public class ConsumoRuntimeHealthController {

    private final ConsumoRuntimeHealthService runtimeHealthService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(runtimeHealthService.snapshot());
    }
}
