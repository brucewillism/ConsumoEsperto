package com.consumoesperto.edith.tools;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.EdithTaskLink;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.EdithTaskLinkRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mede p50/p95/p99 das tools read-only sob volume realista (H2 local).
 * Números gravados em {@code docs/BASELINE_TOOLS.md} após a execução desta suíte.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ToolLatencyBaselineTest {

    private static final int ACCOUNTS = 12;
    private static final int TRANSACTIONS = 2500;
    private static final int INVOICES = 24;
    private static final int WARMUP = 20;
    private static final int SAMPLES = 80;

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ContaBancariaRepository contaBancariaRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private CartaoCreditoRepository cartaoCreditoRepository;
    @Autowired private FaturaRepository faturaRepository;
    @Autowired private EdithTaskLinkRepository taskLinkRepository;
    @Autowired private FinanceAccountsListTool accountsListTool;
    @Autowired private FinanceTransactionsSearchTool transactionsSearchTool;
    @Autowired private FinanceInvoiceReadTool invoiceReadTool;
    @Autowired private EntityManager entityManager;

    @Test
    void medeToolsReadOnly() {
        Fixture fx = seed();
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> empty = Map.of();
        long[] accounts = sample(() -> accountsListTool.execute(fx.contextRef, empty));
        Map<String, Object> searchIn = new HashMap<>();
        searchIn.put("date_from", java.time.LocalDate.now().minusDays(90).toString());
        searchIn.put("date_to", java.time.LocalDate.now().toString());
        searchIn.put("limit", 50);
        long[] search = sample(() -> transactionsSearchTool.execute(fx.contextRef, searchIn));
        Map<String, Object> invoiceIn = Map.of("invoice_id", fx.invoiceId);
        long[] invoice = sample(() -> invoiceReadTool.execute(fx.contextRef, invoiceIn));

        System.out.printf(
            "BASELINE accounts p50=%d p95=%d p99=%d | search p50=%d p95=%d p99=%d | invoice p50=%d p95=%d p99=%d%n",
            p(accounts, 50), p(accounts, 95), p(accounts, 99),
            p(search, 50), p(search, 95), p(search, 99),
            p(invoice, 50), p(invoice, 95), p(invoice, 99)
        );
        assertTrue(p(accounts, 95) >= 0);
        assertTrue(p(search, 95) >= 0);
        assertTrue(p(invoice, 95) >= 0);
    }

    private long[] sample(Runnable action) {
        for (int i = 0; i < WARMUP; i++) {
            action.run();
            entityManager.clear();
        }
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            entityManager.clear();
            long t0 = System.nanoTime();
            action.run();
            samples[i] = (System.nanoTime() - t0) / 1_000_000L;
        }
        Arrays.sort(samples);
        return samples;
    }

    private static long p(long[] sorted, int percentile) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private Fixture seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("base_" + sfx);
        u.setEmail("base_" + sfx + "@t.local");
        u.setPassword(passwordEncoder.encode("secret"));
        u.setNome("Baseline");
        u = usuarioRepository.save(u);

        Categoria cat = new Categoria();
        cat.setNome("Alimentacao");
        cat.setUsuario(u);
        cat = categoriaRepository.save(cat);

        List<ContaBancaria> contas = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            ContaBancaria c = new ContaBancaria();
            c.setNome("Conta " + i);
            c.setUsuario(u);
            c.setTipo(ContaBancaria.TipoConta.CORRENTE);
            c.setSaldoAtual(new BigDecimal("1000.00"));
            c.setAtiva(true);
            c.setPadrao(i == 0);
            contas.add(contaBancariaRepository.save(c));
        }

        LocalDateTime now = LocalDateTime.now();
        List<Transacao> lote = new ArrayList<>(200);
        for (int i = 0; i < TRANSACTIONS; i++) {
            Transacao t = new Transacao();
            t.setUsuario(u);
            t.setDescricao("Tx " + i);
            t.setValor(BigDecimal.valueOf(10 + (i % 90)));
            t.setTipoTransacao(i % 5 == 0 ? Transacao.TipoTransacao.RECEITA : Transacao.TipoTransacao.DESPESA);
            t.setCategoria(cat);
            t.setContaBancaria(contas.get(i % contas.size()));
            t.setDataTransacao(now.minusDays(i % 120));
            lote.add(t);
            if (lote.size() == 200) {
                transacaoRepository.saveAll(lote);
                lote.clear();
            }
        }
        if (!lote.isEmpty()) {
            transacaoRepository.saveAll(lote);
        }

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

        Long invoiceId = null;
        for (int i = 0; i < INVOICES; i++) {
            Fatura f = new Fatura();
            f.setNumeroFatura("BL-" + sfx + "-" + i);
            f.setValorTotal(new BigDecimal("800.00"));
            f.setValorFatura(new BigDecimal("800.00"));
            f.setValorMinimo(new BigDecimal("80.00"));
            f.setDataVencimento(now.plusDays(10));
            f.setDataFechamento(now.minusDays(5));
            f.setStatus(Fatura.StatusFatura.ABERTA);
            f.setCartaoCredito(cartao);
            f.setUsuario(u);
            f = faturaRepository.save(f);
            if (invoiceId == null) {
                invoiceId = f.getId();
            }
        }

        String ctx = "ctx-base-" + sfx;
        taskLinkRepository.save(new EdithTaskLink(
            u.getId(), ctx, "conv-b-" + sfx, "msg-b-" + sfx, "task-b-" + sfx, "req-b-" + sfx, "client-b-" + sfx, "consumo.chat"
        ));
        return new Fixture(ctx, invoiceId);
    }

    private record Fixture(String contextRef, Long invoiceId) {
    }
}
