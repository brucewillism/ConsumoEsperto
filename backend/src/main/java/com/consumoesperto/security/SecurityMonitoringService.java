package com.consumoesperto.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço de Monitoramento de Segurança
 * 
 * Este serviço monitora atividades suspeitas e gera alertas
 * para possíveis tentativas de ataque ou comportamento anômalo.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityMonitoringService {

    @Value("${security.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${security.monitoring.max-failed-logins:10}")
    private int maxFailedLogins;

    @Value("${security.monitoring.max-unauthorized-access:20}")
    private int maxUnauthorizedAccess;

    @Value("${security.monitoring.max-suspicious-ips:5}")
    private int maxSuspiciousIps;

    @Value("${security.monitoring.alert-threshold:5}")
    private int alertThreshold;

    // Contadores de segurança
    private final Map<String, AtomicInteger> failedLoginAttempts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> unauthorizedAccessAttempts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> suspiciousActivityCount = new ConcurrentHashMap<>();
    
    // Histórico de alertas
    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Registra tentativa de login falhada
     */
    public void recordFailedLogin(String username, String ipAddress) {
        if (!monitoringEnabled) return;

        String key = username + "@" + ipAddress;
        int attempts = failedLoginAttempts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        log.warn("🚫 Tentativa de login falhada #{} | Usuario: {} | IP: {}", attempts, username, ipAddress);

        // Verifica se deve gerar alerta
        if (attempts >= alertThreshold) {
            generateSecurityAlert("FAILED_LOGIN", username, ipAddress, attempts);
        }

        // Verifica se deve bloquear
        if (attempts >= maxFailedLogins) {
            blockUser(username, ipAddress, "Muitas tentativas de login falhadas");
        }
    }

    /**
     * Registra tentativa de acesso não autorizado
     */
    public void recordUnauthorizedAccess(String username, String ipAddress, String resource) {
        if (!monitoringEnabled) return;

        String key = username + "@" + ipAddress;
        int attempts = unauthorizedAccessAttempts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        log.warn("🚫 Tentativa de acesso não autorizado #{} | Usuario: {} | IP: {} | Recurso: {}", 
                attempts, username, ipAddress, resource);

        // Verifica se deve gerar alerta
        if (attempts >= alertThreshold) {
            generateSecurityAlert("UNAUTHORIZED_ACCESS", username, ipAddress, attempts);
        }

        // Verifica se deve bloquear
        if (attempts >= maxUnauthorizedAccess) {
            blockUser(username, ipAddress, "Muitas tentativas de acesso não autorizado");
        }
    }

    /**
     * Registra atividade suspeita
     */
    public void recordSuspiciousActivity(String ipAddress, String activity, String details) {
        if (!monitoringEnabled) return;

        int count = suspiciousActivityCount.computeIfAbsent(ipAddress, k -> new AtomicInteger(0)).incrementAndGet();

        log.warn("⚠️ Atividade suspeita #{} | IP: {} | Atividade: {} | Detalhes: {}", 
                count, ipAddress, activity, details);

        // Verifica se deve gerar alerta
        if (count >= alertThreshold) {
            generateSecurityAlert("SUSPICIOUS_ACTIVITY", "ANONYMOUS", ipAddress, count);
        }

        // Verifica se deve bloquear IP
        if (count >= maxSuspiciousIps) {
            blockIp(ipAddress, "Muitas atividades suspeitas detectadas");
        }
    }

    /**
     * Registra tentativa de ataque
     */
    public void recordAttackAttempt(String attackType, String ipAddress, String details) {
        if (!monitoringEnabled) return;

        log.error("🔥 TENTATIVA DE ATAQUE DETECTADA | Tipo: {} | IP: {} | Detalhes: {}", 
                attackType, ipAddress, details);

        // Gera alerta imediato
        generateSecurityAlert("ATTACK_ATTEMPT", "ANONYMOUS", ipAddress, 1);

        // Bloqueia IP imediatamente
        blockIp(ipAddress, "Tentativa de ataque detectada: " + attackType);
    }

    /**
     * Gera alerta de segurança
     */
    private void generateSecurityAlert(String alertType, String username, String ipAddress, int count) {
        String alertKey = alertType + "_" + username + "_" + ipAddress;
        LocalDateTime now = LocalDateTime.now();

        // Evita spam de alertas (máximo 1 por hora)
        if (lastAlertTime.containsKey(alertKey)) {
            LocalDateTime lastAlert = lastAlertTime.get(alertKey);
            if (now.isBefore(lastAlert.plusHours(1))) {
                return;
            }
        }

        lastAlertTime.put(alertKey, now);

        String timestamp = now.format(FORMATTER);
        
        // Log de alerta
        log.error("🚨 ALERTA DE SEGURANÇA | {} | {} | Usuario: {} | IP: {} | Contagem: {} | Timestamp: {}", 
                "🚨", alertType, username, ipAddress, count, timestamp);

        // Aqui você pode implementar notificações adicionais:
        // - Email para administradores
        // - Slack/Discord webhook
        // - Sistema de tickets
        // - Integração com SIEM
    }

    /**
     * Bloqueia usuário
     */
    private void blockUser(String username, String ipAddress, String reason) {
        log.error("🚫 USUÁRIO BLOQUEADO | Usuario: {} | IP: {} | Motivo: {}", username, ipAddress, reason);
        
        // Aqui você pode implementar:
        // - Bloqueio no banco de dados
        // - Adição em lista negra
        // - Notificação para administradores
        // - Log de auditoria
    }

    /**
     * Bloqueia IP
     */
    private void blockIp(String ipAddress, String reason) {
        log.error("🚫 IP BLOQUEADO | IP: {} | Motivo: {}", ipAddress, reason);
        
        // Aqui você pode implementar:
        // - Bloqueio no firewall
        // - Adição em lista negra
        // - Notificação para administradores
        // - Log de auditoria
    }

    /**
     * Reseta contadores de segurança (executado diariamente)
     */
    @Scheduled(cron = "0 0 0 * * ?") // Meia-noite todos os dias
    public void resetSecurityCounters() {
        if (!monitoringEnabled) return;

        log.info("🔄 Resetando contadores de segurança diários");
        
        failedLoginAttempts.clear();
        unauthorizedAccessAttempts.clear();
        suspiciousActivityCount.clear();
        lastAlertTime.clear();
    }

    /**
     * Gera relatório de segurança (executado a cada hora)
     */
    @Scheduled(cron = "0 0 * * * ?") // A cada hora
    public void generateSecurityReport() {
        if (!monitoringEnabled) return;

        int totalFailedLogins = failedLoginAttempts.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        int totalUnauthorizedAccess = unauthorizedAccessAttempts.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        int totalSuspiciousActivity = suspiciousActivityCount.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        if (totalFailedLogins > 0 || totalUnauthorizedAccess > 0 || totalSuspiciousActivity > 0) {
            log.info("📊 RELATÓRIO DE SEGURANÇA | Tentativas de login falhadas: {} | Acessos não autorizados: {} | Atividades suspeitas: {}", 
                    totalFailedLogins, totalUnauthorizedAccess, totalSuspiciousActivity);
        }
    }

    /**
     * Verifica se IP está bloqueado
     */
    public boolean isIpBlocked(String ipAddress) {
        if (!monitoringEnabled) return false;

        // Aqui você pode implementar verificação de IP bloqueado
        // - Consulta em lista negra
        // - Verificação no banco de dados
        // - Verificação em cache Redis
        
        return false; // Implementar lógica real
    }

    /**
     * Verifica se usuário está bloqueado
     */
    public boolean isUserBlocked(String username) {
        if (!monitoringEnabled) return false;

        // Aqui você pode implementar verificação de usuário bloqueado
        // - Consulta no banco de dados
        // - Verificação de status da conta
        
        return false; // Implementar lógica real
    }

    /**
     * Obtém estatísticas de segurança
     */
    public Map<String, Object> getSecurityStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        stats.put("monitoringEnabled", monitoringEnabled);
        stats.put("totalFailedLogins", failedLoginAttempts.values().stream().mapToInt(AtomicInteger::get).sum());
        stats.put("totalUnauthorizedAccess", unauthorizedAccessAttempts.values().stream().mapToInt(AtomicInteger::get).sum());
        stats.put("totalSuspiciousActivity", suspiciousActivityCount.values().stream().mapToInt(AtomicInteger::get).sum());
        stats.put("uniqueIpsWithFailedLogins", failedLoginAttempts.size());
        stats.put("uniqueIpsWithUnauthorizedAccess", unauthorizedAccessAttempts.size());
        stats.put("uniqueSuspiciousIps", suspiciousActivityCount.size());
        
        return stats;
    }
}
