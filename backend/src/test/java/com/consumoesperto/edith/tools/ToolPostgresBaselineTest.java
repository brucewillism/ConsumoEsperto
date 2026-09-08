package com.consumoesperto.edith.tools;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Baseline e curva de search em Postgres real (embedded, planejador PG).
 * Isolado do {@code consumo_db}. Escreve {@code target/pg-baseline.txt}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ToolPostgresBaselineTest {

    private static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stopPg() throws Exception {
        PG.close();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.auto-commit", () -> "true");
        registry.add("spring.jpa.properties.hibernate.connection.provider_disables_autocommit", () -> "false");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("TEST_JPA_DIALECT", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("TEST_DATASOURCE_DRIVER", () -> "org.postgresql.Driver");
        registry.add("TEST_DDL_AUTO", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ContaBancariaRepository contaBancariaRepository;
    @Autowired private CartaoCreditoRepository cartaoCreditoRepository;
    @Autowired private FaturaRepository faturaRepository;
    @Autowired private FinanceAccountsListTool accountsListTool;
    @Autowired private FinanceTransactionsSearchTool transactionsSearchTool;
    @Autowired private FinanceInvoiceReadTool invoiceReadTool;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void medePostgresECurvaSearch() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fx = tx.execute(status -> seed());

        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_transacoes_usuario_periodo_categoria");
        jdbcTemplate.execute(
            "CREATE INDEX idx_transacoes_usuario_periodo_categoria "
                + "ON transacoes (usuario_id, data_transacao DESC, categoria_id)"
        );

        Map<String, Object> empty = Map.of();
        Map<String, Object> searchIn = new HashMap<>();
        searchIn.put("date_from", LocalDate.now().minusDays(90).toString());
        searchIn.put("date_to", LocalDate.now().toString());
        searchIn.put("limit", 50);
        Map<String, Object> invoiceIn = Map.of("invoice_id", fx.invoiceId);

        int[] volumes = {2_500, 50_000, 200_000};
        int already = 0;
        StringBuilder report = new StringBuilder();
        long p95Search2500 = -1;
        long p95Search200k = -1;
        for (int volume : volumes) {
            insertTx(fx.userId, fx.catId, fx.contaId, volume - already);
            already = volume;
            jdbcTemplate.execute("ANALYZE transacoes");
            if (volume == 2_500) {
                long[] accounts = sample(() -> accountsListTool.executeForUser(fx.userId, empty), 20, 40);
                long[] search = sample(() -> transactionsSearchTool.executeForUser(fx.userId, searchIn), 20, 40);
                long[] invoice = sample(() -> invoiceReadTool.executeForUser(fx.userId, invoiceIn), 20, 40);
                p95Search2500 = p(search, 95);
                report.append(String.format(
                    "PG_BASELINE n=2500 accounts p50=%d p95=%d p99=%d | search p50=%d p95=%d p99=%d | invoice p50=%d p95=%d p99=%d%n",
                    p(accounts, 50), p(accounts, 95), p(accounts, 99),
                    p(search, 50), p(search, 95), p(search, 99),
                    p(invoice, 50), p(invoice, 95), p(invoice, 99)
                ));
            } else {
                long[] search = sample(() -> transactionsSearchTool.executeForUser(fx.userId, searchIn), 8, 24);
                report.append(String.format(
                    "PG_SCALE search n=%d p50=%d p95=%d p99=%d%n",
                    volume, p(search, 50), p(search, 95), p(search, 99)
                ));
                if (volume == 200_000) {
                    p95Search200k = p(search, 95);
                }
            }
        }
        String plan = explain(fx.userId);
        report.append("PG_EXPLAIN ").append(plan.replace('\n', ' ')).append('\n');
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "pg-baseline.txt"), report.toString());
        System.out.print(report);
        assertTrue(plan.toLowerCase().contains("idx_transacoes_usuario_periodo_categoria")
            || plan.toLowerCase().contains("index"), "índice não aparece no EXPLAIN: " + plan);
        assertTrue(p95Search200k < 150, "p95 200k=" + p95Search200k);
        assertTrue(p95Search200k <= p95Search2500 * 3 + 20,
            "curva instável 2500=" + p95Search2500 + " 200k=" + p95Search200k);
    }

    private String explain(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) "
                + "SELECT id FROM transacoes WHERE usuario_id = ? "
                + "AND data_transacao BETWEEN ? AND ? AND excluido = false "
                + "ORDER BY data_transacao DESC LIMIT 50",
            userId,
            Timestamp.valueOf(LocalDateTime.now().minusDays(90)),
            Timestamp.valueOf(LocalDateTime.now())
        );
        return rows.toString();
    }

    private void insertTx(long userId, long catId, long contaId, int n) {
        if (n <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        final int chunk = 2000;
        int remaining = n;
        int offset = 0;
        while (remaining > 0) {
            int size = Math.min(chunk, remaining);
            final int start = offset;
            jdbcTemplate.batchUpdate(
                "INSERT INTO transacoes (descricao, valor, tipo_transacao, usuario_id, categoria_id, "
                    + "conta_bancaria_id, data_transacao, data_criacao, recorrente, excluido, status_conferencia) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        int seq = start + i;
                        ps.setString(1, "Tx " + seq);
                        ps.setBigDecimal(2, BigDecimal.valueOf(10 + (seq % 90)));
                        ps.setString(3, seq % 5 == 0 ? "RECEITA" : "DESPESA");
                        ps.setLong(4, userId);
                        ps.setLong(5, catId);
                        ps.setLong(6, contaId);
                        ps.setTimestamp(7, Timestamp.valueOf(now.minusDays(seq % 120)));
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
        }
        long[] out = new long[samples];
        for (int i = 0; i < samples; i++) {
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

    private Fixture seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("pgbase_" + sfx);
        u.setEmail("pgbase_" + sfx + "@t.local");
        u.setPassword(passwordEncoder.encode("secret"));
        u.setNome("PgBaseline");
        u = usuarioRepository.save(u);
        Categoria cat = new Categoria();
        cat.setNome("Alim");
        cat.setUsuario(u);
        cat = categoriaRepository.save(cat);
        ContaBancaria conta = new ContaBancaria();
        conta.setNome("Conta");
        conta.setUsuario(u);
        conta.setTipo(ContaBancaria.TipoConta.CORRENTE);
        conta.setSaldoAtual(new BigDecimal("1000"));
        conta.setAtiva(true);
        conta = contaBancariaRepository.save(conta);
        CartaoCredito cartao = new CartaoCredito();
        cartao.setNome("Nubank");
        cartao.setBanco("Nu");
        cartao.setNumeroCartao("4111111111111111");
        cartao.setDiaVencimento(10);
        cartao.setUsuario(u);
        cartao.setLimiteCredito(new BigDecimal("5000"));
        cartao.setLimiteDisponivel(new BigDecimal("3000"));
        cartao.setAtivo(true);
        cartao = cartaoCreditoRepository.save(cartao);
        Fatura fat = new Fatura();
        fat.setNumeroFatura("PG-" + sfx);
        fat.setValorTotal(new BigDecimal("800"));
        fat.setValorFatura(new BigDecimal("800"));
        fat.setValorMinimo(new BigDecimal("80"));
        fat.setDataVencimento(LocalDateTime.now().plusDays(10));
        fat.setDataFechamento(LocalDateTime.now().minusDays(5));
        fat.setStatus(Fatura.StatusFatura.ABERTA);
        fat.setCartaoCredito(cartao);
        fat.setUsuario(u);
        fat = faturaRepository.save(fat);
        return new Fixture(u.getId(), cat.getId(), conta.getId(), fat.getId());
    }

    private record Fixture(long userId, long catId, long contaId, long invoiceId) {
    }
}
