package com.consumoesperto.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller personalizado para tratamento de erros HTTP
 * 
 * Este controller evita problemas de autenticação em endpoints de erro
 * e fornece respostas de erro consistentes para a aplicação.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Slf4j
@RestController
public class CustomErrorController implements ErrorController {

    /**
     * Trata todos os erros HTTP e retorna uma resposta JSON consistente
     * 
     * @param request Requisição HTTP que causou o erro
     * @return ResponseEntity com detalhes do erro
     */
    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Object status = request.getAttribute("javax.servlet.error.status_code");
        Object message = request.getAttribute("javax.servlet.error.message");
        Object path = request.getAttribute("javax.servlet.error.request_uri");
        
        log.debug("Tratando erro HTTP: status={}, message={}, path={}", status, message, path);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", System.currentTimeMillis());
        errorResponse.put("status", status != null ? status : "UNKNOWN");
        errorResponse.put("message", message != null ? message : "Erro desconhecido");
        errorResponse.put("path", path != null ? path : "N/A");
        
        // Determina o status HTTP apropriado
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if (status != null) {
            try {
                httpStatus = HttpStatus.valueOf(Integer.parseInt(status.toString()));
            } catch (NumberFormatException e) {
                log.warn("Status HTTP inválido: {}", status);
            }
        }
        
        // Log apropriado baseado no tipo de erro
        if (httpStatus.is4xxClientError()) {
            log.debug("Erro do cliente: {} - {}", httpStatus, path);
        } else if (httpStatus.is5xxServerError()) {
            log.warn("Erro do servidor: {} - {}", httpStatus, path);
        }
        
        return ResponseEntity.status(httpStatus).body(errorResponse);
    }
}
