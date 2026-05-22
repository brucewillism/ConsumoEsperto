package com.consumoesperto.service;

import com.consumoesperto.dto.PagamentoFaturaRequest;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Conciliação de fatura: débito consolidado em conta sem duplicar despesas individuais do cartão.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaturaConciliacaoService {

    private static final String CATEGORIA_PAGAMENTO_FATURA = "Pagamento de Fatura";

    private final FaturaRepository faturaRepository;
    private final ContaBancariaService contaBancariaService;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final SaldoMovimentacaoService saldoMovimentacaoService;
    private final SaldoService saldoService;

    @Transactional
    public TransacaoDTO pagarFatura(Long usuarioId, PagamentoFaturaRequest request) {
        Fatura fatura = faturaRepository.findByIdAndCartaoCreditoUsuarioId(request.getFaturaId(), usuarioId)
            .orElseThrow(() -> new RuntimeException("Fatura não encontrada"));
        if (Boolean.TRUE.equals(fatura.getPaga()) || fatura.getStatus() == Fatura.StatusFatura.PAGA) {
            throw new IllegalStateException("Fatura já está paga.");
        }

        ContaBancaria conta = contaBancariaService.buscarEntidade(request.getContaBancariaId(), usuarioId);
        BigDecimal valorFatura = fatura.getValorFatura() != null ? fatura.getValorFatura() : fatura.getValorTotal();
        BigDecimal valor = request.getValor() != null ? request.getValor() : valorFatura;
        valor = valor.setScale(2, RoundingMode.HALF_UP);
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor do pagamento deve ser positivo.");
        }
        if (conta.getSaldoAtual().compareTo(valor) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente na conta selecionada.");
        }

        String cartaoNome = fatura.getCartaoCredito() != null ? fatura.getCartaoCredito().getNome() : "Cartão";
        Transacao pagamento = new Transacao();
        pagamento.setDescricao("Pagamento fatura " + cartaoNome + " — " + fatura.getNumeroFatura());
        pagamento.setValor(valor);
        pagamento.setTipoTransacao(Transacao.TipoTransacao.PAGAMENTO_FATURA);
        pagamento.setStatusConferencia(Transacao.StatusConferencia.CONFIRMADA);
        pagamento.setContaBancaria(conta);
        pagamento.setFatura(fatura);
        pagamento.setDataTransacao(request.getDataPagamento() != null
            ? request.getDataPagamento() : LocalDateTime.now());
        pagamento.setExcluido(false);
        pagamento.setRecorrente(false);
        Usuario u = new Usuario();
        u.setId(usuarioId);
        pagamento.setUsuario(u);
        pagamento.setCategoria(resolverCategoriaPagamento(usuarioId));

        Transacao salva = transacaoRepository.save(pagamento);
        saldoMovimentacaoService.aplicarCriacao(salva);

        LocalDateTime dataPag = pagamento.getDataTransacao();
        fatura.setPaga(true);
        fatura.setStatus(Fatura.StatusFatura.PAGA);
        fatura.setDataPagamento(dataPag);
        fatura.setValorPago(valor);
        faturaRepository.save(fatura);

        saldoService.notificarAlteracaoSaldo(usuarioId);
        log.info("[FATURA] Conciliação userId={} faturaId={} valor={} conta={}",
            usuarioId, fatura.getId(), valor, conta.getId());

        return toDto(salva, conta.getNome());
    }

    private Categoria resolverCategoriaPagamento(Long usuarioId) {
        Categoria existente = categoriaRepository.findByUsuarioIdAndNome(usuarioId, CATEGORIA_PAGAMENTO_FATURA);
        if (existente != null) {
            return existente;
        }
        Usuario u = new Usuario();
        u.setId(usuarioId);
        Categoria c = new Categoria();
        c.setUsuario(u);
        c.setNome(CATEGORIA_PAGAMENTO_FATURA);
        c.setDescricao("Liquidação consolidada de fatura de cartão");
        c.setCor("#6366f1");
        c.setIcone("credit-card");
        return categoriaRepository.save(c);
    }

    private static TransacaoDTO toDto(Transacao t, String contaNome) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setId(t.getId());
        dto.setDescricao(t.getDescricao());
        dto.setValor(t.getValor());
        dto.setTipoTransacao(TransacaoDTO.TipoTransacao.PAGAMENTO_FATURA);
        dto.setContaBancariaId(t.getContaBancaria() != null ? t.getContaBancaria().getId() : null);
        dto.setContaBancariaNome(contaNome);
        dto.setFaturaId(t.getFatura() != null ? t.getFatura().getId() : null);
        dto.setDataTransacao(t.getDataTransacao());
        dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        return dto;
    }
}
