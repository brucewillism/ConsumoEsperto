package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DespesaFixaWhatsappTextParserTest {

    @Test
    void cadastreNovaDespesaFixaValorENome() {
        var p = DespesaFixaWhatsappTextParser.parse(
            "cadastre uma nova despesa fixa com o valor de 220 com o nome de condominio");
        assertNotNull(p);
        assertEquals(new BigDecimal("220.00"), p.valor());
        assertEquals("condominio", p.descricao());
        assertNull(p.diaVencimento());
    }

    @Test
    void salveDespesaFixaDeValorParaDescricao() {
        var p = DespesaFixaWhatsappTextParser.parse("salve essa despesa fixa de 250 para internet dia 10");
        assertNotNull(p);
        assertEquals(new BigDecimal("250.00"), p.valor());
        assertEquals("internet", p.descricao());
        assertEquals(10, p.diaVencimento());
    }

    @Test
    void listaSeparadaPorVirgula() {
        var p = DespesaFixaWhatsappTextParser.parse("cadastrar despesa fixa: aluguel, 1500, dia 5");
        assertNotNull(p);
        assertEquals(new BigDecimal("1500.00"), p.valor());
        assertEquals("aluguel", p.descricao());
        assertEquals(5, p.diaVencimento());
    }

    @Test
    void cadastreImperativoCurto() {
        var p = DespesaFixaWhatsappTextParser.parse("cadastre despesa fixa condominio, 220");
        assertNotNull(p);
        assertEquals(new BigDecimal("220.00"), p.valor());
        assertEquals("condominio", p.descricao());
    }

    @Test
    void contaDeLuzVenceDiaSemValor() {
        var p = DespesaFixaWhatsappTextParser.parse("conta de luz vence dia 15");
        assertNotNull(p);
        assertEquals("conta de luz", p.descricao());
        assertEquals(15, p.diaVencimento());
        assertNull(p.valor());
    }

    @Test
    void lembreteComValorEVencimento() {
        var p = DespesaFixaWhatsappTextParser.parse("lembrete internet de 250 vence dia 10");
        assertNotNull(p);
        assertEquals(new BigDecimal("250.00"), p.valor());
        assertEquals("internet", p.descricao());
        assertEquals(10, p.diaVencimento());
    }

    @Test
    void venceDiaComValorNoFinal() {
        var p = DespesaFixaWhatsappTextParser.parse("conta de agua vence dia 8 de 95");
        assertNotNull(p);
        assertEquals(new BigDecimal("95.00"), p.valor());
        assertEquals("conta de agua", p.descricao());
        assertEquals(8, p.diaVencimento());
    }
}
