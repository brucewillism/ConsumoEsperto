package com.consumoesperto.service;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regra única do projeto: NÃO existe dia de fechamento persistido no cartão.
 * O fechamento é derivado do vencimento (vencimento − N dias, aqui N=10).
 * Estes testes fixam o comportamento derivado em meses curtos, início de mês,
 * compras antes/depois do fechamento, ano bissexto e mudança de vencimento.
 */
@SpringBootTest(properties = "consumoesperto.fatura.dias-entre-fechamento-e-vencimento=10")
@ActiveProfiles("test")
@Transactional
class FaturaFechamentoDerivadoTest {

    @Autowired private FaturaService faturaService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CartaoCreditoRepository cartaoCreditoRepository;
    @Autowired private FaturaRepository faturaRepository;

    private Usuario usuario;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        usuario = new Usuario();
        usuario.setUsername("fech_" + sfx);
        usuario.setEmail("fech_" + sfx + "@test.local");
        usuario.setPassword("senha-de-teste-valida");
        usuario.setNome("Fechamento");
        usuario = usuarioRepository.save(usuario);
    }

    private CartaoCredito cartaoComVencimentoDia(int dia) {
        CartaoCredito c = new CartaoCredito();
        c.setNome("Cartão dia " + dia);
        c.setBanco("Banco Teste");
        c.setNumeroCartao(String.valueOf(System.nanoTime() % 10_000));
        c.setLimiteCredito(new BigDecimal("5000"));
        c.setLimiteDisponivel(new BigDecimal("5000"));
        c.setDiaVencimento(dia);
        c.setAtivo(true);
        c.setUsuario(usuario);
        return cartaoCreditoRepository.save(c);
    }

    @Test
    void mesCurto_vencimentoDia31_ajustaParaUltimoDiaDeFevereiro() {
        CartaoCredito cartao = cartaoComVencimentoDia(31);
        Fatura f = faturaService.resolverFaturaParaCompra(
            usuario.getId(), cartao, LocalDateTime.of(2031, 2, 1, 10, 0));
        assertEquals(LocalDate.of(2031, 2, 28), f.getDataVencimento().toLocalDate(),
            "Fevereiro de 2031 tem 28 dias; vencimento dia 31 deve ajustar para 28");
        assertEquals(LocalDate.of(2031, 2, 18), f.getDataFechamento().toLocalDate(),
            "Fechamento derivado = vencimento − 10 dias");
    }

    @Test
    void anoBissexto_vencimentoDia29_caiEm29DeFevereiro() {
        CartaoCredito cartao = cartaoComVencimentoDia(29);
        Fatura f = faturaService.resolverFaturaParaCompra(
            usuario.getId(), cartao, LocalDateTime.of(2032, 2, 5, 10, 0));
        assertEquals(LocalDate.of(2032, 2, 29), f.getDataVencimento().toLocalDate(),
            "2032 é bissexto; dia 29 de fevereiro é válido");
    }

    @Test
    void vencimentoInicioDoMes_compraNoMeioDoMes_vaiParaProximoCiclo() {
        CartaoCredito cartao = cartaoComVencimentoDia(1);
        Fatura f = faturaService.resolverFaturaParaCompra(
            usuario.getId(), cartao, LocalDateTime.of(2031, 3, 15, 10, 0));
        assertEquals(LocalDate.of(2031, 4, 1), f.getDataVencimento().toLocalDate(),
            "Compra em 15/03 com vencimento dia 1 deve cair na fatura de 01/04");
    }

    @Test
    void compraAntesDoFechamento_entraNaFaturaCorrente() {
        CartaoCredito cartao = cartaoComVencimentoDia(20);
        Fatura corrente = novaFaturaAberta(cartao, LocalDate.of(2031, 5, 20));

        Fatura f = faturaService.resolverFaturaParaCompra(
            usuario.getId(), cartao, LocalDateTime.of(2031, 5, 5, 10, 0));
        assertEquals(corrente.getId(), f.getId(),
            "Compra em 05/05 (antes do fechamento 10/05) entra na fatura corrente");
    }

    @Test
    void compraDepoisDoFechamento_vaiParaCicloSeguinte() {
        CartaoCredito cartao = cartaoComVencimentoDia(20);
        Fatura corrente = novaFaturaAberta(cartao, LocalDate.of(2031, 5, 20));

        Fatura f = faturaService.resolverFaturaParaCompra(
            usuario.getId(), cartao, LocalDateTime.of(2031, 5, 15, 10, 0));
        assertEquals(LocalDate.of(2031, 6, 20), f.getDataVencimento().toLocalDate(),
            "Compra em 15/05 (após fechamento 10/05) vai para a fatura de junho");
        assertEquals(false, corrente.getId().equals(f.getId()));
    }

    @Test
    void mudancaDeVencimento_novasComprasSeguemNovoDia() {
        CartaoCredito cartao = cartaoComVencimentoDia(10);
        Fatura antes = faturaService.resolverFaturaParaCompra(
            usuario.getId(), cartao, LocalDateTime.of(2031, 7, 5, 10, 0));
        assertEquals(10, antes.getDataVencimento().getDayOfMonth());

        cartao.setDiaVencimento(25);
        cartaoCreditoRepository.save(cartao);

        Fatura depois = faturaService.resolverFaturaParaCompra(
            usuario.getId(), cartao, LocalDateTime.of(2031, 9, 1, 10, 0));
        assertEquals(25, depois.getDataVencimento().getDayOfMonth(),
            "Após alterar o vencimento, novos ciclos usam o novo dia");
    }

    @Test
    void faturaExistenteDoMesAlvo_eReutilizadaSemDuplicar() {
        CartaoCredito cartao = cartaoComVencimentoDia(20);
        Fatura existente = novaFaturaAberta(cartao, LocalDate.of(2031, 8, 20));
        Fatura obtida = faturaService.obterOuCriarFaturaParaVencimentoAlvo(
            usuario.getId(), cartao, LocalDate.of(2031, 8, 20));
        assertEquals(existente.getId(), obtida.getId());
        assertEquals(1, faturaRepository.findByCartaoCreditoId(cartao.getId()).size());
    }

    private Fatura novaFaturaAberta(CartaoCredito cartao, LocalDate vencimento) {
        Fatura f = new Fatura();
        f.setCartaoCredito(cartao);
        f.setUsuario(usuario);
        f.setStatusFatura(Fatura.StatusFatura.ABERTA);
        f.setDataVencimento(vencimento.atTime(12, 0));
        f.setDataFechamento(vencimento.minusDays(10).atTime(12, 0));
        f.setValorFatura(BigDecimal.ZERO);
        f.setValorTotal(BigDecimal.ZERO);
        f.setValorPago(BigDecimal.ZERO);
        f.setValorMinimo(BigDecimal.ZERO);
        f.setPaga(false);
        f.setNumeroFatura("T-" + System.nanoTime());
        return faturaRepository.save(f);
    }
}
