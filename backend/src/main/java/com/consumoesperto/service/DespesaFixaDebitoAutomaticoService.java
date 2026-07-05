package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.DespesaFixa;
import com.consumoesperto.model.MovimentacaoSaldoLog.OrigemMovimentacaoSaldo;
import com.consumoesperto.repository.DespesaFixaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

/**
 * Job diário: lança automaticamente as despesas fixas com débito automático ligado,
 * debitando a conta vinculada (ou a conta padrão) no dia efetivo do vencimento.
 *
 * <p>Idempotente pela chave natural (usuário, descrição canônica, data do vencimento, valor):
 * reprocessar o dia não debita duas vezes.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DespesaFixaDebitoAutomaticoService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    static final String PREFIXO_DESCRICAO = "Despesa fixa: ";

    private final DespesaFixaRepository despesaFixaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;
    private final ContaBancariaService contaBancariaService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    /** Roda após o job de agendamentos (06:00) para não concorrer pelo mesmo saldo no mesmo minuto. */
    @Scheduled(cron = "0 30 6 * * *", zone = "America/Sao_Paulo")
    public void debitarDespesasFixasDoDia() {
        processarDia(AppTimeZone.hoje());
    }

    /** Processa um dia específico (exposto para reprocessamento manual/testes). */
    public void processarDia(LocalDate dia) {
        List<DespesaFixa> vencendoHoje = despesaFixaRepository.findAllComDebitoAutomatico().stream()
            .filter(d -> venceNoDia(d, dia))
            .toList();
        if (vencendoHoje.isEmpty()) {
            return;
        }
        log.info("[DESPESA_FIXA_DEBITO] Processando {} despesa(s) fixa(s) com vencimento {}", vencendoHoje.size(), dia);
        SaldoMovimentacaoContexto.definirOrigem(OrigemMovimentacaoSaldo.JOB);
        try {
            for (DespesaFixa d : vencendoHoje) {
                try {
                    debitarUma(d, dia);
                } catch (Exception e) {
                    log.warn("[DESPESA_FIXA_DEBITO] Falha id={} ({}): {}", d.getId(), d.getDescricao(), e.getMessage());
                }
            }
        } finally {
            SaldoMovimentacaoContexto.limpar();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void debitarUma(DespesaFixa d, LocalDate dia) {
        Long usuarioId = d.getUsuario().getId();
        BigDecimal valor = nz(d.getValor()).setScale(2, RoundingMode.HALF_UP);
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String descricaoTx = PREFIXO_DESCRICAO + d.getDescricao();
        LocalDateTime dataTx = dia.atStartOfDay();
        // Mesma data usada na criação abaixo — se divergir, o reprocessamento debita 2x.
        boolean jaDebitado = !transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            usuarioId, descricaoTx, dataTx, valor
        ).isEmpty();
        if (jaDebitado) {
            log.info("[DESPESA_FIXA_DEBITO] Idempotente: já lançado despesaFixaId={} userId={} dia={}",
                d.getId(), usuarioId, dia);
            return;
        }

        ContaBancaria conta = d.getContaBancaria() != null
            ? contaBancariaService.buscarEntidade(d.getContaBancaria().getId(), usuarioId)
            : contaBancariaService.resolverContaParaTransacao(usuarioId, null);
        if (conta == null) {
            log.warn("[DESPESA_FIXA_DEBITO] Sem conta para debitar despesaFixaId={} userId={}", d.getId(), usuarioId);
            return;
        }
        if (!conta.isAtiva()) {
            log.warn("[DESPESA_FIXA_DEBITO] Conta {} inativa — despesaFixaId={} não debitada", conta.getId(), d.getId());
            notificarFalha(d, conta, "a conta vinculada está inativa");
            return;
        }
        if (!conta.temSaldoSuficiente(valor)) {
            log.warn("[DESPESA_FIXA_DEBITO] Saldo insuficiente conta={} despesaFixaId={} valor={}",
                conta.getId(), d.getId(), valor);
            notificarFalha(d, conta, "saldo insuficiente (disponível: " + BRL.format(conta.getSaldoDisponivel()) + ")");
            return;
        }

        TransacaoDTO tx = new TransacaoDTO();
        tx.setDescricao(descricaoTx);
        tx.setValor(valor);
        tx.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
        tx.setDataTransacao(dataTx);
        tx.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        tx.setContaBancariaId(conta.getId());
        transacaoService.criarTransacao(tx, usuarioId, false);

        log.info("[DESPESA_FIXA_DEBITO] Debitado despesaFixaId={} userId={} conta={} valor={}",
            d.getId(), usuarioId, conta.getId(), valor);
        notificarSucesso(d, conta, valor);
    }

    private static boolean venceNoDia(DespesaFixa d, LocalDate dia) {
        if (d.getDiaVencimento() == null) {
            return false;
        }
        int efetivo = VencimentoMensalUtil.diaEfetivoNoMes(d.getDiaVencimento(), YearMonth.from(dia).lengthOfMonth());
        return efetivo == dia.getDayOfMonth();
    }

    private void notificarSucesso(DespesaFixa d, ContaBancaria conta, BigDecimal valor) {
        try {
            String msg = "Débito automático executado: *" + BRL.format(valor) + "* de *" + d.getDescricao()
                + "* saiu da conta *" + conta.getNome() + "* hoje. Saldo disponível: *"
                + BRL.format(conta.getSaldoDisponivel()) + "*.";
            whatsAppNotificationService.enviarParaUsuario(d.getUsuario().getId(), msg);
        } catch (Exception e) {
            log.debug("[DESPESA_FIXA_DEBITO] Falha ao notificar sucesso: {}", e.getMessage());
        }
    }

    private void notificarFalha(DespesaFixa d, ContaBancaria conta, String motivo) {
        try {
            String msg = "Não consegui debitar *" + BRL.format(nz(d.getValor())) + "* de *" + d.getDescricao()
                + "* na conta *" + conta.getNome() + "* hoje: " + motivo
                + ". Regularize e lance manualmente, ou ajuste a conta vinculada no Perfil → Obrigações Fixas.";
            whatsAppNotificationService.enviarParaUsuario(d.getUsuario().getId(), msg);
        } catch (Exception e) {
            log.debug("[DESPESA_FIXA_DEBITO] Falha ao notificar falha: {}", e.getMessage());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
