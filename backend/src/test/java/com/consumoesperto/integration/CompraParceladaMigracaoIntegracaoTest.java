package com.consumoesperto.integration;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.CompraParcelada;
import com.consumoesperto.model.CompraParcelada.StatusCompra;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.CompraParceladaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.legado.CompraParceladaMigracaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompraParceladaMigracaoIntegracaoTest {

    @Autowired private CompraParceladaMigracaoService migracaoService;
    @Autowired private CompraParceladaRepository compraRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CartaoCreditoRepository cartaoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long usuarioId;

    @BeforeEach
    void seed() {
        String s = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("mig_" + s);
        u.setEmail("mig_" + s + "@test.local");
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Migracao");
        usuarioId = usuarioRepository.save(u).getId();

        Categoria cat = new Categoria();
        cat.setNome("Cat Mig");
        cat.setUsuario(u);
        categoriaRepository.save(cat);

        CartaoCredito cartao = new CartaoCredito();
        cartao.setNome("Cartao Mig");
        cartao.setBanco("Teste");
        cartao.setNumeroCartao("4111111111111111");
        cartao.setDiaVencimento(10);
        cartao.setUsuario(u);
        cartao.setLimiteCredito(new BigDecimal("5000"));
        cartao.setLimiteDisponivel(new BigDecimal("5000"));
        cartao.setAtivo(true);
        cartaoRepository.save(cartao);

        criarCompra(cartao, cat, "100.00", 4, StatusCompra.ATIVA);
        criarCompra(cartao, cat, "100.00", 3, StatusCompra.ATIVA);
        criarCompra(cartao, cat, "50.00", 2, StatusCompra.CANCELADA);
    }

    @Test
    void preMigracao_e_dryRun_contamComprasAtivas() {
        Map<String, Object> pre = migracaoService.relatorioPreMigracao(usuarioId);
        assertTrue(((Number) pre.get("total")).intValue() >= 3);

        Map<String, Object> dry = migracaoService.executarMigracao(usuarioId, true);
        assertEquals(2, ((Number) dry.get("migradas")).intValue());
        assertEquals(1, ((Number) dry.get("erros")).intValue());
    }

    @Test
    void somaParcelasIgualValorOriginal() {
        List<BigDecimal> p = CompraParceladaMigracaoService.distribuirParcelas(new BigDecimal("100.00"), 3);
        BigDecimal soma = p.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), soma);
    }

    private void criarCompra(CartaoCredito cartao, Categoria cat, String total, int parcelas, StatusCompra status) {
        CompraParcelada c = new CompraParcelada();
        c.setDescricao("Compra teste");
        c.setValorTotal(new BigDecimal(total));
        c.setValorParcela(new BigDecimal(total).divide(BigDecimal.valueOf(parcelas), 2, java.math.RoundingMode.HALF_UP));
        c.setNumeroParcelas(parcelas);
        c.setParcelaAtual(1);
        c.setDataCompra(LocalDateTime.now());
        c.setDataPrimeiraParcela(LocalDateTime.now());
        c.setCartaoCredito(cartao);
        c.setCategoria(cat);
        c.setStatusCompra(status);
        compraRepository.save(c);
    }
}
