package com.consumoesperto.service.legado;

import com.consumoesperto.model.CompraParcelada;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.CompraParceladaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Migração idempotente CompraParcelada → transações parceladas (grupoParcelaId).
 * Não remover entidade legada. Executar apenas em banco de teste/staging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompraParceladaMigracaoService {

    private static final int SCALE = 2;

    private final CompraParceladaRepository compraParceladaRepository;
    private final TransacaoRepository transacaoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CompraParceladaMigracaoValidacaoService validacaoService;

    @Transactional(readOnly = true)
    public Map<String, Object> relatorioPreMigracao(Long usuarioId) {
        return validacaoService.relatorioPreMigracao(usuarioId);
    }

    @Transactional
    public Map<String, Object> executarMigracao(Long usuarioId, boolean dryRun) {
        garantirTabelaControle();
        Map<String, Object> pre = relatorioPreMigracao(usuarioId);
        List<CompraParcelada> compras = compraParceladaRepository.findByCartaoCreditoUsuarioId(usuarioId);
        int migradas = 0;
        int ignoradas = 0;
        int erros = 0;
        List<String> detalhes = new ArrayList<>();

        for (CompraParcelada compra : compras) {
            if (jaMigrada(compra.getId())) {
                ignoradas++;
                continue;
            }
            try {
                validarCompra(compra);
                List<BigDecimal> parcelas = distribuirParcelas(compra.getValorTotal(), compra.getNumeroParcelas());
                validarSomaParcelas(compra.getValorTotal(), parcelas);
                if (!dryRun) {
                    String grupoId = "CP-MIG-" + compra.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
                    criarTransacoes(compra, parcelas, grupoId);
                    marcarMigrada(compra.getId(), grupoId);
                }
                migradas++;
                detalhes.add("Compra " + compra.getId() + ": " + compra.getNumeroParcelas() + " parcelas OK");
            } catch (Exception e) {
                erros++;
                detalhes.add("Compra " + compra.getId() + " ERRO: " + e.getMessage());
                log.warn("Falha migração compra {}: {}", compra.getId(), e.getMessage());
            }
        }

        Map<String, Object> pos = new LinkedHashMap<>(pre);
        pos.put("dryRun", dryRun);
        pos.put("migradas", migradas);
        pos.put("ignoradas", ignoradas);
        pos.put("erros", erros);
        pos.put("detalhes", detalhes);
        return pos;
    }

    @Transactional
    public Map<String, Object> rollback(Long usuarioId, boolean dryRun) {
        garantirTabelaControle();
        List<Map<String, Object>> registros = jdbcTemplate.queryForList(
            "SELECT compra_parcelada_id, grupo_parcela_id FROM compra_parcelada_migracao_controle WHERE usuario_id = ?",
            usuarioId
        );
        int removidas = 0;
        for (Map<String, Object> row : registros) {
            String grupo = (String) row.get("grupo_parcela_id");
            Long compraId = ((Number) row.get("compra_parcelada_id")).longValue();
            if (!dryRun) {
                transacaoRepository.deleteAll(
                    transacaoRepository.findByUsuarioIdAndGrupoParcelaIdOrderByParcelaAtualAsc(usuarioId, grupo)
                );
                jdbcTemplate.update(
                    "DELETE FROM compra_parcelada_migracao_controle WHERE compra_parcelada_id = ?",
                    compraId
                );
            }
            removidas++;
        }
        return Map.of("rollback", true, "dryRun", dryRun, "registros", removidas);
    }

    /** Distribui centavos: parcelas iguais + ajuste na última parcela. */
    public static List<BigDecimal> distribuirParcelas(BigDecimal total, int quantidade) {
        if (total == null || quantidade <= 0) {
            throw new IllegalArgumentException("Total e quantidade devem ser positivos.");
        }
        BigDecimal base = total.divide(BigDecimal.valueOf(quantidade), SCALE, RoundingMode.DOWN);
        List<BigDecimal> parcelas = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < quantidade - 1; i++) {
            parcelas.add(base);
            acumulado = acumulado.add(base);
        }
        BigDecimal ultima = total.subtract(acumulado).setScale(SCALE, RoundingMode.HALF_UP);
        parcelas.add(ultima);
        return parcelas;
    }

    static void validarSomaParcelas(BigDecimal total, List<BigDecimal> parcelas) {
        BigDecimal soma = parcelas.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.compareTo(total.setScale(SCALE, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalStateException("Soma das parcelas " + soma + " difere do total " + total);
        }
    }

    private void validarCompra(CompraParcelada c) {
        if (c.getCartaoCredito() == null) throw new IllegalArgumentException("Cartão ausente");
        if (c.getUsuario() == null && c.getCartaoCredito().getUsuario() == null) {
            throw new IllegalArgumentException("Usuário ausente");
        }
        if (c.getValorTotal() == null || c.getNumeroParcelas() == null || c.getNumeroParcelas() <= 0) {
            throw new IllegalArgumentException("Valor ou parcelas inválidos");
        }
        if (c.getStatusCompra() == CompraParcelada.StatusCompra.CANCELADA) {
            throw new IllegalArgumentException("Compra cancelada");
        }
    }

    private void criarTransacoes(CompraParcelada compra, List<BigDecimal> parcelas, String grupoId) {
        LocalDateTime dataBase = compra.getDataPrimeiraParcela() != null
            ? compra.getDataPrimeiraParcela()
            : compra.getDataCompra();
        int inicio = compra.getParcelaAtual() != null ? compra.getParcelaAtual() : 1;
        for (int i = inicio - 1; i < parcelas.size(); i++) {
            Transacao t = new Transacao();
            t.setUsuario(compra.getUsuario() != null ? compra.getUsuario() : compra.getCartaoCredito().getUsuario());
            t.setDescricao(compra.getDescricao() + " (" + (i + 1) + "/" + parcelas.size() + ")");
            t.setValor(parcelas.get(i));
            t.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
            t.setCategoria(compra.getCategoria());
            t.setGrupoParcelaId(grupoId);
            t.setParcelaAtual(i + 1);
            t.setTotalParcelas(parcelas.size());
            t.setDataTransacao(dataBase != null ? dataBase.plusMonths(i - (inicio - 1)) : LocalDateTime.now());
            transacaoRepository.save(t);
        }
    }

    private void garantirTabelaControle() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS compra_parcelada_migracao_controle (
                compra_parcelada_id BIGINT PRIMARY KEY,
                usuario_id BIGINT NOT NULL,
                grupo_parcela_id VARCHAR(64) NOT NULL,
                migrado_em TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
    }

    private boolean jaMigrada(Long compraId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM compra_parcelada_migracao_controle WHERE compra_parcelada_id = ?",
            Integer.class,
            compraId
        );
        return count != null && count > 0;
    }

    private void marcarMigrada(Long compraId, String grupoId) {
        Long usuarioId = jdbcTemplate.queryForObject(
            "SELECT COALESCE(cp.usuario_id, c.usuario_id) FROM compras_parceladas cp LEFT JOIN cartoes_credito c ON c.id = cp.cartao_credito_id WHERE cp.id = ?",
            Long.class,
            compraId
        );
        jdbcTemplate.update(
            "INSERT INTO compra_parcelada_migracao_controle (compra_parcelada_id, usuario_id, grupo_parcela_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            compraId, usuarioId, grupoId
        );
    }
}
