package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Statement;
import java.util.List;

/**
 * Aplica patches idempotentes de schema para compatibilidade com releases recentes.
 * Evita falhas em runtime quando o banco local está com colunas antigas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaAutoPatchService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Com {@code spring.datasource.hikari.auto-commit=false}, DDL via {@link JdbcTemplate#execute(String)}
     * pode ficar dentro de transação e ser revertida ao devolver a conexão ao pool. DDL deve usar autocommit.
     */
    private void executeDdlAutocommit(String sql) {
        jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
            boolean orig = con.getAutoCommit();
            con.setAutoCommit(true);
            try (Statement st = con.createStatement()) {
                st.execute(sql);
            } finally {
                con.setAutoCommit(orig);
            }
            return null;
        });
    }

    @PostConstruct
    public void applyPatches() {
        ensureUsuarioAiConfigTable();
        ensureUsuarioAiConfigEvolutionApiKeyColumn();
        ensureUsuarioRendaConfigTable();
        ensureMetasFinanceirasTable();
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema " +
                    "FROM information_schema.tables " +
                    "WHERE table_name = 'transacoes' " +
                    "  AND table_type = 'BASE TABLE' " +
                    "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );

            if (schemas == null || schemas.isEmpty()) {
                log.warn("Schema patch: tabela 'transacoes' não encontrada em nenhum schema acessível.");
                return;
            }

            for (String schema : schemas) {
                String safeSchema = schema.replace("\"", "");
                String qualifiedTable = safeSchema + ".transacoes";
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS recorrente BOOLEAN NOT NULL DEFAULT FALSE");
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS frequencia VARCHAR(255)");
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS proxima_execucao DATE");
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS excluido BOOLEAN NOT NULL DEFAULT FALSE");
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS status_conferencia VARCHAR(255) NOT NULL DEFAULT 'CONFIRMADA'");
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS cnpj VARCHAR(18)");
                log.info("Schema patch aplicado em {}: colunas de recorrência/conferência/CNPJ verificadas.", qualifiedTable);
            }
        } catch (Exception e) {
            // Não derruba a aplicação; apenas registra para troubleshooting.
            log.warn("Falha ao aplicar schema patch automático: {}", e.getMessage());
        }
    }

    private void ensureUsuarioAiConfigTable() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.tables "
                    + "WHERE table_name = 'usuarios' "
                    + "  AND table_type = 'BASE TABLE' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null || schemas.isEmpty()) {
                log.warn("Schema patch: tabela 'usuarios' não encontrada; usuario_ai_config não criada.");
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedTable = schema + ".usuario_ai_config";
                String qualifiedUsuarios = schema + ".usuarios";
                executeDdlAutocommit(
                    "CREATE TABLE IF NOT EXISTS " + qualifiedTable + " ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "usuario_id BIGINT NOT NULL UNIQUE REFERENCES " + qualifiedUsuarios + "(id) ON DELETE CASCADE,"
                        + "evolution_instance_name VARCHAR(128) UNIQUE,"
                        + "whatsapp_owner_phone VARCHAR(32),"
                        + "groq_api_key TEXT,"
                        + "groq_base_url VARCHAR(500),"
                        + "groq_model_text VARCHAR(200),"
                        + "groq_model_vision VARCHAR(200),"
                        + "groq_whisper_model VARCHAR(200),"
                        + "openai_api_key TEXT,"
                        + "openai_base_url VARCHAR(500),"
                        + "openai_model VARCHAR(200),"
                        + "openai_whisper_model VARCHAR(200),"
                        + "ollama_base_url VARCHAR(500),"
                        + "ollama_model VARCHAR(200),"
                        + "provider_order VARCHAR(500)"
                        + ")"
                );
                log.info("Schema patch: tabela {} verificada.", qualifiedTable);
            }
        } catch (Exception e) {
            log.warn("Falha ao CREATE usuario_ai_config: {}", e.getMessage());
        }
    }

    private void ensureUsuarioAiConfigEvolutionApiKeyColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.tables "
                    + "WHERE table_name = 'usuario_ai_config' "
                    + "  AND table_type = 'BASE TABLE' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedTable = schema + ".usuario_ai_config";
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS evolution_api_key TEXT");
            }
        } catch (Exception e) {
            log.warn("Falha ao adicionar evolution_api_key em usuario_ai_config: {}", e.getMessage());
        }
    }

    private void ensureUsuarioRendaConfigTable() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.tables "
                    + "WHERE table_name = 'usuarios' "
                    + "  AND table_type = 'BASE TABLE' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null || schemas.isEmpty()) {
                log.warn("Schema patch: tabela 'usuarios' não encontrada; usuario_renda_config não criada.");
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedTable = schema + ".usuario_renda_config";
                String qualifiedUsuarios = schema + ".usuarios";
                executeDdlAutocommit(
                    "CREATE TABLE IF NOT EXISTS " + qualifiedTable + " ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "usuario_id BIGINT NOT NULL UNIQUE REFERENCES " + qualifiedUsuarios + "(id) ON DELETE CASCADE,"
                        + "salario_bruto NUMERIC(19,2) NOT NULL DEFAULT 0,"
                        + "descontos_fixos_json TEXT,"
                        + "dia_pagamento INTEGER,"
                        + "salario_liquido NUMERIC(19,2) NOT NULL DEFAULT 0,"
                        + "receita_automatica_ativa BOOLEAN NOT NULL DEFAULT FALSE,"
                        + "ultimo_mes_lancamento_auto INTEGER,"
                        + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")"
                );
                log.info("Schema patch: tabela {} verificada.", qualifiedTable);
            }
        } catch (Exception e) {
            log.warn("Falha ao CREATE usuario_renda_config: {}", e.getMessage());
        }
    }

    private void ensureMetasFinanceirasTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.metas_financeiras ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "descricao VARCHAR(255) NOT NULL,"
                    + "valor_total NUMERIC(19,2) NOT NULL,"
                    + "percentual_comprometimento NUMERIC(5,2) NOT NULL,"
                    + "valor_poupado_mensal NUMERIC(19,2) NOT NULL,"
                    + "prazo_meses NUMERIC(10,2) NOT NULL,"
                    + "renda_media_referencia NUMERIC(19,2),"
                    + "prioridade INTEGER NOT NULL DEFAULT 3,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
        } catch (Exception e) {
            log.warn("Falha ao CREATE metas_financeiras: {}", e.getMessage());
            return;
        }
        try {
            executeDdlAutocommit(
                "DO $patch$ BEGIN "
                    + "IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'metas_financeiras') THEN "
                    + "ALTER TABLE public.metas_financeiras ADD COLUMN IF NOT EXISTS prioridade INTEGER NOT NULL DEFAULT 3; "
                    + "END IF; "
                    + "END $patch$"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_metas_financeiras_usuario ON public.metas_financeiras(usuario_id)"
            );
            log.info("Schema patch: tabela public.metas_financeiras verificada.");
        } catch (Exception e) {
            log.warn("Falha ao complementar metas_financeiras (coluna/index): {}", e.getMessage());
        }
    }
}

