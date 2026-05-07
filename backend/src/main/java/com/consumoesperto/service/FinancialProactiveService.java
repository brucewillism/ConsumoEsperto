package com.consumoesperto.service;

import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialProactiveService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final CategoriaRepository categoriaRepository;
    private final OrcamentoService orcamentoService;
    private final ForecastFinanceiroService forecastFinanceiroService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final MetaFinanceiraRepository metaFinanceiraRepository;
    private final OpenAiService openAiService;
    private final ComportamentoService comportamentoService;

    @Transactional(readOnly = true)
    public Optional<Categoria> sugerirCategoria(Long usuarioId, String descricao) {
        List<Categoria> categorias = categoriaRepository.findByUsuarioIdOrderByNome(usuarioId);
        if (categorias.isEmpty() || descricao == null || descricao.isBlank()) {
            return Optional.empty();
        }
        Optional<Categoria> heuristica = sugerirCategoriaHeuristica(categorias, descricao);
        if (heuristica.isPresent()) {
            return heuristica;
        }
        try {
            String nomes = categorias.stream().map(Categoria::getNome).collect(Collectors.joining(", "));
            JsonNode json = openAiService.gerarJson(
                usuarioId,
                "Classifique uma despesa em uma das categorias fornecidas. Retorne JSON {\"categoria\":\"nome exato\",\"confianca\":0-1}.",
                "Descrição da despesa: " + descricao + "\nCategorias disponíveis: " + nomes
            );
            String nome = json.path("categoria").asText("").trim();
            double confianca = json.path("confianca").asDouble(0);
            if (confianca >= 0.55d && !nome.isBlank()) {
                return categorias.stream()
                    .filter(c -> c.getNome() != null && c.getNome().equalsIgnoreCase(nome))
                    .findFirst();
            }
        } catch (Exception e) {
            log.debug("Autoclassificação por IA indisponível: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public void aposDespesaSalva(Transacao transacao) {
        try {
            orcamentoService.verificarAposDespesa(transacao);
        } catch (Exception e) {
            log.warn("Falha ao verificar orçamento após despesa {}: {}", id(transacao), e.getMessage());
        }
        try {
            avisarRiscoVermelho(transacao);
        } catch (Exception e) {
            log.warn("Falha ao verificar forecast após despesa {}: {}", id(transacao), e.getMessage());
        }
        try {
            auditarJuros(transacao);
        } catch (Exception e) {
            log.warn("Falha ao auditar juros após despesa {}: {}", id(transacao), e.getMessage());
        }
        try {
            comportamentoService.avaliarAposDespesa(transacao);
        } catch (Exception e) {
            log.warn("Falha ao avaliar comportamento após despesa {}: {}", id(transacao), e.getMessage());
        }
    }

    private void avisarRiscoVermelho(Transacao transacao) {
        if (!isDespesaConfirmada(transacao) || transacao.getUsuario() == null) {
            return;
        }
        var forecast = forecastFinanceiroService.calcular(transacao.getUsuario().getId());
        boolean vermelho = forecast.getSaldoProjetado().compareTo(BigDecimal.ZERO) < 0
            || forecast.getProbabilidadeVermelho().compareTo(BigDecimal.valueOf(70)) >= 0;
        if (!vermelho) {
            return;
        }
        String msg = "*Alerta financeiro proativo*\n"
            + "Depois desse lançamento, a projeção indica risco de fechar o mês no vermelho.\n"
            + "Saldo projetado: *" + BRL.format(forecast.getSaldoProjetado()) + "*\n"
            + "Probabilidade estimada: *" + forecast.getProbabilidadeVermelho() + "%*\n\n"
            + forecast.getMensagemIa();
        whatsAppNotificationService.enviarParaUsuario(transacao.getUsuario().getId(), msg);
    }

    private void auditarJuros(Transacao transacao) {
        if (!isDespesaConfirmada(transacao) || transacao.getUsuario() == null) {
            return;
        }
        BigDecimal valorReal = transacao.getValorReal();
        BigDecimal valorComJuros = transacao.getValorComJuros();
        if (valorComJuros == null && transacao.getTotalParcelas() != null && transacao.getTotalParcelas() > 1 && transacao.getValor() != null) {
            valorComJuros = transacao.getValor().multiply(BigDecimal.valueOf(transacao.getTotalParcelas()));
        }
        if (valorReal == null || valorComJuros == null || valorComJuros.compareTo(valorReal) <= 0) {
            return;
        }
        BigDecimal juros = valorComJuros.subtract(valorReal).setScale(2, RoundingMode.HALF_UP);
        String meta = metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(transacao.getUsuario().getId())
            .stream()
            .max(Comparator.comparing(MetaFinanceira::getPrioridade, Comparator.nullsFirst(Integer::compareTo)))
            .map(MetaFinanceira::getDescricao)
            .orElse("uma meta financeira");
        String msg = "*Auditoria de juros*\n"
            + "Você pagará aproximadamente *" + BRL.format(juros) + "* de juros em *" + transacao.getDescricao() + "*.\n"
            + "Se pagasse à vista, essa economia poderia ir para *" + meta + "*.";
        whatsAppNotificationService.enviarParaUsuario(transacao.getUsuario().getId(), msg);
    }

    private Optional<Categoria> sugerirCategoriaHeuristica(List<Categoria> categorias, String descricao) {
        String d = normalizar(descricao);
        for (Categoria c : categorias) {
            String nome = normalizar(c.getNome());
            if (!nome.isBlank() && (d.contains(nome) || nome.contains(d))) {
                return Optional.of(c);
            }
        }
        return categorias.stream().filter(c -> {
            String n = normalizar(c.getNome());
            return (d.matches(".*(mercado|supermerc|padaria|restaurante|ifood|pizza).*") && n.matches(".*(aliment|mercado|comida).*"))
                || (d.matches(".*(uber|99|combust|gasolina|onibus|ônibus|metro|metrô).*") && n.matches(".*(transporte|combust).*"))
                || (d.matches(".*(netflix|spotify|amazon|assinatura|academia).*") && n.matches(".*(assinatura|lazer|servico|serviço).*"))
                || (d.matches(".*(aluguel|condominio|condomínio|energia|luz|agua|água|internet).*") && n.matches(".*(moradia|casa|conta).*"));
        }).findFirst();
    }

    private static boolean isDespesaConfirmada(Transacao t) {
        return t != null
            && t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA
            && t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA;
    }

    private static String normalizar(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    private static Long id(Transacao t) {
        return t != null ? t.getId() : null;
    }
}
