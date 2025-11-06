package com.consumoesperto.controller;

import com.consumoesperto.service.BackupAutomaticoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.consumoesperto.security.UserPrincipal;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Controller para gerenciar backups automáticos
 * 
 * Este controller permite executar backups manuais,
 * listar backups disponíveis e restaurar backups.
 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "*"})
public class BackupController {

    private final BackupAutomaticoService backupAutomaticoService;

    /**
     * Executa backup manual
     */
    @PostMapping("/executar")
    public ResponseEntity<Map<String, Object>> executarBackupManual(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("🔄 Executando backup manual para usuário: {}", currentUser.getId());
            
            String backupName = backupAutomaticoService.executarBackupManual();
            
            Map<String, Object> response = Map.of(
                "sucesso", true,
                "mensagem", "Backup manual executado com sucesso",
                "nome_backup", backupName,
                "data_execucao", java.time.LocalDateTime.now().toString()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao executar backup manual: {}", e.getMessage(), e);
            
            Map<String, Object> response = Map.of(
                "sucesso", false,
                "erro", "Erro ao executar backup manual: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Lista backups disponíveis
     */
    @GetMapping("/listar")
    public ResponseEntity<List<Map<String, Object>>> listarBackupsDisponiveis(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("📋 Listando backups disponíveis para usuário: {}", currentUser.getId());
            
            List<Map<String, Object>> backups = backupAutomaticoService.listarBackupsDisponiveis();
            
            return ResponseEntity.ok(backups);
            
        } catch (Exception e) {
            log.error("❌ Erro ao listar backups disponíveis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Baixa arquivo de backup
     */
    @GetMapping("/baixar/{backupName}")
    public ResponseEntity<byte[]> baixarBackup(
            @PathVariable String backupName,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("📥 Baixando backup: {} para usuário: {}", backupName, currentUser.getId());
            
            String zipFile = "./backups/" + backupName;
            if (!zipFile.endsWith(".zip")) {
                zipFile += ".zip";
            }
            
            File backupFile = new File(zipFile);
            if (!backupFile.exists()) {
                log.error("❌ Arquivo de backup não encontrado: {}", zipFile);
                return ResponseEntity.notFound().build();
            }
            
            byte[] fileBytes = Files.readAllBytes(backupFile.toPath());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", backupName);
            headers.setContentLength(fileBytes.length);
            
            log.info("✅ Backup baixado com sucesso: {} - {} bytes", backupName, fileBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
                
        } catch (Exception e) {
            log.error("❌ Erro ao baixar backup: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Restaura backup
     */
    @PostMapping("/restaurar/{backupName}")
    public ResponseEntity<Map<String, Object>> restaurarBackup(
            @PathVariable String backupName,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("🔄 Restaurando backup: {} para usuário: {}", backupName, currentUser.getId());
            
            boolean sucesso = backupAutomaticoService.restaurarBackup(backupName);
            
            Map<String, Object> response = Map.of(
                "sucesso", sucesso,
                "mensagem", sucesso ? "Backup restaurado com sucesso" : "Erro ao restaurar backup",
                "nome_backup", backupName,
                "data_restauracao", java.time.LocalDateTime.now().toString()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao restaurar backup: {}", e.getMessage(), e);
            
            Map<String, Object> response = Map.of(
                "sucesso", false,
                "erro", "Erro ao restaurar backup: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Obtém informações sobre o sistema de backup
     */
    @GetMapping("/informacoes")
    public ResponseEntity<Map<String, Object>> obterInformacoesBackup(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("ℹ️ Obtendo informações do sistema de backup para usuário: {}", currentUser.getId());
            
            Map<String, Object> informacoes = Map.of(
                "sistema_backup", Map.of(
                    "nome", "Sistema de Backup Automático - ConsumoEsperto",
                    "versao", "1.0.0",
                    "descricao", "Sistema de backup automático que inclui banco de dados, configurações e logs"
                ),
                "configuracoes", Map.of(
                    "diretorio_backup", "./backups",
                    "frequencia_automatica", "Diário às 2:00 AM",
                    "max_backups", 30,
                    "tipos_backup", List.of("Banco de dados", "Configurações", "Logs")
                ),
                "funcionalidades", Map.of(
                    "backup_automatico", "Executado automaticamente todos os dias às 2:00 AM",
                    "backup_manual", "Pode ser executado manualmente a qualquer momento",
                    "restauracao", "Permite restaurar backups anteriores",
                    "download", "Permite baixar arquivos de backup",
                    "limpeza_automatica", "Remove backups antigos automaticamente"
                ),
                "instrucoes", Map.of(
                    "backup_manual", "Use POST /api/backup/executar para executar backup manual",
                    "listar_backups", "Use GET /api/backup/listar para ver backups disponíveis",
                    "baixar_backup", "Use GET /api/backup/baixar/{nome} para baixar backup",
                    "restaurar_backup", "Use POST /api/backup/restaurar/{nome} para restaurar backup"
                ),
                "observacoes", Map.of(
                    "seguranca", "Backups contêm dados sensíveis - mantenha-os seguros",
                    "armazenamento", "Backups são armazenados localmente no diretório ./backups",
                    "restauracao", "A restauração substitui dados atuais - use com cuidado",
                    "manutencao", "O sistema limpa automaticamente backups antigos"
                )
            );
            
            return ResponseEntity.ok(informacoes);
            
        } catch (Exception e) {
            log.error("❌ Erro ao obter informações do sistema de backup: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Verifica status do sistema de backup
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> verificarStatusBackup(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("🔍 Verificando status do sistema de backup para usuário: {}", currentUser.getId());
            
            // Verificar se diretório de backup existe
            File backupDir = new File("./backups");
            boolean diretorioExiste = backupDir.exists() && backupDir.isDirectory();
            
            // Verificar se há backups disponíveis
            List<Map<String, Object>> backups = backupAutomaticoService.listarBackupsDisponiveis();
            int totalBackups = backups.size();
            
            // Verificar último backup
            String ultimoBackup = totalBackups > 0 ? (String) backups.get(0).get("nome") : "Nenhum";
            
            // Verificar espaço em disco
            long espacoDisponivel = backupDir.getFreeSpace();
            String espacoDisponivelMB = String.format("%.2f", espacoDisponivel / (1024.0 * 1024.0));
            
            Map<String, Object> status = Map.of(
                "sistema_ativo", true,
                "diretorio_backup", diretorioExiste,
                "total_backups", totalBackups,
                "ultimo_backup", ultimoBackup,
                "espaco_disponivel_mb", espacoDisponivelMB,
                "backup_automatico_habilitado", true,
                "frequencia_backup", "Diário às 2:00 AM",
                "max_backups", 30,
                "status_geral", totalBackups > 0 ? "Funcionando normalmente" : "Nenhum backup encontrado"
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar status do sistema de backup: {}", e.getMessage(), e);
            
            Map<String, Object> status = Map.of(
                "sistema_ativo", false,
                "erro", "Erro ao verificar status: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(status);
        }
    }
}
