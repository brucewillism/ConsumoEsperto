package com.consumoesperto.integration;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.CompraParcelada;
import com.consumoesperto.model.CompraParcelada.StatusCompra;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.CompraParceladaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.legado.CompraParceladaMigracaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migração CompraParcelada em PostgreSQL real (banco consumoesperto_integracao).
 * Executar com backend parado:
 * INTEGRACAO_POSTGRES_TEST=1 mvn test -Dtest=CompraParceladaMigracaoPostgresIntegrationTest
 */
@SpringBootTest
@ActiveProfiles("integracao-postgres-test")
@EnabledIfEnvironmentVariable(named = "INTEGRACAO_POSTGRES_TEST", matches = "1")
class CompraParceladaMigracaoPostgresIntegrationTest {

    @Autowired private CompraParceladaMigracaoService migracaoService;
    @Autowired private CompraParceladaRepository compraRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CartaoCreditoRepository cartaoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long usuarioId;
    private CartaoCredito cartao;
    private Categoria categoria;

    @BeforeEach
    void seed() {
        String s = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("pgmig_" + s);
        u.setEmail("pgmig_" + s + "@test.local");
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("PG Mig");
        usuarioId = usuarioRepository.save(u).getId();

        categoria = new Categoria();
        categoria.setNome("Cat PG " + s);
        categoria.setUsuario(u);
        categoria = categoriaRepository.save(categoria);

        cartao = new CartaoCredito();
        cartao.setNome("Cartao PG");
        cartao.setBanco("Teste");
        cartao.setNumeroCartao("4111111111111111");
        cartao.setDiaVencimento(10);
        cartao.setUsuario(u);
        cartao.setLimiteCredito(new BigDecimal("10000"));
        cartao.setLimiteDisponivel(new BigDecimal("10000"));
        cartao.setAtivo(true);
        cartao = cartaoRepository.save(cartao);

        criar("100.00", 4, StatusCompra.ATIVA);
        criar("100.00", 3, StatusCompra.ATIVA);
        criar("50.00", 2, StatusCompra.CANCELADA);
    }

    @Test
    @Transactional
    void dryRun_migracao_real_idempotencia_postgres() {
        long txAntes = transacaoRepository.count();

        Map<String, Object> dry = migracaoService.executarMigracao(usuarioId, true);
        assertEquals(2, ((Number) dry.get("migradas")).intValue());
        assertEquals(1, ((Number) dry.get("erros")).intValue());
        assertEquals(txAntes, transacaoRepository.count());

        Map<String, Object> real = migracaoService.executarMigracao(usuarioId, false);
        assertEquals(2, ((Number) real.get("migradas")).intValue());
        assertTrue(transacaoRepository.count() > txAntes);

        Map<String, Object> segunda = migracaoService.executarMigracao(usuarioId, false);
        assertEquals(0, ((Number) segunda.get("migradas")).intValue());
        assertTrue(((Number) segunda.get("ignoradas")).intValue() >= 2);

        List<BigDecimal> parcelas = CompraParceladaMigracaoService.distribuirParcelas(new BigDecimal("100.00"), 3);
        BigDecimal soma = parcelas.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), soma);
    }

    @Test
    void rollbackAposMigracao_idempotente_postgres() {
        long txAntes = transacaoRepository.count();
        int comprasLegadas = compraRepository.findByCartaoCreditoUsuarioId(usuarioId).size();

        Map<String, Object> mig = migracaoService.executarMigracao(usuarioId, false);
        assertEquals(2, ((Number) mig.get("migradas")).intValue());
        long txPosMigracao = transacaoRepository.count();
        assertTrue(txPosMigracao > txAntes);

        Integer controlesPos = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM compra_parcelada_migracao_controle WHERE usuario_id = ?",
            Integer.class, usuarioId);
        assertTrue(controlesPos >= 2);

        Map<String, Object> rb1 = migracaoService.rollback(usuarioId, false);
        assertTrue(((Number) rb1.get("registros")).intValue() >= 2);
        long txPosRollback = transacaoRepository.count();
        assertTrue(txPosRollback < txPosMigracao);

        Integer controlesPosRb = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM compra_parcelada_migracao_controle WHERE usuario_id = ?",
            Integer.class, usuarioId);
        assertEquals(0, controlesPosRb.intValue());

        assertEquals(comprasLegadas, compraRepository.findByCartaoCreditoUsuarioId(usuarioId).size());

        Map<String, Object> rb2 = migracaoService.rollback(usuarioId, false);
        assertEquals(0, ((Number) rb2.get("registros")).intValue());
    }

    private void criar(String total, int parcelas, StatusCompra status) {
        CompraParcelada c = new CompraParcelada();
        c.setDescricao("Compra PG");
        c.setValorTotal(new BigDecimal(total));
        c.setValorParcela(new BigDecimal(total).divide(BigDecimal.valueOf(parcelas), 2, java.math.RoundingMode.HALF_UP));
        c.setNumeroParcelas(parcelas);
        c.setParcelaAtual(1);
        c.setDataCompra(LocalDateTime.now());
        c.setDataPrimeiraParcela(LocalDateTime.now());
        c.setCartaoCredito(cartao);
        c.setCategoria(categoria);
        c.setStatusCompra(status);
        compraRepository.save(c);
    }
}
