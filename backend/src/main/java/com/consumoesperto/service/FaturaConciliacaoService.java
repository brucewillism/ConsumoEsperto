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
    /** Tolerância para arredondamento importação PDF vs soma de lançamentos. */
    private static final BigDecimal TOLERANCIA_QUITACAO = new BigDecimal("0.05");

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
        reconciliarStatusPagamento(fatura);
        if (Boolean.TRUE.equals(fatura.getPaga()) || fatura.getStatus() == Fatura.StatusFatura.PAGA) {
            throw new IllegalStateException("Fatura já está paga.");
        }

        ContaBancaria conta = contaBancariaService.buscarEntidade(request.getContaBancariaId(), usuarioId);
        BigDecimal valorDevido = resolverValorDevido(fatura);
        BigDecimal valorFatura = valorDevido;
        BigDecimal valor = request.getValor() != null ? request.getValor() : valorFatura;
        valor = valor.setScale(2, RoundingMode.HALF_UP);
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor do pagamento deve ser positivo.");
        }
        if (!conta.temSaldoSuficiente(valor)) {
            throw new IllegalArgumentException(
                "Saldo insuficiente na conta selecionada. Disponível (incluindo cheque especial): R$ "
                    + conta.getSaldoDisponivel().setScale(2, RoundingMode.HALF_UP));
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
        fatura.setDataPagamento(dataPag);
        BigDecimal totalPago = somaPagamentosRegistrados(fatura);
        fatura.setValorPago(totalPago);
        boolean quitacaoTotal = quitacaoCompleta(totalPago, valorDevido);
        if (quitacaoTotal) {
            fatura.setPaga(true);
            fatura.setStatus(Fatura.StatusFatura.PAGA);
        } else {
            fatura.setPaga(false);
            fatura.setStatus(Fatura.StatusFatura.PARCIAL);
        }
        faturaRepository.save(fatura);

        saldoService.notificarAlteracaoSaldo(usuarioId);
        log.info("[FATURA] Conciliação userId={} faturaId={} valor={} totalPago={} devido={} status={}",
            usuarioId, fatura.getId(), valor, totalPago, valorDevido, fatura.getStatus());

        return toDto(salva, conta.getNome());
    }

    /**
     * Corrige faturas com pagamento registrado (PAGAMENTO_FATURA) mas status ainda aberto/parcial.
     */
    @Transactional
    public boolean reconciliarStatusPagamento(Fatura fatura) {
        if (fatura == null || fatura.getId() == null) {
            return false;
        }
        if (fatura.getStatus() == Fatura.StatusFatura.PAGA || Boolean.TRUE.equals(fatura.getPaga())) {
            return false;
        }
        BigDecimal devido = resolverValorDevido(fatura);
        if (devido.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal totalPago = somaPagamentosRegistrados(fatura);
        if (!quitacaoCompleta(totalPago, devido)) {
            if (totalPago.compareTo(BigDecimal.ZERO) > 0
                && fatura.getStatus() != Fatura.StatusFatura.PARCIAL) {
                fatura.setValorPago(totalPago);
                fatura.setPaga(false);
                fatura.setStatus(Fatura.StatusFatura.PARCIAL);
                faturaRepository.save(fatura);
                log.info("[FATURA] Reconciliação parcial faturaId={} pago={} devido={}",
                    fatura.getId(), totalPago, devido);
                return true;
            }
            return false;
        }
        fatura.setValorPago(totalPago);
        fatura.setPaga(true);
        fatura.setStatus(Fatura.StatusFatura.PAGA);
        if (fatura.getDataPagamento() == null) {
            fatura.setDataPagamento(LocalDateTime.now());
        }
        faturaRepository.save(fatura);
        log.info("[FATURA] Reconciliação quitada faturaId={} pago={} devido={}",
            fatura.getId(), totalPago, devido);
        return true;
    }

    BigDecimal resolverValorDevido(Fatura fatura) {
        BigDecimal vf = fatura.getValorFatura();
        BigDecimal vt = fatura.getValorTotal();
        BigDecimal base = BigDecimal.ZERO;
        if (vf != null && vf.compareTo(BigDecimal.ZERO) > 0) {
            base = vf;
        } else if (vt != null && vt.compareTo(BigDecimal.ZERO) > 0) {
            base = vt;
        }
        if (fatura.getId() != null) {
            BigDecimal somaDespesas = transacaoRepository.sumDespesaConfirmadaPorFaturaId(fatura.getId());
            if (somaDespesas != null && somaDespesas.compareTo(BigDecimal.ZERO) > 0) {
                if (base.compareTo(BigDecimal.ZERO) <= 0 || somaDespesas.compareTo(base) > 0) {
                    base = somaDespesas;
                }
            }
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal somaPagamentosRegistrados(Fatura fatura) {
        if (fatura.getId() == null) {
            return nz(fatura.getValorPago());
        }
        BigDecimal viaTransacoes = transacaoRepository.sumPagamentoFaturaConfirmadoPorFaturaId(fatura.getId());
        BigDecimal viaCampo = nz(fatura.getValorPago());
        return viaTransacoes.max(viaCampo).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean quitacaoCompleta(BigDecimal totalPago, BigDecimal valorDevido) {
        if (totalPago == null || valorDevido == null) {
            return false;
        }
        return totalPago.add(TOLERANCIA_QUITACAO).compareTo(valorDevido) >= 0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
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
