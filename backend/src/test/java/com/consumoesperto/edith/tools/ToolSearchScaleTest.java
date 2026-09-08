package com.consumoesperto.edith.tools;

import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Curva de {@code finance.transactions.search} com pushdown de limit (H2).
 * Volumes: 2.500 → 50.000 → 200.000 no mesmo dataset.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ToolSearchScaleTest {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ContaBancariaRepository contaBancariaRepository;
    @Autowired private FinanceTransactionsSearchTool searchTool;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void curvaSearchComPushdown() throws Exception {
        Fixture fx = seedUser();
        Map<String, Object> input = new HashMap<>();
        input.put("date_from", LocalDate.now().minusDays(200).toString());
        input.put("date_to", LocalDate.now().toString());
        input.put("limit", 50);

        int[] volumes = {2_500, 50_000, 200_000};
        int already = 0;
        StringBuilder report = new StringBuilder("SCALE_H2 search");
        long p95At2500 = -1;
        long p95At200k = -1;
        for (int volume : volumes) {
            insertTx(fx.userId, fx.catId, fx.contaId, volume - already);
            already = volume;
            entityManager.flush();
            entityManager.clear();
            long[] samples = sample(() -> searchTool.executeForUser(fx.userId, input), 8, 24);
            long p50 = p(samples, 50);
            long p95 = p(samples, 95);
            long p99 = p(samples, 99);
            report.append(String.format(java.util.Locale.US, " | n=%d p50=%d p95=%d p99=%d", volume, p50, p95, p99));
            if (volume == 2_500) {
                p95At2500 = p95;
            }
            if (volume == 200_000) {
                p95At200k = p95;
            }
        }
        String plan = explain(fx.userId);
        report.append("\nEXPLAIN ").append(plan.replace('\n', ' '));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "scale-h2.txt"), report.toString());
        System.out.println(report);
        assertTrue(p95At200k >= 0 && p95At2500 >= 0);
        assertTrue(plan.toLowerCase().contains("idx_transacoes_usuario_periodo_categoria"),
            "índice ausente no EXPLAIN: " + plan);
        double ratio = p95At200k / (double) Math.max(1, p95At2500);
        Files.writeString(Path.of("target", "scale-h2.txt"),
            report + String.format(java.util.Locale.US, "%nH2_RATIO p95_200k/p95_2500=%.2f%n", ratio));
        assertTrue(p95At200k <= p95At2500 * 3 + 20,
            "p95 H2 não estável após limit-then-fetch: 2500=" + p95At2500 + " 200k=" + p95At200k);
    }

    private String explain(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "EXPLAIN SELECT id FROM transacoes WHERE usuario_id = ? "
                + "AND data_transacao BETWEEN ? AND ? AND excluido = false "
                + "ORDER BY data_transacao DESC LIMIT 50",
            userId,
            Timestamp.valueOf(LocalDateTime.now().minusDays(200)),
            Timestamp.valueOf(LocalDateTime.now())
        );
        return rows.toString();
    }

    private void insertTx(long userId, long catId, long contaId, int n) {
        if (n <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        final int chunk = 1000;
        int remaining = n;
        int offset = 0;
        while (remaining > 0) {
            int size = Math.min(chunk, remaining);
            final int start = offset;
            jdbcTemplate.batchUpdate(
                "INSERT INTO transacoes (descricao, valor, tipo_transacao, usuario_id, categoria_id, "
                    + "conta_bancaria_id, data_transacao, data_criacao, recorrente, excluido, status_conferencia) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                        int seq = start + i;
                        ps.setString(1, "Tx " + seq);
                        ps.setBigDecimal(2, BigDecimal.valueOf(10 + (seq % 90)));
                        ps.setString(3, seq % 5 == 0 ? "RECEITA" : "DESPESA");
                        ps.setLong(4, userId);
                        ps.setLong(5, catId);
                        ps.setLong(6, contaId);
                        ps.setTimestamp(7, Timestamp.valueOf(now.minusDays(seq % 200)));
                        ps.setTimestamp(8, Timestamp.valueOf(now));
                        ps.setBoolean(9, false);
                        ps.setBoolean(10, false);
                        ps.setString(11, "CONFIRMADA");
                    }

                    @Override
                    public int getBatchSize() {
                        return size;
                    }
                }
            );
            remaining -= size;
            offset += size;
        }
    }

    private long[] sample(Runnable action, int warmup, int samples) {
        for (int i = 0; i < warmup; i++) {
            action.run();
            entityManager.clear();
        }
        long[] out = new long[samples];
        for (int i = 0; i < samples; i++) {
            entityManager.clear();
            long t0 = System.nanoTime();
            action.run();
            out[i] = (System.nanoTime() - t0) / 1_000_000L;
        }
        Arrays.sort(out);
        return out;
    }

    private static long p(long[] sorted, int percentile) {
        int idx = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private Fixture seedUser() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("scale_" + sfx);
        u.setEmail("scale_" + sfx + "@t.local");
        u.setPassword(passwordEncoder.encode("secret"));
        u.setNome("Scale");
        u = usuarioRepository.save(u);
        Categoria cat = new Categoria();
        cat.setNome("Alim");
        cat.setUsuario(u);
        cat = categoriaRepository.save(cat);
        ContaBancaria c = new ContaBancaria();
        c.setNome("Conta");
        c.setUsuario(u);
        c.setTipo(ContaBancaria.TipoConta.CORRENTE);
        c.setSaldoAtual(new BigDecimal("1000"));
        c.setAtiva(true);
        c = contaBancariaRepository.save(c);
        entityManager.flush();
        return new Fixture(u.getId(), cat.getId(), c.getId());
    }

    private record Fixture(long userId, long catId, long contaId) {
    }
}
