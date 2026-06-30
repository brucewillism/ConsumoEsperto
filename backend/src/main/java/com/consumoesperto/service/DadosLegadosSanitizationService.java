package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Conciliação retroativa de dados legados (v8.3) — idempotente, executada uma vez por ambiente após deploy.
 * <ul>
 *   <li>Marca {@code origem_fiscal} em 13º e restituição IR recebidos sem flag.</li>
 *   <li>Desclassifica PIX/transferências erroneamente categorizados como Salário.</li>
 *   <li>Vincula categoria Salário em holerites/automáticos sem categoria.</li>
 *   <li>Remove duplicatas salário (contracheque + automático) via {@link SalarioAutomaticoService}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DadosLegadosSanitizationService {

    static final String PATCH_V83 = "v8.3-conciliacao-retroativa-salarial-fiscal";

    private final JdbcTemplate jdbcTemplate;
    private final SalarioAutomaticoService salarioAutomaticoService;

    /**
     * Invocado por {@link SchemaAutoPatchService} após DDL — não usar {@code @PostConstruct} aqui.
     */
    @Transactional
    public void aplicarPatchV83SeNecessario() {
        ensureSchemaDataPatchesTable();
        if (isPatchApplied(PATCH_V83)) {
            log.debug("Data patch {} já aplicado — ignorando.", PATCH_V83);
            return;
        }

        log.info("Iniciando data patch {} (sanitização salarial/fiscal legada)...", PATCH_V83);
        List<String> schemas = listSchemasComTransacoes();
        int tagged13 = 0;
        int taggedIr = 0;
        int reclassificadas = 0;
        int categoriaSalario = 0;
        int outrasReceitasCriadas = 0;

        for (String schema : schemas) {
            tagged13 += tagDecimoTerceiroSemOrigemFiscal(schema);
            taggedIr += tagRestituicaoIrSemOrigemFiscal(schema);
            outrasReceitasCriadas += garantirCategoriaOutrasReceitas(schema);
            reclassificadas += desclassificarReceitasAvulsasMalRotuladas(schema);
            categoriaSalario += vincularCategoriaSalarioFaltante(schema);
        }

        int podas = salarioAutomaticoService.sanitizarSalariosDuplicadosTodosUsuarios();

        markPatchApplied(PATCH_V83);
        log.info(
            "Data patch {} concluído — schemas={}, 13º={}, IR={}, reclassificadas={}, catSalário={}, "
                + "outrasReceitasCriadas={}, podasSalário={}",
            PATCH_V83,
            schemas.size(),
            tagged13,
            taggedIr,
            reclassificadas,
            categoriaSalario,
            outrasReceitasCriadas,
            podas
        );
    }

    static String resolverOrigemDecimoTerceiro(String descricao) {
        String d = descricao != null ? descricao.toLowerCase(Locale.ROOT) : "";
        if (d.contains("primeira") || d.contains("1ª") || d.contains("1a parcela")) {
            return "DECIMO_TERCEIRA_PRIMEIRA";
        }
        if (d.contains("segunda") || d.contains("2ª") || d.contains("2a parcela")) {
            return "DECIMO_TERCEIRA_SEGUNDA";
        }
        return "DECIMO_TERCEIRO_UNICO";
    }

    static boolean pareceDecimoTerceiro(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return false;
        }
        String d = descricao.toLowerCase(Locale.ROOT);
        return d.contains("13 salario")
            || d.contains("13º sal")
            || d.contains("13o sal")
            || d.contains("decimo terceiro")
            || d.contains("décimo terceiro")
            || (d.contains("13") && d.contains("sal"));
    }

    static boolean pareceRestituicaoIr(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return false;
        }
        String d = descricao.toLowerCase(Locale.ROOT);
        return d.contains("restitu") && d.contains("ir")
            || d.contains("restituicao")
            || d.contains("restituição")
            || d.contains("irrf") && d.contains("restit");
    }

    static boolean pareceReceitaAvulsaNaoSalarial(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return false;
        }
        String d = descricao.toLowerCase(Locale.ROOT);
        return d.contains("pix")
            || d.contains("transfer")
            || d.contains("ted ")
            || d.startsWith("ted ")
            || d.contains(" doc ")
            || d.contains("devolu")
            || d.contains("estorno")
            || d.contains("reembolso");
    }

    private void ensureSchemaDataPatchesTable() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS schema_data_patches ("
                + "patch_id VARCHAR(64) PRIMARY KEY, "
                + "applied_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()"
                + ")"
        );
    }

    private boolean isPatchApplied(String patchId) {
        Boolean exists = jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM schema_data_patches WHERE patch_id = ?)",
            Boolean.class,
            patchId
        );
        return Boolean.TRUE.equals(exists);
    }

    private void markPatchApplied(String patchId) {
        jdbcTemplate.update(
            "INSERT INTO schema_data_patches (patch_id) VALUES (?) ON CONFLICT (patch_id) DO NOTHING",
            patchId
        );
    }

    private List<String> listSchemasComTransacoes() {
        return jdbcTemplate.queryForList(
            "SELECT table_schema FROM information_schema.tables "
                + "WHERE table_name = 'transacoes' "
                + "AND table_type = 'BASE TABLE' "
                + "AND table_schema NOT IN ('pg_catalog', 'information_schema')",
            String.class
        );
    }

    private int tagDecimoTerceiroSemOrigemFiscal(String schema) {
        String table = schema + ".transacoes";
        List<String> descricoes = jdbcTemplate.queryForList(
            "SELECT DISTINCT descricao FROM " + table + " "
                + "WHERE tipo_transacao = 'RECEITA' "
                + "AND status_conferencia = 'CONFIRMADA' "
                + "AND excluido = false "
                + "AND origem_fiscal IS NULL "
                + "AND ("
                + "  LOWER(COALESCE(descricao, '')) LIKE '%13%sal%' "
                + "  OR LOWER(COALESCE(descricao, '')) LIKE '%13º%' "
                + "  OR LOWER(COALESCE(descricao, '')) LIKE '%decimo terceiro%' "
                + "  OR LOWER(COALESCE(descricao, '')) LIKE '%décimo terceiro%' "
                + ")",
            String.class
        );
        int total = 0;
        for (String desc : descricoes) {
            if (!pareceDecimoTerceiro(desc)) {
                continue;
            }
            String origem = resolverOrigemDecimoTerceiro(desc);
            total += jdbcTemplate.update(
                "UPDATE " + table + " SET origem_fiscal = ? "
                    + "WHERE tipo_transacao = 'RECEITA' "
                    + "AND status_conferencia = 'CONFIRMADA' "
                    + "AND excluido = false "
                    + "AND origem_fiscal IS NULL "
                    + "AND descricao = ?",
                origem,
                desc
            );
        }
        return total;
    }

    private int tagRestituicaoIrSemOrigemFiscal(String schema) {
        String table = schema + ".transacoes";
        return jdbcTemplate.update(
            "UPDATE " + table + " SET origem_fiscal = 'RESTITUICAO_IR' "
                + "WHERE tipo_transacao = 'RECEITA' "
                + "AND status_conferencia = 'CONFIRMADA' "
                + "AND excluido = false "
                + "AND origem_fiscal IS NULL "
                + "AND ("
                + "  LOWER(COALESCE(descricao, '')) LIKE '%restitu%ir%' "
                + "  OR LOWER(COALESCE(descricao, '')) LIKE '%restituição%' "
                + "  OR LOWER(COALESCE(descricao, '')) LIKE '%restituicao%' "
                + "  OR (LOWER(COALESCE(descricao, '')) LIKE '%irrf%' AND LOWER(COALESCE(descricao, '')) LIKE '%restit%')"
                + ")"
        );
    }

    private int garantirCategoriaOutrasReceitas(String schema) {
        String categorias = schema + ".categorias";
        String usuarios = schema + ".usuarios";
        return jdbcTemplate.update(
            "INSERT INTO " + categorias + " (nome, descricao, cor, icone, usuario_id, ativo) "
                + "SELECT 'Outras receitas', 'Entradas avulsas (PIX, transferências)', '#6366f1', 'coins', u.id, true "
                + "FROM " + usuarios + " u "
                + "WHERE EXISTS ("
                + "  SELECT 1 FROM " + schema + ".transacoes t "
                + "  JOIN " + categorias + " cs ON cs.id = t.categoria_id "
                + "  WHERE t.usuario_id = u.id AND t.excluido = false AND t.tipo_transacao = 'RECEITA' "
                + "  AND LOWER(cs.nome) IN ('salário', 'salario') "
                + "  AND (LOWER(COALESCE(t.descricao, '')) LIKE '%pix%' "
                + "    OR LOWER(COALESCE(t.descricao, '')) LIKE '%transfer%' "
                + "    OR LOWER(COALESCE(t.descricao, '')) LIKE '%ted%')"
                + ") "
                + "AND NOT EXISTS ("
                + "  SELECT 1 FROM " + categorias + " c "
                + "  WHERE c.usuario_id = u.id AND LOWER(c.nome) IN ('outras receitas', 'renda extra')"
                + ")"
        );
    }

    private int desclassificarReceitasAvulsasMalRotuladas(String schema) {
        String table = schema + ".transacoes";
        String categorias = schema + ".categorias";
        return jdbcTemplate.update(
            "UPDATE " + table + " t SET categoria_id = outras.id "
                + "FROM " + categorias + " sal "
                + "JOIN " + categorias + " outras ON outras.usuario_id = sal.usuario_id "
                + "WHERE t.categoria_id = sal.id "
                + "AND t.excluido = false "
                + "AND t.tipo_transacao = 'RECEITA' "
                + "AND t.origem_fiscal IS NULL "
                + "AND LOWER(sal.nome) IN ('salário', 'salario') "
                + "AND LOWER(outras.nome) = 'outras receitas' "
                + "AND ("
                + "  LOWER(COALESCE(t.descricao, '')) LIKE '%pix%' "
                + "  OR LOWER(COALESCE(t.descricao, '')) LIKE '%transfer%' "
                + "  OR LOWER(COALESCE(t.descricao, '')) LIKE '%ted%' "
                + "  OR LOWER(COALESCE(t.descricao, '')) LIKE '%doc%' "
                + "  OR LOWER(COALESCE(t.descricao, '')) LIKE '%devolu%' "
                + "  OR LOWER(COALESCE(t.descricao, '')) LIKE '%estorno%' "
                + "  OR LOWER(COALESCE(t.descricao, '')) LIKE '%reembolso%'"
                + ")"
        );
    }

    private int vincularCategoriaSalarioFaltante(String schema) {
        String table = schema + ".transacoes";
        String categorias = schema + ".categorias";
        return jdbcTemplate.update(
            "UPDATE " + table + " t SET categoria_id = sal.id "
                + "FROM " + categorias + " sal "
                + "WHERE sal.usuario_id = t.usuario_id "
                + "AND LOWER(sal.nome) IN ('salário', 'salario') "
                + "AND t.excluido = false "
                + "AND t.tipo_transacao = 'RECEITA' "
                + "AND t.status_conferencia = 'CONFIRMADA' "
                + "AND t.origem_fiscal IS NULL "
                + "AND (t.categoria_id IS NULL OR t.categoria_id NOT IN ("
                + "  SELECT c2.id FROM " + categorias + " c2 "
                + "  WHERE c2.usuario_id = t.usuario_id AND LOWER(c2.nome) IN ('salário', 'salario')"
                + ")) "
                + "AND ("
                + "  LOWER(COALESCE(t.descricao, '')) LIKE '%salário%' "
                + "  OR LOWER(COALESCE(t.descricao, '')) LIKE '%salario%' "
                + "  OR t.recorrente = true"
                + ")"
        );
    }
}
