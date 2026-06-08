package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simula o texto final da confirmação de despesa "gastei 45,90 no mercado" reproduzindo a montagem do
 * {@code WhatsAppCommandService.handleExpense} (ramo sem cartão) + cabeçalho {@code msgOk}, para validar
 * vocativo, tom em 2ª pessoa e encaixe do bloco de orçamento sem quebras.
 */
class JarvisConfirmacaoDespesaSimulacaoTest {

    private final JarvisProtocolService jarvis = new JarvisProtocolService();

    /** Reproduz exatamente a concatenação do handleExpense (ramo "sem cartão associado") + msgOk. */
    private String montarMensagemFinal(String vocativo, String descricao, String valorFmt, String orcamentoLinha) {
        String jarvisLinha = jarvis.formatExpenseCatalogued(vocativo, valorFmt);
        String invoiceMessage = ""; // sem cartão => vincularNaFatura devolve vazio
        String corpo = jarvisLinha + "\n\n*" + descricao + "* (sem cartão associado).\n"
            + invoiceMessage.trim() + orcamentoLinha;
        return "\u2705 *Despesa registada*\n" + corpo;
    }

    @Test
    void usuarioComOrcamentoCritico84() {
        Usuario u = new Usuario();
        u.setNome("Ana Paula");
        u.setJarvisConfigurado(true);
        u.setTratamento("chefe");
        String voc = jarvis.montarVocativoCompleto(u);

        String orcamento = jarvis.linhaOrcamentoPosDespesa(
            "Mercado", new BigDecimal("252.00"), new BigDecimal("300.00"), new BigDecimal("84.00"));
        String mensagem = montarMensagemFinal(voc, "Mercado", "R$ 45,90", orcamento);

        System.out.println("\n===== CASO A: orçamento crítico (84%) =====\nvocativo=" + voc + "\n" + mensagem + "\n==========================================\n");

        assertFalse(mensagem.contains("{{"), "Não pode sobrar placeholder literal");
        assertFalse(mensagem.contains("null"), "Não pode aparecer 'null' no texto");
        assertTrue(mensagem.contains("Feito, chefe."), "Vocativo configurado deve aparecer");
        assertTrue(mensagem.contains("Já lancei *R$ 45,90* para você."), "Tom em 2ª pessoa na confirmação");
        assertTrue(mensagem.contains("*Mercado* já está em 84% do orçamento mensal"), "Bloco de orçamento crítico");
        assertTrue(mensagem.contains("perto do limite"), "Aviso de proximidade do teto");
        assertFalse(mensagem.contains("estourou"), "84% não é estouro");
    }

    @Test
    void usuarioSemOrcamentoDefinido() {
        Usuario u = new Usuario();
        u.setNome("João");
        u.setGenero(Usuario.GeneroUsuario.MALE);
        String voc = jarvis.montarVocativoCompleto(u);

        String orcamento = jarvis.linhaSemOrcamentoPosDespesa("Mercado");
        String mensagem = montarMensagemFinal(voc, "Mercado", "R$ 45,90", orcamento);

        System.out.println("\n===== CASO B: sem orçamento definido =====\nvocativo=" + voc + "\n" + mensagem + "\n==========================================\n");

        assertFalse(mensagem.contains("{{"), "Não pode sobrar placeholder literal");
        assertFalse(mensagem.contains("null"), "Não pode aparecer 'null' no texto");
        assertTrue(mensagem.contains("Já lancei *R$ 45,90* para você."), "Tom em 2ª pessoa na confirmação");
        assertTrue(mensagem.contains("Você ainda não tem um orçamento definido para *Mercado*"), "Sugestão de criar orçamento");
        assertTrue(mensagem.contains("Quer que eu crie um agora"), "Proatividade ao sugerir o teto");
    }
}
