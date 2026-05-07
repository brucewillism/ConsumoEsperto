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
        ensureGrupoFamiliarTables();
        ensureOrcamentosTable();
        ensureImportacoesFaturaCartaoTable();
        ensureContrachequesScoreTables();
        ensureUsuarioGeneroColumns();
        ensureUsuarioJarvisPerfilColumns();
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
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ALTER COLUMN tipo_transacao TYPE VARCHAR(32)");
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

    private void ensureOrcamentosTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.orcamentos ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "categoria_id BIGINT NOT NULL REFERENCES public.categorias(id) ON DELETE CASCADE,"
                    + "valor_limite NUMERIC(19,2) NOT NULL,"
                    + "mes INTEGER NOT NULL,"
                    + "ano INTEGER NOT NULL,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "CONSTRAINT uk_orcamento_usuario_categoria_mes_ano UNIQUE(usuario_id, categoria_id, mes, ano)"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_orcamentos_usuario_mes_ano ON public.orcamentos(usuario_id, mes, ano)"
            );
            executeDdlAutocommit("ALTER TABLE public.orcamentos ADD COLUMN IF NOT EXISTS compartilhado BOOLEAN NOT NULL DEFAULT FALSE");
            executeDdlAutocommit("ALTER TABLE public.orcamentos ADD COLUMN IF NOT EXISTS grupo_familiar_id BIGINT REFERENCES public.grupos_familiares(id) ON DELETE SET NULL");
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_orcamentos_grupo_mes_ano ON public.orcamentos(grupo_familiar_id, mes, ano)"
            );
            log.info("Schema patch: tabela public.orcamentos verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE orcamentos: {}", e.getMessage());
        }
    }

    private void ensureImportacoesFaturaCartaoTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.importacoes_fatura_cartao ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "cartao_credito_id BIGINT REFERENCES public.cartoes_credito(id) ON DELETE SET NULL,"
                    + "fatura_id BIGINT REFERENCES public.faturas(id) ON DELETE SET NULL,"
                    + "banco_cartao VARCHAR(120),"
                    + "data_vencimento TIMESTAMP,"
                    + "data_fechamento TIMESTAMP,"
                    + "valor_total NUMERIC(19,2),"
                    + "pagamento_minimo NUMERIC(19,2),"
                    + "itens_json TEXT NOT NULL,"
                    + "auditoria_json TEXT,"
                    + "status VARCHAR(32) NOT NULL DEFAULT 'PENDENTE',"
                    + "novos_detectados INTEGER NOT NULL DEFAULT 0,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_confirmacao TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_importacoes_fatura_usuario_status "
                    + "ON public.importacoes_fatura_cartao(usuario_id, status, data_criacao DESC)"
            );
            log.info("Schema patch: tabela public.importacoes_fatura_cartao verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE importacoes_fatura_cartao: {}", e.getMessage());
        }
    }

    private void ensureContrachequesScoreTables() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.contracheques_importados ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "empresa VARCHAR(160),"
                    + "mes INTEGER,"
                    + "ano INTEGER,"
                    + "salario_bruto NUMERIC(19,2),"
                    + "salario_liquido NUMERIC(19,2),"
                    + "total_descontos NUMERIC(19,2),"
                    + "descontos_json TEXT,"
                    + "insights_json TEXT,"
                    + "status VARCHAR(32) NOT NULL DEFAULT 'PENDENTE',"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_confirmacao TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_contracheques_usuario_periodo "
                    + "ON public.contracheques_importados(usuario_id, ano DESC, mes DESC)"
            );
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.usuarios_score ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL UNIQUE REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "score INTEGER NOT NULL DEFAULT 500,"
                    + "nivel VARCHAR(40) NOT NULL DEFAULT 'Bronze',"
                    + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.historico_score ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "delta INTEGER NOT NULL,"
                    + "score_resultante INTEGER NOT NULL,"
                    + "motivo VARCHAR(120) NOT NULL,"
                    + "detalhe VARCHAR(500),"
                    + "data_evento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_historico_score_usuario_data "
                    + "ON public.historico_score(usuario_id, data_evento DESC)"
            );
            log.info("Schema patch: contracheques e score verificados.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE contracheques/score: {}", e.getMessage());
        }
    }

    private void ensureUsuarioGeneroColumns() {
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
                log.warn("Schema patch: tabela 'usuarios' não encontrada; colunas de gênero não aplicadas.");
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedUsuarios = schema + ".usuarios";
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ADD COLUMN IF NOT EXISTS genero VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'");
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ADD COLUMN IF NOT EXISTS genero_confirmado BOOLEAN NOT NULL DEFAULT FALSE");
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ADD COLUMN IF NOT EXISTS jarvis_google_genero_notificado BOOLEAN NOT NULL DEFAULT FALSE");
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ADD COLUMN IF NOT EXISTS preferencia_tratamento_jarvis VARCHAR(32) NOT NULL DEFAULT 'AUTOMATICO'");
            }
            log.info("Schema patch: colunas genero / genero_confirmado / jarvis_google_genero_notificado verificadas em usuarios.");
        } catch (Exception e) {
            log.warn("Falha ao aplicar colunas de gênero em usuarios: {}", e.getMessage());
        }
    }

    private void ensureUsuarioJarvisPerfilColumns() {
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
                log.warn("Schema patch: tabela 'usuarios' não encontrada; colunas tratamento/jarvis_configurado não aplicadas.");
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedUsuarios = schema + ".usuarios";
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ADD COLUMN IF NOT EXISTS tratamento VARCHAR(32)");
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ADD COLUMN IF NOT EXISTS jarvis_configurado BOOLEAN NOT NULL DEFAULT FALSE");
                executeDdlAutocommit(
                    "UPDATE " + qualifiedUsuarios + " SET jarvis_configurado = FALSE WHERE jarvis_configurado IS NULL");
            }
            log.info("Schema patch: colunas tratamento / jarvis_configurado verificadas em usuarios.");
        } catch (Exception e) {
            log.warn("Falha ao aplicar colunas J.A.R.V.I.S. em usuarios: {}", e.getMessage());
        }
    }

    private void ensureGrupoFamiliarTables() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.grupos_familiares ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "nome VARCHAR(120) NOT NULL,"
                    + "criador_usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.grupo_familiar_membros ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "grupo_familiar_id BIGINT NOT NULL REFERENCES public.grupos_familiares(id) ON DELETE CASCADE,"
                    + "usuario_id BIGINT REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "convidado_por_usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "convite_email VARCHAR(120),"
                    + "convite_whatsapp VARCHAR(32),"
                    + "token_convite VARCHAR(64) NOT NULL UNIQUE,"
                    + "status VARCHAR(32) NOT NULL DEFAULT 'PENDENTE',"
                    + "data_convite TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_resposta TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_grupo_membro_usuario_not_null "
                    + "ON public.grupo_familiar_membros(grupo_familiar_id, usuario_id) WHERE usuario_id IS NOT NULL"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_grupo_membros_usuario_status "
                    + "ON public.grupo_familiar_membros(usuario_id, status)"
            );
            log.info("Schema patch: grupos familiares verificados.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE grupos familiares: {}", e.getMessage());
        }
    }
}

