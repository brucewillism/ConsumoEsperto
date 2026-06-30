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
    private final DadosLegadosSanitizationService dadosLegadosSanitizationService;

    /** Extensão {@code vector} (pgvector) instalada nesta base. */
    private boolean isPgVectorExtensionInstalled() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')", Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }

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
        ensureUsuarioAiConfigEvolutionSessionSuppressedColumn();
        ensureUsuarioRendaConfigTable();
        ensureRendaConfigContaBancariaColumn();
        ensureRendaConfigTipoRendaColumns();
        ensureRendaConfigMetaFaturamentoColumn();
        ensureMetasFinanceirasTable();
        ensureGrupoFamiliarTables();
        ensureOrcamentosTable();
        ensureDespesasFixasTable();
        ensureImportacoesFaturaCartaoTable();
        ensureSugestoesContencaoJarvisTable();
        ensureContrachequesScoreTables();
        ensureUsuarioConfiguracaoFiscalTable();
        ensureTransacaoOrigemFiscalColumn();
        ensureContasBancariasTable();
        ensureContaBancariaChequeEspecialColumn();
        ensureContaBancariaSaldoInicialColumn();
        ensureDebitosInternosTable();
        ensureAgendamentosPagamentosTable();
        ensureAssinaturasRecorrentesTable();
        ensureRendasTable();
        ensureTransferenciasContasTable();
        ensureTransacaoContaBancariaColumn();
        ensureTransacaoEmprestimoIdColumn();
        ensureUsuarioGeneroColumns();
        ensureUsuarioJarvisPerfilColumns();
        ensureUsuarioFotoUrlTextColumn();
        ensureUsuarioGoogleCalendarColumns();
        ensureMetasFinanceirasCronosColumns();
        ensureJarvisCronosEventLogTable();
        ensureAuditLogTable();
        ensurePgvectorExtensionAndMemoriaSemanticaJarvisTable();
        ensureTransacaoSemanticaIndexTable();
        ensureJarvisFeedbackTable();
        ensureJarvisFeedbackDataExpiracaoColumn();
        ensureUsuarioSessoesContextoTable();
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
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS origem_fiscal VARCHAR(40)");
                executeDdlAutocommit("ALTER TABLE " + qualifiedTable + " ALTER COLUMN tipo_transacao TYPE VARCHAR(32)");
                log.info("Schema patch aplicado em {}: colunas de recorrência/conferência/CNPJ verificadas.", qualifiedTable);
            }
        } catch (Exception e) {
            // Não derruba a aplicação; apenas registra para troubleshooting.
            log.warn("Falha ao aplicar schema patch automático: {}", e.getMessage());
        }
        try {
            dadosLegadosSanitizationService.aplicarPatchV83SeNecessario();
        } catch (Exception e) {
            log.warn("Data patch v8.3 (sanitização legada): {}", e.getMessage());
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

    private void ensureUsuarioAiConfigEvolutionSessionSuppressedColumn() {
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
                    "ALTER TABLE " + qualifiedTable
                        + " ADD COLUMN IF NOT EXISTS evolution_session_suppressed BOOLEAN NOT NULL DEFAULT FALSE"
                );
            }
        } catch (Exception e) {
            log.warn("Falha ao adicionar evolution_session_suppressed em usuario_ai_config: {}", e.getMessage());
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
                        + "conta_bancaria_id BIGINT,"
                        + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")"
                );
                log.info("Schema patch: tabela {} verificada.", qualifiedTable);
            }
        } catch (Exception e) {
            log.warn("Falha ao CREATE usuario_renda_config: {}", e.getMessage());
        }
    }

    private void ensureRendaConfigContaBancariaColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.tables "
                    + "WHERE table_name = 'usuario_renda_config' "
                    + "  AND table_type = 'BASE TABLE' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String fkName = "fk_" + schema + "_usuario_renda_config_conta_bancaria";
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".usuario_renda_config "
                        + "ADD COLUMN IF NOT EXISTS conta_bancaria_id BIGINT"
                );
                executeDdlAutocommit(
                    "DO $$ BEGIN "
                        + "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '" + fkName + "') THEN "
                        + "ALTER TABLE " + schema + ".usuario_renda_config "
                        + "ADD CONSTRAINT " + fkName + " "
                        + "FOREIGN KEY (conta_bancaria_id) REFERENCES public.contas_bancarias(id) ON DELETE SET NULL; "
                        + "END IF; END $$"
                );
            }
            log.info("Schema patch: coluna conta_bancaria_id verificada em usuario_renda_config.");
        } catch (Exception e) {
            log.warn("Falha ao ADD conta_bancaria_id em usuario_renda_config: {}", e.getMessage());
        }
    }

    private void ensureRendaConfigTipoRendaColumns() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.tables "
                    + "WHERE table_name = 'usuario_renda_config' "
                    + "  AND table_type = 'BASE TABLE' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".usuario_renda_config "
                        + "ADD COLUMN IF NOT EXISTS tipo_configuracao_renda VARCHAR(30) NOT NULL DEFAULT 'CONTRACHEQUE'"
                );
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".usuario_renda_config "
                        + "ADD COLUMN IF NOT EXISTS valor_recebimento_unico NUMERIC(19, 2)"
                );
            }
            log.info("Schema patch: colunas tipo_configuracao_renda e valor_recebimento_unico verificadas.");
        } catch (Exception e) {
            log.warn("Falha ao ADD tipo_configuracao_renda em usuario_renda_config: {}", e.getMessage());
        }
    }

    private void ensureRendaConfigMetaFaturamentoColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.tables "
                    + "WHERE table_name = 'usuario_renda_config' "
                    + "  AND table_type = 'BASE TABLE' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".usuario_renda_config "
                        + "ADD COLUMN IF NOT EXISTS meta_faturamento_mensal NUMERIC(19, 2)"
                );
            }
            log.info("Schema patch: coluna meta_faturamento_mensal verificada em usuario_renda_config.");
        } catch (Exception e) {
            log.warn("Falha ao ADD meta_faturamento_mensal em usuario_renda_config: {}", e.getMessage());
        }
    }

    private void ensureUsuarioConfiguracaoFiscalTable() {
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
                log.warn("Schema patch: tabela 'usuarios' não encontrada; usuario_configuracao_fiscal não criada.");
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedTable = schema + ".usuario_configuracao_fiscal";
                String qualifiedUsuarios = schema + ".usuarios";
                executeDdlAutocommit(
                    "CREATE TABLE IF NOT EXISTS " + qualifiedTable + " ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "usuario_id BIGINT NOT NULL UNIQUE REFERENCES " + qualifiedUsuarios + "(id) ON DELETE CASCADE,"
                        + "mes_restituicao_ir INTEGER,"
                        + "valor_restituicao NUMERIC(19,2),"
                        + "tipo_recebimento_13 VARCHAR(32),"
                        + "mes_parcela_unica INTEGER,"
                        + "mes_primeira_parcela INTEGER,"
                        + "mes_segunda_parcela INTEGER,"
                        + "dia_pagamento_13 INTEGER,"
                        + "provisionamento_ativo BOOLEAN NOT NULL DEFAULT TRUE,"
                        + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")"
                );
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS mes_segunda_parcela INTEGER"
                );
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS dia_pagamento_13 INTEGER"
                );
                log.info("Schema patch: tabela {} verificada.", qualifiedTable);
            }
        } catch (Exception e) {
            log.warn("Falha ao CREATE usuario_configuracao_fiscal: {}", e.getMessage());
        }
    }

    /** Garante coluna origem_fiscal em todos os schemas (redundante com loop transacoes, idempotente). */
    private void ensureTransacaoOrigemFiscalColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'transacoes' AND table_type = 'BASE TABLE' "
                    + "AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".transacoes ADD COLUMN IF NOT EXISTS origem_fiscal VARCHAR(40)"
                );
            }
        } catch (Exception e) {
            log.warn("Falha ao ADD origem_fiscal em transacoes: {}", e.getMessage());
        }
    }

    private void ensureContasBancariasTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.contas_bancarias ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "nome VARCHAR(100) NOT NULL,"
                    + "tipo VARCHAR(20) NOT NULL,"
                    + "saldo_atual NUMERIC(19,2) NOT NULL DEFAULT 0,"
                    + "ativa BOOLEAN NOT NULL DEFAULT TRUE,"
                    + "padrao BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_contas_bancarias_usuario ON public.contas_bancarias(usuario_id)"
            );
            log.info("Schema patch: tabela public.contas_bancarias verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE contas_bancarias: {}", e.getMessage());
        }
    }

    private void ensureContaBancariaChequeEspecialColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'contas_bancarias' AND table_type = 'BASE TABLE' "
                    + "AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".contas_bancarias "
                        + "ADD COLUMN IF NOT EXISTS limite_cheque_especial NUMERIC(15,2) NOT NULL DEFAULT 0"
                );
            }
            log.info("Schema patch: coluna limite_cheque_especial verificada em contas_bancarias.");
            removerCheckSaldoNaoNegativoContas();
        } catch (Exception e) {
            log.warn("Falha ao adicionar limite_cheque_especial em contas_bancarias: {}", e.getMessage());
        }
    }

    private void ensureContaBancariaSaldoInicialColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'contas_bancarias' AND table_type = 'BASE TABLE' "
                    + "AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".contas_bancarias "
                        + "ADD COLUMN IF NOT EXISTS saldo_inicial NUMERIC(19,2)"
                );
                executeDdlAutocommit(
                    "UPDATE " + schema + ".contas_bancarias "
                        + "SET saldo_inicial = saldo_atual WHERE saldo_inicial IS NULL"
                );
            }
            log.info("Schema patch: coluna saldo_inicial verificada em contas_bancarias.");
        } catch (Exception e) {
            log.warn("Falha ao adicionar saldo_inicial em contas_bancarias: {}", e.getMessage());
        }
    }

    /** Permite saldo_atual negativo dentro do limite de cheque especial. */
    private void removerCheckSaldoNaoNegativoContas() {
        try {
            List<String> constraints = jdbcTemplate.queryForList(
                "SELECT con.conname || '|' || n.nspname "
                    + "FROM pg_constraint con "
                    + "JOIN pg_class rel ON rel.oid = con.conrelid "
                    + "JOIN pg_namespace n ON n.oid = rel.relnamespace "
                    + "WHERE rel.relname = 'contas_bancarias' "
                    + "AND con.contype = 'c' "
                    + "AND pg_get_constraintdef(con.oid) ILIKE '%saldo%>=%0%'",
                String.class
            );
            if (constraints == null) {
                return;
            }
            for (String row : constraints) {
                String[] parts = row.split("\\|", 2);
                if (parts.length == 2) {
                    executeDdlAutocommit(
                        "ALTER TABLE " + parts[1].replace("\"", "") + ".contas_bancarias DROP CONSTRAINT IF EXISTS "
                            + parts[0].replace("\"", "")
                    );
                    log.info("Schema patch: removido CHECK saldo>=0 em contas_bancarias ({})", parts[0]);
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao remover CHECK saldo>=0 em contas_bancarias: {}", e.getMessage());
        }
    }

    private void ensureDebitosInternosTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.debitos_internos ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "grupo_familiar_id BIGINT NOT NULL REFERENCES public.grupos_familiares(id) ON DELETE CASCADE,"
                    + "credor_usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "devedor_usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "valor NUMERIC(19,2) NOT NULL,"
                    + "descricao VARCHAR(200),"
                    + "liquidado BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_liquidacao TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_debitos_internos_credor "
                    + "ON public.debitos_internos(credor_usuario_id, liquidado)"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_debitos_internos_devedor "
                    + "ON public.debitos_internos(devedor_usuario_id, liquidado)"
            );
            log.info("Schema patch: tabela public.debitos_internos verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE debitos_internos: {}", e.getMessage());
        }
    }

    private void ensureAgendamentosPagamentosTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.agendamentos_pagamentos ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "conta_debito_id BIGINT NOT NULL REFERENCES public.contas_bancarias(id),"
                    + "beneficiario VARCHAR(200) NOT NULL,"
                    + "valor NUMERIC(19,2) NOT NULL,"
                    + "data_vencimento DATE NOT NULL,"
                    + "codigo_barras_ou_pix TEXT,"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'AGENDADO',"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_processamento TIMESTAMP,"
                    + "mensagem_erro VARCHAR(500)"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_agendamentos_usuario_status "
                    + "ON public.agendamentos_pagamentos(usuario_id, status, data_vencimento)"
            );
            log.info("Schema patch: tabela public.agendamentos_pagamentos verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE agendamentos_pagamentos: {}", e.getMessage());
        }
    }

    private void ensureAssinaturasRecorrentesTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.assinaturas_recorrentes ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "nome VARCHAR(200) NOT NULL,"
                    + "valor NUMERIC(19,2) NOT NULL,"
                    + "dia_vencimento INTEGER NOT NULL CHECK (dia_vencimento >= 1 AND dia_vencimento <= 31),"
                    + "conta_debito_padrao_id BIGINT REFERENCES public.contas_bancarias(id),"
                    + "ativo BOOLEAN NOT NULL DEFAULT TRUE,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_assinaturas_usuario_ativo "
                    + "ON public.assinaturas_recorrentes(usuario_id, ativo, dia_vencimento)"
            );
            log.info("Schema patch: tabela public.assinaturas_recorrentes verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE assinaturas_recorrentes: {}", e.getMessage());
        }
    }

    private void ensureRendasTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.rendas ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "descricao VARCHAR(200) NOT NULL,"
                    + "valor NUMERIC(19,2) NOT NULL,"
                    + "dia_pagamento INTEGER NOT NULL,"
                    + "conta_destino_id BIGINT NOT NULL REFERENCES public.contas_bancarias(id),"
                    + "ativa BOOLEAN NOT NULL DEFAULT TRUE,"
                    + "ultimo_mes_credito INTEGER,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_rendas_usuario ON public.rendas(usuario_id)"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_rendas_conta_destino ON public.rendas(conta_destino_id)"
            );
            log.info("Schema patch: tabela public.rendas verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE rendas: {}", e.getMessage());
        }
    }

    private void ensureTransferenciasContasTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.transferencias_contas ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "conta_origem_id BIGINT NOT NULL REFERENCES public.contas_bancarias(id),"
                    + "conta_destino_id BIGINT NOT NULL REFERENCES public.contas_bancarias(id),"
                    + "valor NUMERIC(19,2) NOT NULL,"
                    + "descricao VARCHAR(200),"
                    + "data_transferencia TIMESTAMP NOT NULL,"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_transferencias_usuario ON public.transferencias_contas(usuario_id)"
            );
            log.info("Schema patch: tabela public.transferencias_contas verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE transferencias_contas: {}", e.getMessage());
        }
    }

    private void ensureTransacaoContaBancariaColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'transacoes' AND table_type = 'BASE TABLE' "
                    + "AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".transacoes ADD COLUMN IF NOT EXISTS conta_bancaria_id BIGINT"
                );
                executeDdlAutocommit(
                    "DO $$ BEGIN "
                        + "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transacoes_conta_bancaria') THEN "
                        + "ALTER TABLE " + schema + ".transacoes ADD CONSTRAINT fk_transacoes_conta_bancaria "
                        + "FOREIGN KEY (conta_bancaria_id) REFERENCES public.contas_bancarias(id) ON DELETE SET NULL; "
                        + "END IF; END $$"
                );
            }
        } catch (Exception e) {
            log.warn("Falha ao ADD conta_bancaria_id em transacoes: {}", e.getMessage());
        }
    }

    private void ensureTransacaoEmprestimoIdColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'transacoes' AND table_type = 'BASE TABLE' "
                    + "AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                executeDdlAutocommit(
                    "ALTER TABLE " + schema + ".transacoes ADD COLUMN IF NOT EXISTS emprestimo_id VARCHAR(36)"
                );
            }
            log.info("Schema patch: coluna emprestimo_id verificada em transacoes.");
        } catch (Exception e) {
            log.warn("Falha ao ADD emprestimo_id em transacoes: {}", e.getMessage());
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

    private void ensureDespesasFixasTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.despesas_fixas ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "descricao VARCHAR(200) NOT NULL,"
                    + "valor NUMERIC(19,2) NOT NULL,"
                    + "dia_vencimento INTEGER NOT NULL,"
                    + "categoria VARCHAR(120),"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_despesas_fixas_usuario ON public.despesas_fixas(usuario_id)"
            );
            log.info("Schema patch: tabela public.despesas_fixas verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE despesas_fixas: {}", e.getMessage());
        }
    }

    private void ensureSugestoesContencaoJarvisTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.sugestoes_contencao_jarvis ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "importacao_fatura_cartao_id BIGINT REFERENCES public.importacoes_fatura_cartao(id) ON DELETE SET NULL,"
                    + "categoria_id BIGINT REFERENCES public.categorias(id) ON DELETE SET NULL,"
                    + "chave_agrupamento VARCHAR(200) NOT NULL,"
                    + "rotulo_exibicao VARCHAR(260) NOT NULL,"
                    + "tipo_habito VARCHAR(24) NOT NULL DEFAULT 'OUTRO',"
                    + "valor_gasto_referencia NUMERIC(19,2) NOT NULL,"
                    + "media_tres_meses NUMERIC(19,2),"
                    + "percentual_aumento NUMERIC(8,2),"
                    + "valor_teto_sugerido NUMERIC(19,2) NOT NULL,"
                    + "mes_alvo INTEGER NOT NULL,"
                    + "ano_alvo INTEGER NOT NULL,"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'PENDENTE',"
                    + "data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_sugestoes_contencao_usuario_status "
                    + "ON public.sugestoes_contencao_jarvis(usuario_id, status, data_criacao DESC)"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_sugestoes_contencao_importacao "
                    + "ON public.sugestoes_contencao_jarvis(importacao_fatura_cartao_id)"
            );
            log.info("Schema patch: tabela public.sugestoes_contencao_jarvis verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE sugestoes_contencao_jarvis: {}", e.getMessage());
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
                "ALTER TABLE public.contracheques_importados ADD COLUMN IF NOT EXISTS auditoria_soma_bruto_ok BOOLEAN"
            );
            executeDdlAutocommit(
                "ALTER TABLE public.contracheques_importados ADD COLUMN IF NOT EXISTS auditoria_delta_bruto NUMERIC(19,2)"
            );
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.contracheque_descontos ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "contracheque_importado_id BIGINT NOT NULL REFERENCES public.contracheques_importados(id) ON DELETE CASCADE,"
                    + "descricao VARCHAR(200) NOT NULL,"
                    + "valor NUMERIC(19,2) NOT NULL"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_contracheque_desc_import_id ON public.contracheque_descontos(contracheque_importado_id)"
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

    /**
     * URLs de foto do Google OAuth frequentemente excedem VARCHAR(500); truncagem quebra a tag img no front.
     */
    private void ensureUsuarioFotoUrlTextColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.columns "
                    + "WHERE table_name = 'usuarios' "
                    + "  AND column_name = 'foto_url' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null || schemas.isEmpty()) {
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedUsuarios = schema + ".usuarios";
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ALTER COLUMN foto_url TYPE TEXT");
            }
            log.info("Schema patch: coluna usuarios.foto_url verificada como TEXT.");
        } catch (Exception e) {
            log.warn("Falha ao ampliar foto_url em usuarios: {}", e.getMessage());
        }
    }

    private void ensureUsuarioGoogleCalendarColumns() {
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
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedUsuarios = schema + ".usuarios";
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ADD COLUMN IF NOT EXISTS google_calendar_refresh_token TEXT");
                executeDdlAutocommit(
                    "ALTER TABLE " + qualifiedUsuarios + " ADD COLUMN IF NOT EXISTS google_calendar_linked_at TIMESTAMP");
            }
            log.info("Schema patch: colunas Google Calendar verificadas em usuarios.");
        } catch (Exception e) {
            log.warn("Falha ao aplicar colunas Google Calendar: {}", e.getMessage());
        }
    }

    private void ensureMetasFinanceirasCronosColumns() {
        try {
            executeDdlAutocommit("ALTER TABLE public.metas_financeiras ADD COLUMN IF NOT EXISTS data_expiracao DATE");
            executeDdlAutocommit(
                "ALTER TABLE public.metas_financeiras ADD COLUMN IF NOT EXISTS google_calendar_event_id VARCHAR(128)");
            log.info("Schema patch: metas_financeiras — colunas Cronos verificadas.");
        } catch (Exception e) {
            log.warn("Falha ao aplicar colunas Cronos em metas_financeiras: {}", e.getMessage());
        }
    }

    private void ensureAuditLogTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.audit_log ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "tipo VARCHAR(64),"
                    + "descricao TEXT NOT NULL,"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );
            executeDdlAutocommit("CREATE INDEX IF NOT EXISTS idx_audit_log_usuario ON public.audit_log(usuario_id)");
            log.info("Schema patch: audit_log verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE audit_log: {}", e.getMessage());
        }
    }

    private void ensureJarvisCronosEventLogTable() {
        try {
            executeDdlAutocommit(
                "CREATE TABLE IF NOT EXISTS public.jarvis_cronos_evento_log ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "usuario_id BIGINT NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,"
                    + "event_fingerprint VARCHAR(256) NOT NULL,"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "CONSTRAINT uk_cronos_evento UNIQUE(usuario_id, event_fingerprint)"
                    + ")"
            );
            executeDdlAutocommit(
                "CREATE INDEX IF NOT EXISTS idx_cronos_evento_usuario ON public.jarvis_cronos_evento_log(usuario_id)");
            log.info("Schema patch: jarvis_cronos_evento_log verificada.");
        } catch (Exception e) {
            log.warn("Falha ao CREATE jarvis_cronos_evento_log: {}", e.getMessage());
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

    /**
     * pgvector + memória semântica J.A.R.V.I.S. (uma tabela por schema que contém {@code usuarios}).
     */
    private void ensurePgvectorExtensionAndMemoriaSemanticaJarvisTable() {
        try {
            executeDdlAutocommit("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (Exception e) {
            log.warn(
                "Schema patch: CREATE EXTENSION vector falhou (ex.: sem superuser). "
                    + "Instale pgvector no Postgres ou conceda privilégio: {}",
                e.getMessage());
        }
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
                log.warn("Schema patch: 'usuarios' inexistente; memoria_semantica_jarvis não criada.");
                return;
            }
            boolean vectorReady = isPgVectorExtensionInstalled();
            String embeddingCol = vectorReady ? "embedding vector(1536)" : "embedding BYTEA";
            if (!vectorReady) {
                log.warn(
                    "Schema patch: pgvector não disponível nesta imagem Postgres — criando memoria_semantica_jarvis com embedding BYTEA "
                        + "(consultas só texto funcionam; busca por similaridade vetorial usa imagem pgvector/pgvector ou CREATE EXTENSION).");
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedUsuarios = schema + ".usuarios";
                String qualified = schema + ".memoria_semantica_jarvis";
                try {
                    executeDdlAutocommit(
                        "CREATE TABLE IF NOT EXISTS " + qualified + " ("
                            + "id BIGSERIAL PRIMARY KEY,"
                            + "usuario_id BIGINT NOT NULL REFERENCES " + qualifiedUsuarios + "(id) ON DELETE CASCADE,"
                            + "contexto TEXT NOT NULL,"
                            + embeddingCol + ","
                            + "data_registro TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),"
                            + "categoria_origem VARCHAR(32) NOT NULL "
                            + "CHECK (categoria_origem IN ('FINANCAS','HABITO','AGENDA'))"
                            + ")"
                    );
                    executeDdlAutocommit(
                        "CREATE INDEX IF NOT EXISTS idx_mem_sem_jarvis_usuario_registro ON "
                            + qualified + " (usuario_id, data_registro DESC)"
                    );
                } catch (Exception e) {
                    log.warn("Schema patch memoria_semantica_jarvis [{}] em {} falhou: {}", schema, qualified, e.getMessage());
                }
            }
            log.info(
                "Schema patch: memoria_semantica_jarvis processada para {} schema(s); pgvector={}.",
                schemas.size(),
                vectorReady);
        } catch (Exception e) {
            log.warn(
                "Schema patch memória semântica (liste schemas / extensões): {}",
                e.getMessage());
        }
    }

    /**
     * Índice pgvector por transação (RAG analítico — Protocolo Memória Semântica nas movimentações).
     */
    private void ensureTransacaoSemanticaIndexTable() {
        try {
            executeDdlAutocommit("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (Exception e) {
            log.warn(
                "Schema patch transacao_semantica_index: CREATE EXTENSION vector falhou: {}",
                e.getMessage());
        }
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.tables "
                    + "WHERE table_name = 'transacoes' "
                    + "  AND table_type = 'BASE TABLE' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            if (schemas == null || schemas.isEmpty()) {
                log.warn("Schema patch: 'transacoes' inexistente; transacao_semantica_index não criada.");
                return;
            }
            boolean vectorReadyTx = isPgVectorExtensionInstalled();
            String embeddingColTx = vectorReadyTx ? "embedding vector(1536)" : "embedding BYTEA";
            if (!vectorReadyTx) {
                log.warn(
                    "Schema patch: criando transacao_semantica_index com embedding BYTEA (pgvector ausente;"
                        + " RAG por similaridade em transações fica indisponível até imagem/pgvector ou CREATE EXTENSION).");
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qTx = schema + ".transacoes";
                String qUs = schema + ".usuarios";
                String qIdx = schema + ".transacao_semantica_index";
                try {
                    executeDdlAutocommit(
                        "CREATE TABLE IF NOT EXISTS " + qIdx + " ("
                            + "id BIGSERIAL PRIMARY KEY,"
                            + "transacao_id BIGINT NOT NULL REFERENCES " + qTx + "(id) ON DELETE CASCADE,"
                            + "usuario_id BIGINT NOT NULL REFERENCES " + qUs + "(id) ON DELETE CASCADE,"
                            + "texto_indexado TEXT NOT NULL,"
                            + embeddingColTx + ","
                            + "atualizado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),"
                            + "CONSTRAINT uq_transacao_semantica_tx UNIQUE (transacao_id)"
                            + ")"
                    );
                    executeDdlAutocommit(
                        "CREATE INDEX IF NOT EXISTS idx_transacao_semantica_usuario ON " + qIdx + " (usuario_id)"
                    );
                } catch (Exception inner) {
                    log.warn("Schema patch transacao_semantica_index [{}] falhou em {}: {}", schema, qIdx, inner.getMessage());
                }
            }
            log.info(
                "Schema patch: transacao_semantica_index verificada em {} schema(s); pgvector={}.",
                schemas.size(),
                vectorReadyTx);
        } catch (Exception e) {
            log.warn("Schema patch transacao_semantica_index: {}", e.getMessage());
        }
    }

    private void ensureJarvisFeedbackTable() {
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
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String u = schema + ".usuarios";
                String t = schema + ".jarvis_feedback";
                executeDdlAutocommit(
                    "CREATE TABLE IF NOT EXISTS " + t + " ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "usuario_id BIGINT NOT NULL REFERENCES " + u + "(id) ON DELETE CASCADE,"
                        + "insight_id VARCHAR(120) NOT NULL,"
                        + "tipo_alvo VARCHAR(32) NOT NULL,"
                        + "positivo BOOLEAN NOT NULL,"
                        + "categoria_chave VARCHAR(200),"
                        + "data_registro TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()"
                        + ")"
                );
                executeDdlAutocommit(
                    "CREATE INDEX IF NOT EXISTS idx_jarvis_fb_usuario_registro ON " + t
                        + " (usuario_id, data_registro DESC)"
                );
            }
            log.info("Schema patch: jarvis_feedback verificada em {} schema(s).", schemas.size());
        } catch (Exception e) {
            log.warn("Schema patch jarvis_feedback: {}", e.getMessage());
        }
    }

    private void ensureJarvisFeedbackDataExpiracaoColumn() {
        try {
            List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema "
                    + "FROM information_schema.tables "
                    + "WHERE table_name = 'jarvis_feedback' "
                    + "  AND table_type = 'BASE TABLE' "
                    + "  AND table_schema NOT IN ('pg_catalog', 'information_schema')",
                String.class
            );
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String t = schema + ".jarvis_feedback";
                executeDdlAutocommit(
                    "ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS data_expiracao TIMESTAMP WITHOUT TIME ZONE");
            }
        } catch (Exception e) {
            log.warn("Schema patch jarvis_feedback.data_expiracao: {}", e.getMessage());
        }
    }

    private void ensureUsuarioSessoesContextoTable() {
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
                return;
            }
            for (String rawSchema : schemas) {
                String schema = rawSchema.replace("\"", "");
                String qualifiedTable = schema + ".usuario_sessoes_contexto";
                String qualifiedUsuarios = schema + ".usuarios";
                executeDdlAutocommit(
                    "CREATE TABLE IF NOT EXISTS " + qualifiedTable + " ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "usuario_id BIGINT NOT NULL REFERENCES " + qualifiedUsuarios + "(id) ON DELETE CASCADE,"
                        + "canal VARCHAR(32) NOT NULL DEFAULT 'WHATSAPP',"
                        + "chave_sessao VARCHAR(128) NOT NULL,"
                        + "contexto_json TEXT NOT NULL,"
                        + "expira_em TIMESTAMP,"
                        + "atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE (usuario_id, canal, chave_sessao)"
                        + ")"
                );
                log.info("Schema patch: tabela {} verificada.", qualifiedTable);
            }
        } catch (Exception e) {
            log.warn("Falha ao CREATE usuario_sessoes_contexto: {}", e.getMessage());
        }
    }
}

