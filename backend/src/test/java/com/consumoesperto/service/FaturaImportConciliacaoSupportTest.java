package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaturaImportConciliacaoSupportTest {

    @Test
    void naoRoubaParcelaDePrevistaFutura() {
        Fatura alvo = fatura(1L, Fatura.StatusFatura.ABERTA, YearMonth.of(2026, 7));
        Fatura previstaJulho = fatura(2L, Fatura.StatusFatura.PREVISTA, YearMonth.of(2026, 8));
        Transacao tx = txNaFatura(previstaJulho, 4, 10);

        ImportacaoFaturaItemDTO item = item(3, 10);
        assertFalse(FaturaImportConciliacaoSupport.deveVincularTransacaoExistenteNaFatura(tx, alvo, item));
    }

    @Test
    void naoRoubaDeFaturaPaga() {
        Fatura alvo = fatura(1L, Fatura.StatusFatura.ABERTA, YearMonth.of(2026, 7));
        Fatura paga = fatura(3L, Fatura.StatusFatura.PAGA, YearMonth.of(2026, 6));
        Transacao tx = txNaFatura(paga, 3, 10);

        assertFalse(FaturaImportConciliacaoSupport.deveVincularTransacaoExistenteNaFatura(
            tx, alvo, item(3, 10)));
    }

    @Test
    void permiteOrfaSemFatura() {
        Fatura alvo = fatura(1L, Fatura.StatusFatura.ABERTA, YearMonth.of(2026, 7));
        Transacao tx = new Transacao();
        tx.setFatura(null);

        assertTrue(FaturaImportConciliacaoSupport.deveVincularTransacaoExistenteNaFatura(tx, alvo, item(3, 10)));
    }

    @Test
    void naoMisturaParcelaDiferenteNaMesmaPrevista() {
        Fatura alvo = fatura(1L, Fatura.StatusFatura.ABERTA, YearMonth.of(2026, 7));
        Fatura prevista = fatura(2L, Fatura.StatusFatura.PREVISTA, YearMonth.of(2026, 7));
        Transacao tx = txNaFatura(prevista, 4, 5);

        assertFalse(FaturaImportConciliacaoSupport.deveVincularTransacaoExistenteNaFatura(
            tx, alvo, item(3, 10)));
    }

    private static Fatura fatura(Long id, Fatura.StatusFatura status, YearMonth ym) {
        Fatura f = new Fatura();
        f.setId(id);
        f.setStatusFatura(status);
        f.setDataVencimento(ym.atEndOfMonth().atTime(12, 0));
        CartaoCredito c = new CartaoCredito();
        c.setId(99L);
        f.setCartaoCredito(c);
        return f;
    }

    private static Transacao txNaFatura(Fatura fatura, int parcela, int total) {
        Transacao tx = new Transacao();
        tx.setFatura(fatura);
        tx.setParcelaAtual(parcela);
        tx.setTotalParcelas(total);
        tx.setValor(new BigDecimal("555.81"));
        tx.setDataTransacao(LocalDateTime.of(2026, 7, 2, 12, 0));
        return tx;
    }

    private static ImportacaoFaturaItemDTO item(int parcela, int total) {
        ImportacaoFaturaItemDTO dto = new ImportacaoFaturaItemDTO();
        dto.setParcelaAtual(parcela);
        dto.setTotalParcelas(total);
        dto.setValor(new BigDecimal("555.81"));
        dto.setDescricao("PIX PATRICIA B");
        return dto;
    }
}
