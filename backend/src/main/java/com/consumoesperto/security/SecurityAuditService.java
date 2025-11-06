package com.consumoesperto.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Serviço de Auditoria de Segurança
 * 
 * Este serviço registra todas as ações sensíveis relacionadas à segurança,
 * incluindo tentativas de login, acessos a recursos protegidos e operações
 * administrativas.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {

    @Value("${security.audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${security.audit.log-failed-logins:true}")
    private boolean logFailedLogins;

    @Value("${security.audit.log-successful-logins:true}")
    private boolean logSuccessfulLogins;

    @Value("${security.audit.log-sensitive-operations:true}")
    private boolean logSensitiveOperations;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Registra tentativa de login bem-sucedida
     */
    public void logSuccessfulLogin(String username, String ipAddress, String userAgent) {
        if (!auditEnabled || !logSuccessfulLogins) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        log.info("🔐 [AUDIT] LOGIN SUCESSO | {} | Usuario: {} | IP: {} | User-Agent: {}",
                timestamp, username, ipAddress, userAgent);
    }

    /**
     * Registra tentativa de login falhada
     */
    public void logFailedLogin(String username, String ipAddress, String userAgent, String reason) {
        if (!auditEnabled || !logFailedLogins) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        log.warn("❌ [AUDIT] LOGIN FALHA | {} | Usuario: {} | IP: {} | User-Agent: {} | Motivo: {}",
                timestamp, username, ipAddress, userAgent, reason);
    }

    /**
     * Registra acesso a recurso protegido
     */
    public void logResourceAccess(String username, String resource, String method, String ipAddress) {
        if (!auditEnabled || !logSensitiveOperations) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        log.info("🔓 [AUDIT] ACESSO RECURSO | {} | Usuario: {} | Recurso: {} | Método: {} | IP: {}",
                timestamp, username, resource, method, ipAddress);
    }

    /**
     * Registra operação sensível (criação, edição, exclusão)
     */
    public void logSensitiveOperation(String username, String operation, String resource, String details, String ipAddress) {
        if (!auditEnabled || !logSensitiveOperations) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        log.info("⚠️ [AUDIT] OPERAÇÃO SENSÍVEL | {} | Usuario: {} | Operação: {} | Recurso: {} | Detalhes: {} | IP: {}",
                timestamp, username, operation, resource, details, ipAddress);
    }

    /**
     * Registra tentativa de acesso não autorizado
     */
    public void logUnauthorizedAccess(String username, String resource, String method, String ipAddress, String reason) {
        if (!auditEnabled) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        log.warn("🚫 [AUDIT] ACESSO NÃO AUTORIZADO | {} | Usuario: {} | Recurso: {} | Método: {} | IP: {} | Motivo: {}",
                timestamp, username, resource, method, ipAddress, reason);
    }

    /**
     * Registra logout do usuário
     */
    public void logLogout(String username, String ipAddress) {
        if (!auditEnabled) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        log.info("🚪 [AUDIT] LOGOUT | {} | Usuario: {} | IP: {}",
                timestamp, username, ipAddress);
    }

    /**
     * Registra alteração de senha
     */
    public void logPasswordChange(String username, String ipAddress) {
        if (!auditEnabled || !logSensitiveOperations) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        log.info("🔑 [AUDIT] ALTERAÇÃO SENHA | {} | Usuario: {} | IP: {}",
                timestamp, username, ipAddress);
    }

    /**
     * Registra tentativa de alteração de senha falhada
     */
    public void logFailedPasswordChange(String username, String ipAddress, String reason) {
        if (!auditEnabled || !logFailedLogins) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        log.warn("❌ [AUDIT] FALHA ALTERAÇÃO SENHA | {} | Usuario: {} | IP: {} | Motivo: {}",
                timestamp, username, ipAddress, reason);
    }

    /**
     * Extrai informações da requisição HTTP
     */
    public String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Extrai User-Agent da requisição
     */
    public String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "N/A";
    }
}
