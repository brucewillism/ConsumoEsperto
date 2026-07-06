package com.consumoesperto.integration;

import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.MovimentacaoSaldoLog;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MovimentacaoSaldoLogRepository;
import com.consumoesperto.service.SaldoMovimentacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BLOCO 3 (aceite): N mutações concorrentes na mesma conta não perdem escrita —
 * o SELECT ... FOR UPDATE em {@code aplicarDelta} serializa os read-modify-write.
 * BLOCO 4 (aceite): cada mutação gera exatamente uma linha append-only em
 * {@code movimentacao_saldo_log}, com saldo_antes/saldo_depois encadeados.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("com.consumoesperto.integration.SaldoConcorrenciaPostgresIntegrationTest#dockerDisponivel")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaldoConcorrenciaPostgresIntegrationTest {

    static boolean dockerDisponivel() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("consumo_test")
        .withUsername("consumo")
        .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired private ContaBancariaRepository contaBancariaRepository;
    @Autowired private MovimentacaoSaldoLogRepository movimentacaoSaldoLogRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private SaldoMovimentacaoService service;
    private TransactionTemplate tx;
    private Long contaId;

    @BeforeEach
    void setUp() {
        service = new SaldoMovimentacaoService(contaBancariaRepository, movimentacaoSaldoLogRepository);
        tx = new TransactionTemplate(transactionManager);
        contaId = tx.execute(status -> {
            Usuario u = new Usuario();
            u.setUsername("conc-" + System.nanoTime());
            u.setEmail("conc" + System.nanoTime() + "@t.com");
            u.setNome("Concorrência");
            entityManager.persist(u);
            ContaBancaria conta = new ContaBancaria();
            conta.setNome("Conta Corrente");
            conta.setTipo(ContaBancaria.TipoConta.CORRENTE);
            conta.setSaldoAtual(BigDecimal.ZERO);
            conta.setSaldoInicial(BigDecimal.ZERO);
            conta.setUsuario(u);
            entityManager.persist(conta);
            return conta.getId();
        });
    }

    @Test
    void mutacoesConcorrentes_naoPerdemEscrita() throws Exception {
        int threads = 8;
        int operacoesPorThread = 5;
        BigDecimal credito = new BigDecimal("10.00");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch prontos = new CountDownLatch(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                prontos.countDown();
                largada.await();
                for (int op = 0; op < operacoesPorThread; op++) {
                    tx.executeWithoutResult(status -> service.creditarConta(contaId, credito));
                }
                return null;
            }));
        }
        prontos.await();
        largada.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        BigDecimal esperado = credito.multiply(BigDecimal.valueOf((long) threads * operacoesPorThread));
        BigDecimal saldoFinal = tx.execute(status ->
            contaBancariaRepository.findById(contaId).orElseThrow().getSaldoAtual());
        assertEquals(0, saldoFinal.compareTo(esperado),
            "escrita perdida: esperado " + esperado + ", obtido " + saldoFinal);
    }

    @Test
    void cadaMutacaoGeraUmaLinhaDeAuditoriaEncadeada() {
        for (int i = 0; i < 3; i++) {
            tx.executeWithoutResult(status -> service.creditarConta(contaId, new BigDecimal("25.00")));
        }

        List<MovimentacaoSaldoLog> linhas = tx.execute(status ->
            movimentacaoSaldoLogRepository.findUltimasPorConta(contaId, PageRequest.of(0, 10)));
        assertEquals(3, linhas.size());
        // Mais recente primeiro: saldo_antes de cada linha == saldo_depois da anterior
        assertEquals(0, linhas.get(0).getSaldoDepois().compareTo(new BigDecimal("75.00")));
        assertEquals(0, linhas.get(0).getSaldoAntes().compareTo(linhas.get(1).getSaldoDepois()));
        assertEquals(0, linhas.get(1).getSaldoAntes().compareTo(linhas.get(2).getSaldoDepois()));
        assertEquals(0, linhas.get(2).getSaldoAntes().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(MovimentacaoSaldoLog.TipoOperacaoSaldo.CREDITO_DIRETO, linhas.get(0).getTipoOperacao());
        assertEquals(MovimentacaoSaldoLog.OrigemMovimentacaoSaldo.APP, linhas.get(0).getOrigem());
    }

    @Test
    void ajustesAbsolutosConcorrentes_serializamPeloLock() throws Exception {
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch prontos = new CountDownLatch(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final BigDecimal alvo = new BigDecimal("1000.00").add(BigDecimal.valueOf(i * 100L));
            futures.add(pool.submit(() -> {
                prontos.countDown();
                largada.await();
                tx.executeWithoutResult(status -> service.ajustarSaldoManual(contaId, alvo));
                return null;
            }));
        }
        prontos.await();
        largada.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        List<MovimentacaoSaldoLog> linhas = tx.execute(status ->
            movimentacaoSaldoLogRepository.findUltimasPorConta(contaId, PageRequest.of(0, 10)));
        assertEquals(4, linhas.size());
        BigDecimal saldoFinal = tx.execute(status ->
            contaBancariaRepository.findById(contaId).orElseThrow().getSaldoAtual());
        assertEquals(0, linhas.get(0).getSaldoDepois().compareTo(saldoFinal),
            "saldo final deve coincidir com última linha do ledger após ajustes serializados");
    }
}
