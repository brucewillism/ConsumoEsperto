package com.consumoesperto.regressao;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.RecurringExpenseDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regressão T1: transação PREVISTO não entra na detecção de recorrência (só CONFIRMADA).
 */
@ExtendWith(MockitoExtension.class)
class PrevistoForaMediaHistoricaRegressionTest {

    @Mock private TransacaoRepository transacaoRepository;
    @InjectMocks private RecurringExpenseDetectionService service;

    @Test
    void detectar_ignoraDespesaPrevista() {
        Transacao prevista = new Transacao();
        prevista.setDescricao("NETFLIX");
        prevista.setValor(new BigDecimal("49.90"));
        prevista.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
        prevista.setStatusConferencia(Transacao.StatusConferencia.PREVISTO);
        prevista.setDataTransacao(LocalDateTime.of(2026, 3, 5, 10, 0));
        prevista.setExcluido(false);

        when(transacaoRepository.findByUsuarioIdAndTipoAndPeriodo(
            eq(1L), eq(Transacao.TipoTransacao.DESPESA), any(), any()))
            .thenReturn(List.of(prevista));

        assertTrue(service.detectar(1L).isEmpty(), "PREVISTO não deve alimentar detecção de recorrência");
    }
}
