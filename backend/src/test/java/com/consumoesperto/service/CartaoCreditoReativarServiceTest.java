package com.consumoesperto.service;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartaoCreditoReativarServiceTest {

    @Mock
    private CartaoCreditoRepository cartaoCreditoRepository;
    @Mock
    private FaturaRepository faturaRepository;
    @Mock
    private TransacaoRepository transacaoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CartaoCreditoService cartaoCreditoService;

    @Test
    void encontraInativoPorBancoItau() {
        CartaoCredito inativo = cartao(99L, "itau", "Vinicius Falcao de Souza", "1234", false);
        when(cartaoCreditoRepository.findByUsuarioId(1L)).thenReturn(List.of(inativo));

        List<CartaoCredito> found = cartaoCreditoService.encontrarInativosPorReferencia(1L, "itau");

        assertEquals(1, found.size());
        assertEquals(99L, found.get(0).getId());
    }

    @Test
    void reativaCartaoInativo() {
        CartaoCredito inativo = cartao(5L, "itau", "Meu Itau", "5678", false);
        when(cartaoCreditoRepository.findByIdAndUsuarioId(5L, 1L)).thenReturn(Optional.of(inativo));
        when(cartaoCreditoRepository.save(any(CartaoCredito.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = cartaoCreditoService.reativarCartao(5L, 1L);

        assertTrue(dto.getAtivo());
        verify(cartaoCreditoRepository).save(inativo);
        assertTrue(inativo.getAtivo());
    }

    private static CartaoCredito cartao(Long id, String banco, String nome, String numero, boolean ativo) {
        CartaoCredito c = new CartaoCredito();
        c.setId(id);
        c.setBanco(banco);
        c.setNome(nome);
        c.setNumeroCartao(numero);
        c.setAtivo(ativo);
        c.setLimiteCredito(BigDecimal.valueOf(1750));
        c.setLimiteDisponivel(BigDecimal.valueOf(1750));
        c.setDiaVencimento(10);
        Usuario u = new Usuario();
        u.setId(1L);
        c.setUsuario(u);
        return c;
    }
}
