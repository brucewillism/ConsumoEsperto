package com.consumoesperto.controller;

import com.consumoesperto.service.ExportacaoDadosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.consumoesperto.security.UserPrincipal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Controller para exportação de dados
 * 
 * Este controller permite exportar dados financeiros em diferentes formatos
 * como CSV, JSON, XML, etc.
 */
@RestController
@RequestMapping("/api/exportacao")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "*"})
public class ExportacaoDadosController {

    private final ExportacaoDadosService exportacaoDadosService;

    /**
     * Exporta dados completos em CSV
     */
    @GetMapping("/csv/completo")
    public ResponseEntity<byte[]> exportarDadosCompletosCsv(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {
        
        try {
            log.info("📤 Exportando dados completos em CSV para usuário: {}", currentUser.getId());
            
            // Definir período padrão se não informado
            LocalDate inicio = dataInicio != null ? 
                LocalDate.parse(dataInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now().minusMonths(1);
                
            LocalDate fim = dataFim != null ? 
                LocalDate.parse(dataFim, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now();
            
            // Exportar dados
            byte[] csvBytes = exportacaoDadosService.exportarDadosCompletosCsv(
                currentUser.getId(), inicio, fim);
            
            // Configurar headers para download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", 
                "dados-financeiros-completos-" + currentUser.getId() + ".csv");
            headers.setContentLength(csvBytes.length);
            
            log.info("✅ Dados completos exportados em CSV com sucesso - {} bytes", csvBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
                
        } catch (Exception e) {
            log.error("❌ Erro ao exportar dados completos em CSV: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Exporta transações em CSV
     */
    @GetMapping("/csv/transacoes")
    public ResponseEntity<byte[]> exportarTransacoesCsv(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {
        
        try {
            log.info("💳 Exportando transações em CSV para usuário: {}", currentUser.getId());
            
            // Definir período padrão se não informado
            LocalDate inicio = dataInicio != null ? 
                LocalDate.parse(dataInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now().minusMonths(1);
                
            LocalDate fim = dataFim != null ? 
                LocalDate.parse(dataFim, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now();
            
            // Exportar dados
            byte[] csvBytes = exportacaoDadosService.exportarTransacoesCsv(
                currentUser.getId(), inicio, fim);
            
            // Configurar headers para download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", 
                "transacoes-" + currentUser.getId() + ".csv");
            headers.setContentLength(csvBytes.length);
            
            log.info("✅ Transações exportadas em CSV com sucesso - {} bytes", csvBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
                
        } catch (Exception e) {
            log.error("❌ Erro ao exportar transações em CSV: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Exporta dados em JSON
     */
    @GetMapping("/json")
    public ResponseEntity<byte[]> exportarDadosJson(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {
        
        try {
            log.info("📤 Exportando dados em JSON para usuário: {}", currentUser.getId());
            
            // Definir período padrão se não informado
            LocalDate inicio = dataInicio != null ? 
                LocalDate.parse(dataInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now().minusMonths(1);
                
            LocalDate fim = dataFim != null ? 
                LocalDate.parse(dataFim, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now();
            
            // Exportar dados
            byte[] jsonBytes = exportacaoDadosService.exportarDadosJson(
                currentUser.getId(), inicio, fim);
            
            // Configurar headers para download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", 
                "dados-financeiros-" + currentUser.getId() + ".json");
            headers.setContentLength(jsonBytes.length);
            
            log.info("✅ Dados exportados em JSON com sucesso - {} bytes", jsonBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(jsonBytes);
                
        } catch (Exception e) {
            log.error("❌ Erro ao exportar dados em JSON: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Exporta dados em XML
     */
    @GetMapping("/xml")
    public ResponseEntity<byte[]> exportarDadosXml(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {
        
        try {
            log.info("📤 Exportando dados em XML para usuário: {}", currentUser.getId());
            
            // Definir período padrão se não informado
            LocalDate inicio = dataInicio != null ? 
                LocalDate.parse(dataInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now().minusMonths(1);
                
            LocalDate fim = dataFim != null ? 
                LocalDate.parse(dataFim, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now();
            
            // Exportar dados
            byte[] xmlBytes = exportacaoDadosService.exportarDadosXml(
                currentUser.getId(), inicio, fim);
            
            // Configurar headers para download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setContentDispositionFormData("attachment", 
                "dados-financeiros-" + currentUser.getId() + ".xml");
            headers.setContentLength(xmlBytes.length);
            
            log.info("✅ Dados exportados em XML com sucesso - {} bytes", xmlBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(xmlBytes);
                
        } catch (Exception e) {
            log.error("❌ Erro ao exportar dados em XML: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lista formatos de exportação disponíveis
     */
    @GetMapping("/formatos")
    public ResponseEntity<Object> listarFormatosDisponiveis(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("📋 Listando formatos de exportação disponíveis para usuário: {}", currentUser.getId());
            
            Object formatos = Map.of(
                "formatos", List.of(
                    Map.of(
                        "id", "csv-completo",
                        "nome", "CSV Completo",
                        "descricao", "Exporta todos os dados financeiros em formato CSV",
                        "endpoint", "/api/exportacao/csv/completo",
                        "parametros", List.of("dataInicio", "dataFim"),
                        "mime_type", "text/plain",
                        "extensao", ".csv"
                    ),
                    Map.of(
                        "id", "csv-transacoes",
                        "nome", "CSV Transações",
                        "descricao", "Exporta apenas as transações em formato CSV",
                        "endpoint", "/api/exportacao/csv/transacoes",
                        "parametros", List.of("dataInicio", "dataFim"),
                        "mime_type", "text/plain",
                        "extensao", ".csv"
                    ),
                    Map.of(
                        "id", "json",
                        "nome", "JSON",
                        "descricao", "Exporta todos os dados financeiros em formato JSON",
                        "endpoint", "/api/exportacao/json",
                        "parametros", List.of("dataInicio", "dataFim"),
                        "mime_type", "application/json",
                        "extensao", ".json"
                    ),
                    Map.of(
                        "id", "xml",
                        "nome", "XML",
                        "descricao", "Exporta todos os dados financeiros em formato XML",
                        "endpoint", "/api/exportacao/xml",
                        "parametros", List.of("dataInicio", "dataFim"),
                        "mime_type", "application/xml",
                        "extensao", ".xml"
                    )
                ),
                "instrucoes", Map.of(
                    "formato_data", "yyyy-MM-dd",
                    "exemplo_data_inicio", "2024-01-01",
                    "exemplo_data_fim", "2024-12-31",
                    "periodo_padrao", "Último mês se não informado",
                    "observacao", "Todos os formatos incluem dados do período especificado"
                )
            );
            
            return ResponseEntity.ok(formatos);
            
        } catch (Exception e) {
            log.error("❌ Erro ao listar formatos de exportação: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
