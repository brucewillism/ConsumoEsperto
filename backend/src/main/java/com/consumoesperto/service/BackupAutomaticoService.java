package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serviço para backup automático
 * 
 * Este serviço realiza backups automáticos do sistema
 * incluindo banco de dados, arquivos de configuração e logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupAutomaticoService {

    private static final String BACKUP_DIR = "./backups";
    private static final String DB_BACKUP_DIR = "./backups/database";
    private static final String CONFIG_BACKUP_DIR = "./backups/config";
    private static final String LOGS_BACKUP_DIR = "./backups/logs";
    private static final int MAX_BACKUPS = 30; // Manter últimos 30 backups

    /**
     * Executa backup automático diário às 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void executarBackupAutomatico() {
        try {
            log.info("🔄 Iniciando backup automático do sistema...");
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupName = "backup_automatico_" + timestamp;
            
            // Criar diretórios de backup
            criarDiretoriosBackup();
            
            // Executar backup do banco de dados
            boolean dbBackupSuccess = executarBackupBancoDados(backupName);
            
            // Executar backup de configurações
            boolean configBackupSuccess = executarBackupConfiguracoes(backupName);
            
            // Executar backup de logs
            boolean logsBackupSuccess = executarBackupLogs(backupName);
            
            // Criar arquivo ZIP consolidado
            boolean zipSuccess = criarArquivoZipConsolidado(backupName);
            
            // Limpar backups antigos
            limparBackupsAntigos();
            
            if (dbBackupSuccess && configBackupSuccess && logsBackupSuccess && zipSuccess) {
                log.info("✅ Backup automático concluído com sucesso: {}", backupName);
            } else {
                log.warn("⚠️ Backup automático concluído com alguns erros: {}", backupName);
            }
            
        } catch (Exception e) {
            log.error("❌ Erro durante backup automático: {}", e.getMessage(), e);
        }
    }

    /**
     * Executa backup manual
     */
    public String executarBackupManual() {
        try {
            log.info("🔄 Iniciando backup manual do sistema...");
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupName = "backup_manual_" + timestamp;
            
            // Criar diretórios de backup
            criarDiretoriosBackup();
            
            // Executar backup do banco de dados
            boolean dbBackupSuccess = executarBackupBancoDados(backupName);
            
            // Executar backup de configurações
            boolean configBackupSuccess = executarBackupConfiguracoes(backupName);
            
            // Executar backup de logs
            boolean logsBackupSuccess = executarBackupLogs(backupName);
            
            // Criar arquivo ZIP consolidado
            boolean zipSuccess = criarArquivoZipConsolidado(backupName);
            
            if (dbBackupSuccess && configBackupSuccess && logsBackupSuccess && zipSuccess) {
                log.info("✅ Backup manual concluído com sucesso: {}", backupName);
                return backupName;
            } else {
                log.warn("⚠️ Backup manual concluído com alguns erros: {}", backupName);
                return backupName + "_com_erros";
            }
            
        } catch (Exception e) {
            log.error("❌ Erro durante backup manual: {}", e.getMessage(), e);
            throw new RuntimeException("Erro durante backup manual: " + e.getMessage());
        }
    }

    /**
     * Executa backup do banco de dados
     */
    private boolean executarBackupBancoDados(String backupName) {
        try {
            log.info("🗄️ Executando backup do banco de dados...");
            
            String dbBackupFile = DB_BACKUP_DIR + "/" + backupName + "_database.sql";
            
            // Comando para backup do PostgreSQL
            String[] command = {
                "pg_dump",
                "-h", "localhost",
                "-U", "bruce",
                "-d", "consumoesperto",
                "-f", dbBackupFile
            };
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("✅ Backup do banco de dados concluído: {}", dbBackupFile);
                return true;
            } else {
                log.error("❌ Erro no backup do banco de dados. Código de saída: {}", exitCode);
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao executar backup do banco de dados: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Executa backup de configurações
     */
    private boolean executarBackupConfiguracoes(String backupName) {
        try {
            log.info("⚙️ Executando backup de configurações...");
            
            String configBackupDir = CONFIG_BACKUP_DIR + "/" + backupName + "_config";
            Path configPath = Paths.get(configBackupDir);
            Files.createDirectories(configPath);
            
            // Copiar arquivos de configuração
            String[] configFiles = {
                "src/main/resources/application.properties",
                "src/main/resources/application-dev.properties",
                "src/main/resources/application-prod.properties",
                "src/main/resources/application-secure.properties"
            };
            
            for (String configFile : configFiles) {
                File sourceFile = new File(configFile);
                if (sourceFile.exists()) {
                    File destFile = new File(configBackupDir + "/" + sourceFile.getName());
                    Files.copy(sourceFile.toPath(), destFile.toPath());
                    log.info("✅ Configuração copiada: {}", sourceFile.getName());
                }
            }
            
            log.info("✅ Backup de configurações concluído: {}", configBackupDir);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erro ao executar backup de configurações: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Executa backup de logs
     */
    private boolean executarBackupLogs(String backupName) {
        try {
            log.info("📝 Executando backup de logs...");
            
            String logsBackupDir = LOGS_BACKUP_DIR + "/" + backupName + "_logs";
            Path logsPath = Paths.get(logsBackupDir);
            Files.createDirectories(logsPath);
            
            // Copiar arquivos de log
            String[] logFiles = {
                "logs/consumo-esperto.log",
                "logs/consumo-esperto.log.2025-09-13.0.gz",
                "logs/consumo-esperto.log.2025-09-14.0.gz",
                "logs/consumo-esperto.log.2025-09-21.0.gz",
                "logs/consumo-esperto.log.2025-09-22.0.gz",
                "logs/consumo-esperto.log.2025-09-24.0.gz",
                "logs/consumo-esperto.log.2025-09-26.0.gz",
                "logs/consumo-esperto.log.2025-09-28.0.gz",
                "logs/consumo-esperto.log.2025-09-29.0.gz"
            };
            
            for (String logFile : logFiles) {
                File sourceFile = new File(logFile);
                if (sourceFile.exists()) {
                    File destFile = new File(logsBackupDir + "/" + sourceFile.getName());
                    Files.copy(sourceFile.toPath(), destFile.toPath());
                    log.info("✅ Log copiado: {}", sourceFile.getName());
                }
            }
            
            log.info("✅ Backup de logs concluído: {}", logsBackupDir);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erro ao executar backup de logs: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cria arquivo ZIP consolidado
     */
    private boolean criarArquivoZipConsolidado(String backupName) {
        try {
            log.info("📦 Criando arquivo ZIP consolidado...");
            
            String zipFile = BACKUP_DIR + "/" + backupName + ".zip";
            
            try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile))) {
                // Adicionar backup do banco de dados
                adicionarArquivoAoZip(zipOut, DB_BACKUP_DIR + "/" + backupName + "_database.sql", "database.sql");
                
                // Adicionar configurações
                adicionarDiretorioAoZip(zipOut, CONFIG_BACKUP_DIR + "/" + backupName + "_config", "config/");
                
                // Adicionar logs
                adicionarDiretorioAoZip(zipOut, LOGS_BACKUP_DIR + "/" + backupName + "_logs", "logs/");
                
                // Adicionar arquivo de informações do backup
                adicionarInformacoesBackup(zipOut, backupName);
            }
            
            log.info("✅ Arquivo ZIP consolidado criado: {}", zipFile);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar arquivo ZIP consolidado: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Adiciona arquivo ao ZIP
     */
    private void adicionarArquivoAoZip(ZipOutputStream zipOut, String filePath, String entryName) throws IOException {
        File file = new File(filePath);
        if (file.exists()) {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zipOut.putNextEntry(zipEntry);
            
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zipOut.write(buffer, 0, length);
                }
            }
            
            zipOut.closeEntry();
            log.info("✅ Arquivo adicionado ao ZIP: {}", entryName);
        }
    }

    /**
     * Adiciona diretório ao ZIP
     */
    private void adicionarDiretorioAoZip(ZipOutputStream zipOut, String dirPath, String entryPrefix) throws IOException {
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String entryName = entryPrefix + file.getName();
                        adicionarArquivoAoZip(zipOut, file.getAbsolutePath(), entryName);
                    }
                }
            }
        }
    }

    /**
     * Adiciona informações do backup ao ZIP
     */
    private void adicionarInformacoesBackup(ZipOutputStream zipOut, String backupName) throws IOException {
        ZipEntry zipEntry = new ZipEntry("backup_info.txt");
        zipOut.putNextEntry(zipEntry);
        
        String info = String.format(
            "BACKUP DO SISTEMA CONSUMO ESPERTO\n" +
            "==================================\n\n" +
            "Nome do Backup: %s\n" +
            "Data/Hora: %s\n" +
            "Tipo: Automático\n" +
            "Versão do Sistema: 1.0.0\n" +
            "Ambiente: Development\n\n" +
            "CONTEÚDO DO BACKUP:\n" +
            "- database.sql: Backup completo do banco de dados PostgreSQL\n" +
            "- config/: Arquivos de configuração do sistema\n" +
            "- logs/: Arquivos de log do sistema\n\n" +
            "INSTRUÇÕES DE RESTAURAÇÃO:\n" +
            "1. Extrair o arquivo ZIP\n" +
            "2. Restaurar o banco de dados usando: psql -U bruce -d consumoesperto -f database.sql\n" +
            "3. Copiar os arquivos de configuração para os diretórios apropriados\n" +
            "4. Reiniciar o sistema\n\n" +
            "Para mais informações, consulte a documentação do sistema.",
            backupName,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
        );
        
        zipOut.write(info.getBytes("UTF-8"));
        zipOut.closeEntry();
    }

    /**
     * Cria diretórios de backup
     */
    private void criarDiretoriosBackup() throws IOException {
        Files.createDirectories(Paths.get(BACKUP_DIR));
        Files.createDirectories(Paths.get(DB_BACKUP_DIR));
        Files.createDirectories(Paths.get(CONFIG_BACKUP_DIR));
        Files.createDirectories(Paths.get(LOGS_BACKUP_DIR));
    }

    /**
     * Limpa backups antigos
     */
    private void limparBackupsAntigos() {
        try {
            log.info("🧹 Limpando backups antigos...");
            
            File backupDir = new File(BACKUP_DIR);
            File[] backupFiles = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
            
            if (backupFiles != null && backupFiles.length > MAX_BACKUPS) {
                // Ordenar por data de modificação (mais antigos primeiro)
                java.util.Arrays.sort(backupFiles, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                
                int filesToDelete = backupFiles.length - MAX_BACKUPS;
                for (int i = 0; i < filesToDelete; i++) {
                    if (backupFiles[i].delete()) {
                        log.info("✅ Backup antigo removido: {}", backupFiles[i].getName());
                    } else {
                        log.warn("⚠️ Falha ao remover backup antigo: {}", backupFiles[i].getName());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao limpar backups antigos: {}", e.getMessage(), e);
        }
    }

    /**
     * Lista backups disponíveis
     */
    public java.util.List<java.util.Map<String, Object>> listarBackupsDisponiveis() {
        try {
            log.info("📋 Listando backups disponíveis...");
            
            java.util.List<java.util.Map<String, Object>> backups = new java.util.ArrayList<>();
            
            File backupDir = new File(BACKUP_DIR);
            File[] backupFiles = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
            
            if (backupFiles != null) {
                for (File backupFile : backupFiles) {
                    java.util.Map<String, Object> backupInfo = new java.util.HashMap<>();
                    backupInfo.put("nome", backupFile.getName());
                    backupInfo.put("tamanho_bytes", backupFile.length());
                    backupInfo.put("tamanho_mb", String.format("%.2f", backupFile.length() / (1024.0 * 1024.0)));
                    backupInfo.put("data_criacao", new java.util.Date(backupFile.lastModified()));
                    backupInfo.put("data_modificacao", new java.util.Date(backupFile.lastModified()));
                    backupInfo.put("caminho", backupFile.getAbsolutePath());
                    
                    backups.add(backupInfo);
                }
            }
            
            // Ordenar por data de modificação (mais recentes primeiro)
            backups.sort((a, b) -> {
                java.util.Date dateA = (java.util.Date) a.get("data_modificacao");
                java.util.Date dateB = (java.util.Date) b.get("data_modificacao");
                return dateB.compareTo(dateA);
            });
            
            log.info("✅ {} backups encontrados", backups.size());
            return backups;
            
        } catch (Exception e) {
            log.error("❌ Erro ao listar backups disponíveis: {}", e.getMessage(), e);
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Restaura backup
     */
    public boolean restaurarBackup(String backupName) {
        try {
            log.info("🔄 Iniciando restauração do backup: {}", backupName);
            
            String zipFile = BACKUP_DIR + "/" + backupName;
            if (!zipFile.endsWith(".zip")) {
                zipFile += ".zip";
            }
            
            File backupFile = new File(zipFile);
            if (!backupFile.exists()) {
                log.error("❌ Arquivo de backup não encontrado: {}", zipFile);
                return false;
            }
            
            // Extrair arquivo ZIP
            String extractDir = BACKUP_DIR + "/restore_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            extrairArquivoZip(zipFile, extractDir);
            
            // Restaurar banco de dados
            boolean dbRestoreSuccess = restaurarBancoDados(extractDir + "/database.sql");
            
            // Restaurar configurações
            boolean configRestoreSuccess = restaurarConfiguracoes(extractDir + "/config/");
            
            if (dbRestoreSuccess && configRestoreSuccess) {
                log.info("✅ Backup restaurado com sucesso: {}", backupName);
                return true;
            } else {
                log.warn("⚠️ Backup restaurado com alguns erros: {}", backupName);
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao restaurar backup: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extrai arquivo ZIP
     */
    private void extrairArquivoZip(String zipFile, String extractDir) throws IOException {
        try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry = zipIn.getNextEntry();
            
            while (entry != null) {
                String filePath = extractDir + "/" + entry.getName();
                File file = new File(filePath);
                
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipIn.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
                
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    /**
     * Restaura banco de dados
     */
    private boolean restaurarBancoDados(String sqlFile) {
        try {
            log.info("🗄️ Restaurando banco de dados...");
            
            String[] command = {
                "psql",
                "-h", "localhost",
                "-U", "bruce",
                "-d", "consumoesperto",
                "-f", sqlFile
            };
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("✅ Banco de dados restaurado com sucesso");
                return true;
            } else {
                log.error("❌ Erro na restauração do banco de dados. Código de saída: {}", exitCode);
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao restaurar banco de dados: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Restaura configurações
     */
    private boolean restaurarConfiguracoes(String configDir) {
        try {
            log.info("⚙️ Restaurando configurações...");
            
            File configDirFile = new File(configDir);
            if (!configDirFile.exists()) {
                log.warn("⚠️ Diretório de configurações não encontrado: {}", configDir);
                return true; // Não é um erro crítico
            }
            
            File[] configFiles = configDirFile.listFiles();
            if (configFiles != null) {
                for (File configFile : configFiles) {
                    String destPath = "src/main/resources/" + configFile.getName();
                    Files.copy(configFile.toPath(), Paths.get(destPath));
                    log.info("✅ Configuração restaurada: {}", configFile.getName());
                }
            }
            
            log.info("✅ Configurações restauradas com sucesso");
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erro ao restaurar configurações: {}", e.getMessage(), e);
            return false;
        }
    }
}
