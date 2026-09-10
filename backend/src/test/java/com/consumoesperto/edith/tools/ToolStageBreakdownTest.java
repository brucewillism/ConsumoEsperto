package com.consumoesperto.edith.tools;

import com.consumoesperto.eco.CapabilityStageClock;
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
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Breakdown dos ~20 ms de {@code finance.transactions.search} (e contas/fatura).
 * Só mede. Não otimiza. Postgres embedded, isolado do {@code consumo_db}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ToolStageBreakdownTest {

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
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @Test
    void medeEstagiosSearchAccountsInvoice() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fx = tx.execute(status -> seed());
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_transacoes_usuario_periodo_categoria");
        jdbcTemplate.execute(
            "CREATE INDEX idx_transacoes_usuario_periodo_categoria "
                + "ON transacoes (usuario_id, data_transacao DESC, categoria_id)"
        );
        insertTx(fx.userId, fx.catId, fx.contaId, 2_500);
        jdbcTemplate.execute("ANALYZE transacoes");

        Map<String, Object> empty = Map.of();
        Map<String, Object> searchIn = new HashMap<>();
        searchIn.put("date_from", LocalDate.now().minusDays(90).toString());
        searchIn.put("date_to", LocalDate.now().toString());
        searchIn.put("limit", 50);
        Map<String, Object> invoiceIn = Map.of("invoice_id", fx.invoiceId);

        StageStats search = sampleStages(() -> transactionsSearchTool.executeForUser(fx.userId, searchIn), 12, 32);
        StageStats accounts = sampleStages(() -> accountsListTool.executeForUser(fx.userId, empty), 12, 32);
        StageStats invoice = sampleStages(() -> invoiceReadTool.executeForUser(fx.userId, invoiceIn), 12, 32);

        JsonStats searchJson = sampleJson(() -> transactionsSearchTool.executeForUser(fx.userId, searchIn), 8, 16);
        JsonStats accountsJson = sampleJson(() -> accountsListTool.executeForUser(fx.userId, empty), 8, 16);
        JsonStats invoiceJson = sampleJson(() -> invoiceReadTool.executeForUser(fx.userId, invoiceIn), 8, 16);

        HttpStats searchHttp = sampleHttp(fx.token, "finance.transactions.search",
            "{\"input\":{\"date_from\":\"" + searchIn.get("date_from") + "\",\"date_to\":\""
                + searchIn.get("date_to") + "\",\"limit\":50}}", 4, 12);
        HttpStats accountsHttp = sampleHttp(fx.token, "finance.accounts.list", "{\"input\":{}}", 4, 12);
        HttpStats invoiceHttp = sampleHttp(fx.token, "finance.invoice.read",
            "{\"input\":{\"invoice_id\":" + fx.invoiceId + "}}", 4, 12);

        String report = formatReport(search, accounts, invoice, searchJson, accountsJson, invoiceJson,
            searchHttp, accountsHttp, invoiceHttp);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "stage-breakdown.txt"), report);
        System.out.print(report);

        assertTrue(search.p95("t_tool_ms") >= 0);
        assertFalse(dominant(search, searchStages()).isBlank());
    }

    private StageStats sampleStages(RunnableWithResult action, int warmup, int samples) {
        for (int i = 0; i < warmup; i++) {
            CapabilityStageClock.enable();
            try {
                action.run();
            } finally {
                CapabilityStageClock.disable();
            }
        }
        List<Map<String, Double>> snaps = new ArrayList<>();
        double[] tools = new double[samples];
        for (int i = 0; i < samples; i++) {
            CapabilityStageClock.enable();
            long t0 = System.nanoTime();
            try {
                action.run();
            } finally {
                tools[i] = (System.nanoTime() - t0) / 1_000_000.0;
                Map<String, Double> snap = new LinkedHashMap<>(CapabilityStageClock.snapshotMs());
                snap.put(CapabilityStageClock.TOOL, tools[i]);
                double accounted = 0;
                for (String k : new String[] {
                    CapabilityStageClock.POOL, CapabilityStageClock.OWNERSHIP, CapabilityStageClock.SQL_IDS,
                    CapabilityStageClock.JPA_HYDRATE, CapabilityStageClock.RECONCILE, CapabilityStageClock.DTO_MAP
                }) {
                    accounted += snap.getOrDefault(k, 0.0);
                }
                snap.put(CapabilityStageClock.UNACCOUNTED, Math.max(0.0, tools[i] - accounted));
                snaps.add(snap);
                CapabilityStageClock.disable();
            }
        }
        return new StageStats(snaps, tools);
    }

    private JsonStats sampleJson(RunnableWithResult action, int warmup, int samples) throws Exception {
        for (int i = 0; i < warmup; i++) {
            objectMapper.writeValueAsString(action.run());
        }
        double[] json = new double[samples];
        double[] tool = new double[samples];
        for (int i = 0; i < samples; i++) {
            long t0 = System.nanoTime();
            Object result = action.run();
            tool[i] = (System.nanoTime() - t0) / 1_000_000.0;
            long j0 = System.nanoTime();
            objectMapper.writeValueAsString(result);
            json[i] = (System.nanoTime() - j0) / 1_000_000.0;
        }
        return new JsonStats(p(tool, 95), p(json, 95));
    }

    private HttpStats sampleHttp(String token, String capability, String body, int warmup, int samples) throws Exception {
        for (int i = 0; i < warmup; i++) {
            mockMvc.perform(post("/api/capabilities/" + capability + ":invoke")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
        }
        double[] total = new double[samples];
        for (int i = 0; i < samples; i++) {
            long t0 = System.nanoTime();
            mockMvc.perform(post("/api/capabilities/" + capability + ":invoke")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
            total[i] = (System.nanoTime() - t0) / 1_000_000.0;
        }
        return new HttpStats(p(total, 50), p(total, 95));
    }

    private static String formatReport(
        StageStats search, StageStats accounts, StageStats invoice,
        JsonStats searchJson, JsonStats accountsJson, JsonStats invoiceJson,
        HttpStats searchHttp, HttpStats accountsHttp, HttpStats invoiceHttp
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("STAGE_BREAKDOWN postgres n=2500 (não otimizado)\n");
        sb.append(formatCap("finance.transactions.search", search, searchJson, searchHttp, searchStages()));
        sb.append(formatCap("finance.accounts.list", accounts, accountsJson, accountsHttp, accountsStages()));
        sb.append(formatCap("finance.invoice.read", invoice, invoiceJson, invoiceHttp, invoiceStages()));
        return sb.toString();
    }

    private static String formatCap(String id, StageStats st, JsonStats js, HttpStats http, String[] stages) {
        String dom = dominant(st, stages);
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(id).append('\n');
        sb.append(String.format("  t_tool_ms p50=%.2f p95=%.2f%n", st.p50(CapabilityStageClock.TOOL), st.p95(CapabilityStageClock.TOOL)));
        for (String s : stages) {
            sb.append(String.format("  %s p50=%.3f p95=%.3f%n", s, st.p50(s), st.p95(s)));
        }
        if (st.has(CapabilityStageClock.JPA_ITEMS)) {
            sb.append(String.format("  %s (subset de t_dto_map_ms) p50=%.3f p95=%.3f%n",
                CapabilityStageClock.JPA_ITEMS, st.p50(CapabilityStageClock.JPA_ITEMS), st.p95(CapabilityStageClock.JPA_ITEMS)));
        }
        sb.append(String.format("  t_unaccounted_ms p95=%.3f%n", st.p95(CapabilityStageClock.UNACCOUNTED)));
        sb.append(String.format("  t_json_ms p95=%.3f (ObjectMapper, fora do HTTP)%n", js.jsonP95));
        sb.append(String.format("  t_total_ms HTTP invoke p50=%.2f p95=%.2f%n", http.p50, http.p95));
        sb.append(String.format("  t_framework_ms p95=%.2f (HTTP − t_tool_ms p95)%n",
            Math.max(0, http.p95 - st.p95(CapabilityStageClock.TOOL))));
        sb.append("  dominante=").append(dom).append('\n');
        return sb.toString();
    }

    private static String[] searchStages() {
        return new String[] {
            CapabilityStageClock.POOL, CapabilityStageClock.OWNERSHIP, CapabilityStageClock.SQL_IDS,
            CapabilityStageClock.JPA_HYDRATE, CapabilityStageClock.DTO_MAP
        };
    }

    private static String[] accountsStages() {
        return new String[] {
            CapabilityStageClock.POOL, CapabilityStageClock.JPA_HYDRATE, CapabilityStageClock.DTO_MAP
        };
    }

    private static String[] invoiceStages() {
        return new String[] {
            CapabilityStageClock.POOL, CapabilityStageClock.JPA_HYDRATE, CapabilityStageClock.RECONCILE,
            CapabilityStageClock.DTO_MAP
        };
    }

    private static String dominant(StageStats st, String[] stages) {
        String best = stages[0];
        double max = -1;
        for (String s : stages) {
            double v = st.p95(s);
            if (v > max) {
                max = v;
                best = s;
            }
        }
        return best;
    }

    private void insertTx(long userId, long catId, long contaId, int n) {
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

    private Fixture seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("stg_" + sfx);
        u.setEmail("stg_" + sfx + "@t.local");
        u.setPassword(passwordEncoder.encode("secret"));
        u.setNome("Stage");
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
        fat.setNumeroFatura("STG-" + sfx);
        fat.setValorTotal(new BigDecimal("800"));
        fat.setValorFatura(new BigDecimal("800"));
        fat.setValorMinimo(new BigDecimal("80"));
        fat.setDataVencimento(LocalDateTime.now().plusDays(10));
        fat.setDataFechamento(LocalDateTime.now().minusDays(5));
        fat.setStatus(Fatura.StatusFatura.ABERTA);
        fat.setCartaoCredito(cartao);
        fat.setUsuario(u);
        fat = faturaRepository.save(fat);
        UserPrincipal principal = UserPrincipal.create(u);
        String token = "Bearer " + jwtTokenProvider.generateToken(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        return new Fixture(u.getId(), cat.getId(), conta.getId(), fat.getId(), token);
    }

    private static double p(double[] values, int percentile) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int idx = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    @FunctionalInterface
    private interface RunnableWithResult {
        Object run();
    }

    private record Fixture(long userId, long catId, long contaId, long invoiceId, String token) {
    }

    private static final class StageStats {
        private final List<Map<String, Double>> snaps;
        private final double[] tools;

        StageStats(List<Map<String, Double>> snaps, double[] tools) {
            this.snaps = snaps;
            this.tools = tools;
        }

        boolean has(String key) {
            return snaps.stream().anyMatch(s -> s.containsKey(key));
        }

        double p50(String key) {
            return p(key, 50);
        }

        double p95(String key) {
            return p(key, 95);
        }

        private double p(String key, int percentile) {
            if (CapabilityStageClock.TOOL.equals(key)) {
                return ToolStageBreakdownTest.p(tools, percentile);
            }
            double[] v = new double[snaps.size()];
            for (int i = 0; i < snaps.size(); i++) {
                v[i] = snaps.get(i).getOrDefault(key, 0.0);
            }
            return ToolStageBreakdownTest.p(v, percentile);
        }
    }

    private record JsonStats(double toolP95, double jsonP95) {
    }

    private record HttpStats(double p50, double p95) {
    }
}
