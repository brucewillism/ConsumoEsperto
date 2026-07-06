package com.consumoesperto.regressao;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.MovimentacaoSaldoLog;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MovimentacaoSaldoLogRepository;
import com.consumoesperto.service.SaldoMovimentacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressão ST-04: ajuste manual de saldo gera linha no ledger com tipo AJUSTE_MANUAL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AjusteManualLedgerRegressionTest {

    @Mock private ContaBancariaRepository contaBancariaRepository;
    @Mock private MovimentacaoSaldoLogRepository movimentacaoSaldoLogRepository;

    @InjectMocks private SaldoMovimentacaoService saldoMovimentacaoService;

    private ContaBancaria conta;

    @BeforeEach
    void setUp() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        conta = new ContaBancaria();
        conta.setId(5L);
        conta.setUsuario(usuario);
        conta.setSaldoAtual(new BigDecimal("1000.00"));
        when(contaBancariaRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(conta));
        when(contaBancariaRepository.save(any(ContaBancaria.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void ajustarSaldoManual_gravaLedgerComTipoAjusteManual() {
        saldoMovimentacaoService.ajustarSaldoManual(5L, new BigDecimal("1500.00"));

        ArgumentCaptor<MovimentacaoSaldoLog> captor = ArgumentCaptor.forClass(MovimentacaoSaldoLog.class);
        verify(movimentacaoSaldoLogRepository).save(captor.capture());
        MovimentacaoSaldoLog log = captor.getValue();

        assertEquals(MovimentacaoSaldoLog.TipoOperacaoSaldo.AJUSTE_MANUAL, log.getTipoOperacao());
        assertEquals(0, log.getDelta().compareTo(new BigDecimal("500.00")));
        assertEquals(0, log.getSaldoAntes().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, log.getSaldoDepois().compareTo(new BigDecimal("1500.00")));
    }
}
