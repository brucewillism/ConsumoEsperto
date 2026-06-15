package com.consumoesperto.service;

import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.dto.MetaFinanceiraRequest;
import com.consumoesperto.dto.OrcamentoRequest;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.AssinaturaRecorrente;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.DespesaFixa;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Orcamento;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.OrcamentoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.StringFuzzy;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fluxo WhatsApp: busca com listagem numerada para apagar/editar transações, metas e cartões.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppGestaoProativaService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter DDM = DateTimeFormatter.ofPattern("dd/MM");
    private static final Pattern APENAS_DIGITO = Pattern.compile("^\\s*(\\d{1,2})\\s*$");

    private final TransacaoRepository transacaoRepository;
    private final MetaFinanceiraRepository metaFinanceiraRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final TransacaoService transacaoService;
    private final MetaFinanceiraService metaFinanceiraService;
    private final CartaoCreditoService cartaoCreditoService;
    private final ContaBancariaService contaBancariaService;
    private final CategoriaService categoriaService;
    private final OrcamentoService orcamentoService;
    private final SaldoService saldoService;
    private final DespesaFixaService despesaFixaService;
    private final AssinaturaRecorrenteService assinaturaRecorrenteService;

    private final Map<Long, SessaoGestao> sessoes = new ConcurrentHashMap<>();

    public boolean temSessaoAtiva(Long usuarioId) {
        return sessoes.containsKey(usuarioId);
    }

    public void cancelarSessao(Long usuarioId) {
        sessoes.remove(usuarioId);
    }

    /**
     * Respostas de continuação (número, sim/não, valor, mover/apagar faturas).
     */
    public Optional<String> tentarConsumirResposta(Long usuarioId, String textoBruto) {
        SessaoGestao s = sessoes.get(usuarioId);
        if (s == null) {
            return Optional.empty();
        }
        String t = textoBruto != null ? textoBruto.trim() : "";
        if (t.isEmpty()) {
            return Optional.empty();
        }
        return switch (s.passo) {
            case ESCOLHER_INDICE -> consumirIndice(usuarioId, s, t);
            case CONFIRMAR_DELETE_UNICO -> consumirSimNaoDeleteUnico(usuarioId, s, t);
            case PARCEL_DELETE_OPCAO -> consumirOpcaoParcelDelete(usuarioId, s, t);
            case PEDIR_NOVO_VALOR_TRANSACAO -> consumirNovoValorTransacao(usuarioId, s, t);
            case PEDIR_NOVO_VALOR_META -> consumirNovoValorMeta(usuarioId, s, t);
            case PEDIR_NOVO_LIMITE_CARTAO -> consumirNovoLimiteCartao(usuarioId, s, t);
            case PEDIR_NOVO_NOME_CATEGORIA -> consumirNovoNomeCategoria(usuarioId, s, t);
            case PEDIR_NOVO_NOME_CONTA -> consumirNovoNomeConta(usuarioId, s, t);
            case PEDIR_NOVO_LIMITE_ORCAMENTO -> consumirNovoLimiteOrcamento(usuarioId, s, t);
            case CARTAO_FATURA_DECISAO -> consumirDecisaoFatura(usuarioId, s, t);
            case CARTAO_ESCOLHER_DESTINO -> consumirDestinoFatura(usuarioId, s, t);
        };
    }

    @Transactional
    public String iniciarGestao(JsonNode cmd, Long userId, String sourceText) {
        cancelarSessao(userId);
        String op = cmd.path("manageOperation").asText("").trim().toLowerCase(Locale.ROOT);
        String target = cmd.path("manageTarget").asText("").trim().toLowerCase(Locale.ROOT);
        String phrase = whatsAppFirstNonBlank(
            cmd.path("searchPhrase").asText(""),
            cmd.path("description").asText(""),
            cmd.path("identifier").asText("")
        );
        if (phrase.isBlank() && sourceText != null) {
            phrase = sourceText.trim();
        }
        phrase = phrase.replaceAll("(?i)^(apague|apagar|delete|remover|edite|editar)\\s+", "").trim();
        phrase = phrase.replaceAll("(?i)\\b(desta|neste)\\s+m[eê]s\\b", "").trim();
        if (phrase.isBlank()) {
            return msgErro("Gestão", "Diz o que procurar (ex.: *gasolina*, *meta Lazer*, *cartão Inter*).");
        }
        boolean edit = op.contains("edit");
        boolean del = op.contains("del") || op.contains("apag");
        if (!edit && !del) {
            del = true;
        }

        Transacao.TipoTransacao filtroTipo = Transacao.TipoTransacao.DESPESA;
        String tipoTxt = cmd.path("tipoTransacao").asText("").trim().toUpperCase(Locale.ROOT);
        if ("RECEITA".equals(tipoTxt)) {
            filtroTipo = Transacao.TipoTransacao.RECEITA;
        } else if ("DESPESA".equals(tipoTxt)) {
            filtroTipo = Transacao.TipoTransacao.DESPESA;
        }

        String targetLc = target.toLowerCase(Locale.ROOT);
        boolean explicitMeta = targetLc.contains("meta") && !targetLc.contains("cart");
        boolean explicitCartao = targetLc.contains("cartao") || targetLc.contains("cartão") || targetLc.contains("card");
        boolean explicitContaBancaria = targetLc.contains("conta_bancaria") || targetLc.contains("conta bancaria")
            || targetLc.contains("carteira") || (targetLc.contains("conta") && !targetLc.contains("cart"));
        boolean explicitCategoria = targetLc.contains("categoria");
        boolean explicitOrcamento = targetLc.contains("orcamento") || targetLc.contains("orçamento");
        boolean explicitDespesaFixa = targetLc.contains("despesa_fixa") || targetLc.contains("despesa fixa")
            || targetLc.contains("obrigacao") || targetLc.contains("obrigação")
            || (targetLc.contains("fixa") && !targetLc.contains("categoria"));
        boolean explicitAssinatura = targetLc.contains("assinatura") || targetLc.contains("subscription");
        boolean explicitTransacao = targetLc.contains("transacao") || targetLc.contains("transação")
            || targetLc.contains("lancamento") || targetLc.contains("lançamento")
            || (targetLc.contains("despesa") && !explicitDespesaFixa)
            || targetLc.contains("receita") || targetLc.contains("gasto");

        if (explicitMeta) {
            return iniciarMetas(userId, phrase, edit, del);
        }
        if (explicitAssinatura) {
            return iniciarAssinaturas(userId, phrase, edit, del);
        }
        if (explicitDespesaFixa) {
            return iniciarDespesasFixas(userId, phrase, edit, del);
        }
        if (explicitCartao) {
            return iniciarCartoes(userId, phrase, edit, del);
        }
        if (explicitContaBancaria) {
            return iniciarContasBancarias(userId, phrase, edit, del);
        }
        if (explicitCategoria) {
            return iniciarCategorias(userId, phrase, edit, del);
        }
        if (explicitOrcamento) {
            return iniciarOrcamentos(userId, phrase, edit, del, cmd, sourceText);
        }
        if (explicitTransacao) {
            return iniciarTransacoes(userId, phrase, edit, del, filtroTipo, cmd, sourceText);
        }

        String pl = phrase.toLowerCase(Locale.ROOT);
        if (pl.matches("(?s).*\\bmeta\\b.*")) {
            return iniciarMetas(userId, phrase, edit, del);
        }
        if (pl.contains("cartão") || pl.contains("cartao")) {
            return iniciarCartoes(userId, phrase, edit, del);
        }
        if (pl.contains("orçamento") || pl.contains("orcamento")) {
            return iniciarOrcamentos(userId, phrase, edit, del, cmd, sourceText);
        }
        if (pl.contains("assinatura")) {
            return iniciarAssinaturas(userId, phrase, edit, del);
        }
        if (pl.contains("despesa fixa") || pl.contains("obrigacao fixa") || pl.contains("obrigação fixa")) {
            return iniciarDespesasFixas(userId, phrase, edit, del);
        }
        if (pl.contains("categoria")) {
            return iniciarCategorias(userId, phrase, edit, del);
        }
        return iniciarTransacoes(userId, phrase, edit, del, filtroTipo, cmd, sourceText);
    }

    private String iniciarTransacoes(
        Long userId,
        String phrase,
        boolean edit,
        boolean del,
        Transacao.TipoTransacao filtroTipo,
        JsonNode cmd,
        String sourceText
    ) {
        YearMonth ym = resolveMes(cmd, sourceText);
        LocalDateTime ini = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);
        List<Transacao> found = transacaoRepository.searchByUsuarioDescricaoNoMes(userId, phrase, filtroTipo, ini, fim);
        if (found.isEmpty()) {
            List<Transacao> todasMes = transacaoRepository.findByUsuarioIdAndTipoAndPeriodo(userId, filtroTipo, ini, fim);
            found = todasMes.stream()
                .filter(t -> t.getDescricao() != null && StringFuzzy.descricaoContemTermoFuzzy(t.getDescricao(), phrase))
                .collect(Collectors.toList());
        }
        if (found.isEmpty()) {
            return msgErro("Busca",
                "Não encontrei transações com *" + phrase + "* em *" + labelMes(ym) + "*. Ajusta o termo ou o mês.");
        }
        List<ItemRef> itens = found.stream().map(tr -> new ItemRef(tr.getId(), linhaTransacao(tr))).collect(Collectors.toList());
        SessaoGestao s = new SessaoGestao();
        s.tipoAlvo = TipoAlvo.TRANSACAO;
        s.operacao = edit ? Operacao.EDIT : Operacao.DELETE;
        s.itens = itens;
        s.mesRef = ym;
        if (itens.size() == 1) {
            if (s.operacao == Operacao.DELETE) {
                s.passo = Passo.CONFIRMAR_DELETE_UNICO;
                s.selecionadoId = itens.get(0).id;
                sessoes.put(userId, s);
                return "Encontrei *1* lançamento em *" + labelMes(ym) + "*:\n\n1. " + itens.get(0).linha
                    + "\n\nResponde *sim* para apagar ou *não* para cancelar.";
            }
            s.passo = Passo.PEDIR_NOVO_VALOR_TRANSACAO;
            s.selecionadoId = itens.get(0).id;
            sessoes.put(userId, s);
            return "Encontrei *1* lançamento em *" + labelMes(ym) + "*:\n\n1. " + itens.get(0).linha
                + "\n\nEnvia o *novo valor* (só o número, ex.: *180,50*).";
        }
        s.passo = Passo.ESCOLHER_INDICE;
        sessoes.put(userId, s);
        StringBuilder sb = new StringBuilder();
        sb.append("Encontrei esses itens em *").append(labelMes(ym)).append(":*\n\n");
        for (int i = 0; i < itens.size(); i++) {
            sb.append(i + 1).append(". ").append(itens.get(i).linha).append("\n");
        }
        sb.append("\nQual deles? Responde só com o *número* para ")
            .append(s.operacao == Operacao.DELETE ? "apagar" : "editar").append(".");
        return sb.toString();
    }

    private String iniciarMetas(Long userId, String phrase, boolean edit, boolean del) {
        String frag = phrase.replaceAll("(?i)^meta\\s+", "").trim();
        if (frag.isBlank()) {
            frag = phrase;
        }
        final String termoMeta = frag;
        List<MetaFinanceira> todas = metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(userId);
        List<MetaFinanceira> found = todas.stream()
            .filter(m -> m.getDescricao() != null && StringFuzzy.descricaoContemTermoFuzzy(m.getDescricao(), termoMeta))
            .collect(Collectors.toList());
        if (found.isEmpty()) {
            found = metaFinanceiraRepository.findByUsuarioIdAndDescricaoContainingIgnoreCase(userId, termoMeta);
        }
        if (found.isEmpty()) {
            if (parecePedidoCriarMeta(phrase)) {
                String nome = extrairNomeMetaGestao(phrase);
                if (nome.isBlank()) {
                    nome = termoMeta;
                }
                return msgInfo("Nova meta",
                    "Para *criar* a meta *" + nome + "*, envia o *valor total* e o *percentual da renda* "
                        + "(ex.: *3500 10*), ou repete: *cadastrar meta " + nome + " 3500 com 10% da renda*.");
            }
            return msgErro("Metas", "Não encontrei metas com *" + termoMeta + "*.");
        }
        List<ItemRef> itens = found.stream()
            .map(m -> new ItemRef(m.getId(), m.getDescricao() + " — objetivo " + BRL.format(m.getValorTotal())))
            .collect(Collectors.toList());
        SessaoGestao s = new SessaoGestao();
        s.tipoAlvo = TipoAlvo.META;
        s.operacao = edit ? Operacao.EDIT : Operacao.DELETE;
        s.itens = itens;
        s.mesRef = null;
        if (itens.size() == 1) {
            if (s.operacao == Operacao.DELETE) {
                s.passo = Passo.CONFIRMAR_DELETE_UNICO;
                s.selecionadoId = itens.get(0).id;
                sessoes.put(userId, s);
                return "Encontrei *1* meta:\n\n1. " + itens.get(0).linha + "\n\nResponde *sim* para apagar ou *não* para cancelar.";
            }
            s.passo = Passo.PEDIR_NOVO_VALOR_META;
            s.selecionadoId = itens.get(0).id;
            sessoes.put(userId, s);
            return "Encontrei *1* meta:\n\n1. " + itens.get(0).linha
                + "\n\nEnvia *valor total* e *percentual da renda* separados por espaço (ex.: *8000 12*).";
        }
        s.passo = Passo.ESCOLHER_INDICE;
        sessoes.put(userId, s);
        StringBuilder sb = new StringBuilder();
        sb.append("Encontrei esses itens:\n\n");
        for (int i = 0; i < itens.size(); i++) {
            sb.append(i + 1).append(". ").append(itens.get(i).linha).append("\n");
        }
        sb.append("\nQual deles? Responde só com o *número* para ")
            .append(s.operacao == Operacao.DELETE ? "apagar" : "editar").append(".");
        return sb.toString();
    }

    private String iniciarCartoes(Long userId, String phrase, boolean edit, boolean del) {
        String frag = phrase.replaceAll("(?i)^(meu|minha|o|a)\\s+", "").replaceAll("(?i)\\s+cart[aã]o\\s*", " ").trim();
        List<CartaoCredito> ativos = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(userId);
        List<CartaoCredito> found = ativos.stream()
            .filter(c -> cartaoCorrespondeFraseFuzzy(c, frag))
            .collect(Collectors.toList());
        if (found.isEmpty()) {
            return msgErro("Cartões", "Não encontrei cartão ativo com *" + frag + "*.");
        }
        List<ItemRef> itens = found.stream()
            .map(c -> new ItemRef(c.getId(), (c.getBanco() != null ? c.getBanco() : "") + " · " + c.getNome()
                + " — limite " + BRL.format(c.getLimiteCredito())))
            .collect(Collectors.toList());
        SessaoGestao s = new SessaoGestao();
        s.tipoAlvo = TipoAlvo.CARTAO;
        s.operacao = edit ? Operacao.EDIT : Operacao.DELETE;
        s.itens = itens;
        if (itens.size() == 1) {
            s.selecionadoId = itens.get(0).id;
            if (s.operacao == Operacao.DELETE) {
                long nf = cartaoCreditoService.contarFaturasDoCartao(s.selecionadoId, userId);
                if (nf > 0) {
                    s.passo = Passo.CARTAO_FATURA_DECISAO;
                    sessoes.put(userId, s);
                    return "Encontrei *1* cartão:\n\n1. " + itens.get(0).linha
                        + "\n\nEste cartão tem *" + nf + "* fatura(s). Responde *mover* para passar as faturas a outro cartão "
                        + "ou *apagar faturas* para remover as faturas (irreversível). *não* cancela.";
                }
                cartaoCreditoService.deletarCartaoCredito(s.selecionadoId, userId);
                sessoes.remove(userId);
                return msgOk("Cartão", "Cartão desativado. Sem faturas associadas.");
            }
            s.passo = Passo.PEDIR_NOVO_LIMITE_CARTAO;
            sessoes.put(userId, s);
            return "Encontrei *1* cartão:\n\n1. " + itens.get(0).linha + "\n\nEnvia o *novo limite total* (número).";
        }
        s.passo = Passo.ESCOLHER_INDICE;
        sessoes.put(userId, s);
        StringBuilder sb = new StringBuilder();
        sb.append("Encontrei esses itens:\n\n");
        for (int i = 0; i < itens.size(); i++) {
            sb.append(i + 1).append(". ").append(itens.get(i).linha).append("\n");
        }
        sb.append("\nQual deles? Responde só com o *número*.");
        return sb.toString();
    }

    private String iniciarContasBancarias(Long userId, String phrase, boolean edit, boolean del) {
        String frag = phrase.replaceAll("(?i)^(minha|meu|a|o)\\s+", "")
            .replaceAll("(?i)\\s+conta\\s*", " ").trim();
        List<ContaBancaria> found = contaBancariaService.encontrarAtivasPorApelidoNormalizado(userId, frag);
        if (found.isEmpty()) {
            return msgErro("Contas", "Não encontrei conta ativa com *" + frag + "*.");
        }
        List<ItemRef> itens = found.stream()
            .map(c -> new ItemRef(c.getId(), c.getNome() + " — " + BRL.format(c.getSaldoAtual())))
            .collect(Collectors.toList());
        return montarSessaoSimples(userId, TipoAlvo.CONTA_BANCARIA, edit, del, itens, "conta", "Envia o *novo nome* da conta.");
    }

    private String iniciarCategorias(Long userId, String phrase, boolean edit, boolean del) {
        String frag = phrase.replaceAll("(?i)^categoria\\s+", "").trim();
        List<Categoria> found = categoriaService.encontrarAtivasPorApelidoNormalizado(userId, frag);
        if (found.isEmpty()) {
            return msgErro("Categorias", "Não encontrei categoria com *" + frag + "*.");
        }
        List<ItemRef> itens = found.stream()
            .map(c -> new ItemRef(c.getId(), c.getNome()))
            .collect(Collectors.toList());
        return montarSessaoSimples(userId, TipoAlvo.CATEGORIA, edit, del, itens, "categoria", "Envia o *novo nome* da categoria.");
    }

    private String iniciarOrcamentos(Long userId, String phrase, boolean edit, boolean del, JsonNode cmd, String sourceText) {
        YearMonth ym = resolveMes(cmd, sourceText);
        String frag = phrase.replaceAll("(?i)^(orcamento|orçamento)\\s+(de|para|em)?\\s*", "").trim();
        List<Orcamento> todos = orcamentoRepository.findByUsuarioIdAndMesAndAno(userId, ym.getMonthValue(), ym.getYear());
        List<Orcamento> found = todos.stream()
            .filter(o -> o.getCategoria() != null && o.getCategoria().getNome() != null
                && StringFuzzy.descricaoContemTermoFuzzy(o.getCategoria().getNome(), frag))
            .collect(Collectors.toList());
        if (found.isEmpty() && !frag.isBlank()) {
            found = todos.stream()
                .filter(o -> o.getCategoria() != null && o.getCategoria().getNome() != null
                    && o.getCategoria().getNome().toLowerCase(Locale.ROOT).contains(frag.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (found.isEmpty()) {
            return msgErro("Orçamentos", "Não encontrei orçamento com *" + frag + "* em *" + labelMes(ym) + "*.");
        }
        List<ItemRef> itens = found.stream()
            .map(o -> new ItemRef(o.getId(), o.getCategoria().getNome() + " — limite " + BRL.format(o.getValorLimite())))
            .collect(Collectors.toList());
        SessaoGestao s = new SessaoGestao();
        s.tipoAlvo = TipoAlvo.ORCAMENTO;
        s.operacao = edit ? Operacao.EDIT : Operacao.DELETE;
        s.itens = itens;
        s.mesRef = ym;
        if (itens.size() == 1) {
            s.selecionadoId = itens.get(0).id;
            if (s.operacao == Operacao.DELETE) {
                s.passo = Passo.CONFIRMAR_DELETE_UNICO;
                sessoes.put(userId, s);
                return "Encontrei *1* orçamento em *" + labelMes(ym) + "*:\n\n1. " + itens.get(0).linha
                    + "\n\nResponde *sim* para apagar ou *não* para cancelar.";
            }
            s.passo = Passo.PEDIR_NOVO_LIMITE_ORCAMENTO;
            sessoes.put(userId, s);
            return "Encontrei *1* orçamento:\n\n1. " + itens.get(0).linha + "\n\nEnvia o *novo limite* (número).";
        }
        s.passo = Passo.ESCOLHER_INDICE;
        sessoes.put(userId, s);
        StringBuilder sb = new StringBuilder();
        sb.append("Encontrei esses orçamentos em *").append(labelMes(ym)).append(":*\n\n");
        for (int i = 0; i < itens.size(); i++) {
            sb.append(i + 1).append(". ").append(itens.get(i).linha).append("\n");
        }
        sb.append("\nQual deles? Responde só com o *número*.");
        return sb.toString();
    }

    private String iniciarDespesasFixas(Long userId, String phrase, boolean edit, boolean del) {
        if (edit) {
            return msgInfo("Despesa fixa",
                "Para alterar valor ou vencimento, envie por exemplo: *edita despesa fixa aluguel valor 1200 dia 10*.");
        }
        String frag = phrase.replaceAll("(?i)^(a|o|minha|meu)\\s+", "")
            .replaceAll("(?i)\\s+despesa\\s+fixa\\s*", " ")
            .replaceAll("(?i)^despesa\\s+fixa\\s*", "")
            .trim();
        if (frag.isBlank()) {
            frag = phrase;
        }
        List<DespesaFixa> found = despesaFixaService.encontrarPorIdentificador(userId, frag);
        if (found.isEmpty()) {
            return msgErro("Despesas fixas", "Não encontrei despesa fixa com *" + frag + "*.");
        }
        List<ItemRef> itens = found.stream()
            .map(d -> new ItemRef(d.getId(),
                d.getDescricao() + " — " + BRL.format(d.getValor()) + ", vence dia " + d.getDiaVencimento()))
            .collect(Collectors.toList());
        return montarSessaoDeleteConfirmado(userId, TipoAlvo.DESPESA_FIXA, itens, "despesa fixa");
    }

    private String iniciarAssinaturas(Long userId, String phrase, boolean edit, boolean del) {
        if (edit) {
            return msgInfo("Assinatura",
                "Para pausar ou reativar, envie *desative a assinatura da Netflix* ou *reative o Spotify*.");
        }
        String frag = phrase.replaceAll("(?i)^(a|o|minha|meu)\\s+", "")
            .replaceAll("(?i)\\s+assinatura(?:\\s+(?:da|de|do))?\\s*", " ")
            .replaceAll("(?i)^assinatura\\s*", "")
            .trim();
        if (frag.isBlank()) {
            frag = phrase;
        }
        List<AssinaturaRecorrente> found = assinaturaRecorrenteService.encontrarPorIdentificador(userId, frag);
        if (found.isEmpty()) {
            return msgErro("Assinaturas", "Não encontrei assinatura com *" + frag + "*.");
        }
        List<ItemRef> itens = found.stream()
            .map(a -> new ItemRef(a.getId(),
                a.getNome() + " — " + BRL.format(a.getValor()) + "/mês, dia " + a.getDiaVencimento()
                    + (a.isAtivo() ? " (ativa)" : " (pausada)")))
            .collect(Collectors.toList());
        return montarSessaoDeleteConfirmado(userId, TipoAlvo.ASSINATURA, itens, "assinatura");
    }

    private String montarSessaoDeleteConfirmado(Long userId, TipoAlvo tipo, List<ItemRef> itens, String rotulo) {
        SessaoGestao s = new SessaoGestao();
        s.tipoAlvo = tipo;
        s.operacao = Operacao.DELETE;
        s.itens = itens;
        if (itens.size() == 1) {
            s.selecionadoId = itens.get(0).id;
            s.passo = Passo.CONFIRMAR_DELETE_UNICO;
            sessoes.put(userId, s);
            return "Encontrei *1* " + rotulo + ":\n\n1. " + itens.get(0).linha
                + "\n\nTem certeza que deseja remover " + (rotulo.startsWith("a") ? "a " : "a ")
                + rotulo + " *" + extrairNomeLinha(itens.get(0).linha) + "*?\nResponde *sim* ou *não*.";
        }
        s.passo = Passo.ESCOLHER_INDICE;
        sessoes.put(userId, s);
        StringBuilder sb = new StringBuilder();
        sb.append("Encontrei estas ").append(rotulo).append("s:\n\n");
        for (int i = 0; i < itens.size(); i++) {
            sb.append(i + 1).append(". ").append(itens.get(i).linha).append("\n");
        }
        sb.append("\nQual delas? Responde só com o *número* para remover.");
        return sb.toString();
    }

    private static String extrairNomeLinha(String linha) {
        if (linha == null) {
            return "";
        }
        int idx = linha.indexOf(" — ");
        return idx > 0 ? linha.substring(0, idx).trim() : linha.trim();
    }

    private String montarSessaoSimples(
        Long userId,
        TipoAlvo tipo,
        boolean edit,
        boolean del,
        List<ItemRef> itens,
        String rotulo,
        String promptEdit
    ) {
        SessaoGestao s = new SessaoGestao();
        s.tipoAlvo = tipo;
        s.operacao = edit ? Operacao.EDIT : Operacao.DELETE;
        s.itens = itens;
        if (itens.size() == 1) {
            s.selecionadoId = itens.get(0).id;
            if (s.operacao == Operacao.DELETE) {
                s.passo = Passo.CONFIRMAR_DELETE_UNICO;
                sessoes.put(userId, s);
                return "Encontrei *1* " + rotulo + ":\n\n1. " + itens.get(0).linha + "\n\nResponde *sim* para apagar ou *não* para cancelar.";
            }
            s.passo = tipo == TipoAlvo.CATEGORIA ? Passo.PEDIR_NOVO_NOME_CATEGORIA : Passo.PEDIR_NOVO_NOME_CONTA;
            sessoes.put(userId, s);
            return "Encontrei *1* " + rotulo + ":\n\n1. " + itens.get(0).linha + "\n\n" + promptEdit;
        }
        s.passo = Passo.ESCOLHER_INDICE;
        sessoes.put(userId, s);
        StringBuilder sb = new StringBuilder();
        sb.append("Encontrei esses itens:\n\n");
        for (int i = 0; i < itens.size(); i++) {
            sb.append(i + 1).append(". ").append(itens.get(i).linha).append("\n");
        }
        sb.append("\nQual deles? Responde só com o *número*.");
        return sb.toString();
    }

    private static boolean cartaoCorrespondeFraseFuzzy(CartaoCredito c, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return false;
        }
        String nome = c.getNome() != null ? c.getNome() : "";
        String banco = c.getBanco() != null ? c.getBanco() : "";
        return StringFuzzy.descricaoContemTermoFuzzy(nome, phrase)
            || StringFuzzy.descricaoContemTermoFuzzy(banco, phrase)
            || StringFuzzy.descricaoContemTermoFuzzy(banco + " " + nome, phrase);
    }

    private Optional<String> consumirIndice(Long userId, SessaoGestao s, String t) {
        Matcher m = APENAS_DIGITO.matcher(t);
        if (!m.matches()) {
            return Optional.of(msgInfo("Escolha", "Responde só com o *número* da lista (ex.: *2*)."));
        }
        int idx = Integer.parseInt(m.group(1));
        if (idx < 1 || idx > s.itens.size()) {
            return Optional.of(msgErro("Escolha", "Número inválido. Usa um valor entre 1 e " + s.itens.size() + "."));
        }
        ItemRef escolhido = s.itens.get(idx - 1);
        s.selecionadoId = escolhido.id;
        if (s.tipoAlvo == TipoAlvo.TRANSACAO) {
            if (s.operacao == Operacao.DELETE) {
                TransacaoDTO td = transacaoService.buscarPorId(escolhido.id, userId);
                if (td.getGrupoParcelaId() != null && !td.getGrupoParcelaId().isBlank()) {
                    s.passo = Passo.PARCEL_DELETE_OPCAO;
                    sessoes.put(userId, s);
                    return Optional.of(msgInfo("Parcelamento",
                        "Este lançamento faz parte de um *parcelamento*. Como queres apagar?\n"
                            + "• *1* — só esta parcela\n• *2* — esta e as seguintes\n• *3* — todo o parcelamento\n• *não* — cancelar"));
                }
                transacaoService.deletarTransacao(s.selecionadoId, userId);
                saldoService.notificarAlteracaoSaldo(userId);
                sessoes.remove(userId);
                return Optional.of(msgOk("Transação", "Apaguei: " + escolhido.linha));
            }
            s.passo = Passo.PEDIR_NOVO_VALOR_TRANSACAO;
            sessoes.put(userId, s);
            return Optional.of("Item *" + idx + "* selecionado.\n\nEnvia o *novo valor* (número, ex.: *180,50*).");
        }
        if (s.tipoAlvo == TipoAlvo.META) {
            if (s.operacao == Operacao.DELETE) {
                metaFinanceiraService.excluir(s.selecionadoId, userId);
                sessoes.remove(userId);
                return Optional.of(msgOk("Meta", "Meta removida."));
            }
            s.passo = Passo.PEDIR_NOVO_VALOR_META;
            sessoes.put(userId, s);
            return Optional.of("Meta *" + idx + "* selecionada.\n\nEnvia *valor total* e *percentual* (ex.: *8000 12*).");
        }
        if (s.tipoAlvo == TipoAlvo.CARTAO) {
            if (s.operacao == Operacao.DELETE) {
                long nf = cartaoCreditoService.contarFaturasDoCartao(s.selecionadoId, userId);
                if (nf > 0) {
                    s.passo = Passo.CARTAO_FATURA_DECISAO;
                    sessoes.put(userId, s);
                    return Optional.of("Cartão *" + idx + "* selecionado. Há *" + nf + "* fatura(s). "
                        + "Responde *mover* ou *apagar faturas* (ou *não* para cancelar).");
                }
                cartaoCreditoService.deletarCartaoCredito(s.selecionadoId, userId);
                sessoes.remove(userId);
                return Optional.of(msgOk("Cartão", "Cartão desativado."));
            }
            s.passo = Passo.PEDIR_NOVO_LIMITE_CARTAO;
            sessoes.put(userId, s);
            return Optional.of("Cartão *" + idx + "* selecionado.\n\nEnvia o *novo limite total* (número).");
        }
        if (s.tipoAlvo == TipoAlvo.CONTA_BANCARIA) {
            if (s.operacao == Operacao.DELETE) {
                contaBancariaService.inativar(s.selecionadoId, userId);
                saldoService.notificarAlteracaoSaldo(userId);
                sessoes.remove(userId);
                return Optional.of(msgOk("Conta", "Conta inativada."));
            }
            s.passo = Passo.PEDIR_NOVO_NOME_CONTA;
            sessoes.put(userId, s);
            return Optional.of("Conta *" + idx + "* selecionada.\n\nEnvia o *novo nome*.");
        }
        if (s.tipoAlvo == TipoAlvo.CATEGORIA) {
            if (s.operacao == Operacao.DELETE) {
                categoriaService.deletar(userId, s.selecionadoId);
                sessoes.remove(userId);
                return Optional.of(msgOk("Categoria", "Categoria removida."));
            }
            s.passo = Passo.PEDIR_NOVO_NOME_CATEGORIA;
            sessoes.put(userId, s);
            return Optional.of("Categoria *" + idx + "* selecionada.\n\nEnvia o *novo nome*.");
        }
        if (s.tipoAlvo == TipoAlvo.ORCAMENTO) {
            if (s.operacao == Operacao.DELETE) {
                orcamentoService.excluir(userId, s.selecionadoId);
                sessoes.remove(userId);
                return Optional.of(msgOk("Orçamento", "Orçamento removido."));
            }
            s.passo = Passo.PEDIR_NOVO_LIMITE_ORCAMENTO;
            sessoes.put(userId, s);
            return Optional.of("Orçamento *" + idx + "* selecionado.\n\nEnvia o *novo limite* (número).");
        }
        if (s.tipoAlvo == TipoAlvo.DESPESA_FIXA && s.operacao == Operacao.DELETE) {
            s.passo = Passo.CONFIRMAR_DELETE_UNICO;
            sessoes.put(userId, s);
            return Optional.of("Tem certeza que deseja remover a despesa fixa *"
                + extrairNomeLinha(escolhido.linha) + "*?\nResponde *sim* ou *não*.");
        }
        if (s.tipoAlvo == TipoAlvo.ASSINATURA && s.operacao == Operacao.DELETE) {
            s.passo = Passo.CONFIRMAR_DELETE_UNICO;
            sessoes.put(userId, s);
            return Optional.of("Tem certeza que deseja remover a assinatura *"
                + extrairNomeLinha(escolhido.linha) + "*?\nResponde *sim* ou *não*.");
        }
        sessoes.remove(userId);
        return Optional.empty();
    }

    private Optional<String> consumirOpcaoParcelDelete(Long userId, SessaoGestao s, String t) {
        String n = normalize(t);
        if (n.equals("nao") || n.equals("n") || n.startsWith("nao ") || n.contains("cancela")) {
            sessoes.remove(userId);
            return Optional.of(msgInfo("Cancelado", "Não apaguei nada."));
        }
        if (n.equals("1") || n.contains("so esta") || n.contains("só esta") || n.contains("apenas esta")) {
            transacaoService.deletarTransacao(s.selecionadoId, userId);
            saldoService.notificarAlteracaoSaldo(userId);
            sessoes.remove(userId);
            return Optional.of(msgOk("Transação", "Parcela removida."));
        }
        if (n.equals("2") || n.contains("seguintes") || n.contains("proximas") || n.contains("próximas")) {
            transacaoService.deletarTransacaoComModoParcelamento(s.selecionadoId, userId, "FUTURAS");
            saldoService.notificarAlteracaoSaldo(userId);
            sessoes.remove(userId);
            return Optional.of(msgOk("Parcelamento", "Removi esta parcela e as seguintes do mesmo grupo."));
        }
        if (n.equals("3") || n.contains("inteiro") || n.contains("tudo") || n.contains("completo")) {
            transacaoService.deletarTransacaoComModoParcelamento(s.selecionadoId, userId, "TUDO");
            saldoService.notificarAlteracaoSaldo(userId);
            sessoes.remove(userId);
            return Optional.of(msgOk("Parcelamento", "Removi todas as parcelas do grupo."));
        }
        return Optional.of(msgInfo("Opção", "Responde *1*, *2*, *3* ou *não*."));
    }

    private Optional<String> consumirSimNaoDeleteUnico(Long userId, SessaoGestao s, String t) {
        String n = normalize(t);
        if (n.equals("nao") || n.equals("n") || n.startsWith("nao ") || n.contains("cancela")) {
            sessoes.remove(userId);
            return Optional.of(msgInfo("Cancelado", "Não apaguei nada."));
        }
        if (n.equals("sim") || n.equals("s") || n.startsWith("sim ") || n.contains("confirmo")) {
            return switch (s.tipoAlvo) {
                case TRANSACAO -> confirmarDeleteTransacao(userId, s);
                case META -> {
                    metaFinanceiraService.excluir(s.selecionadoId, userId);
                    sessoes.remove(userId);
                    yield Optional.of(msgOk("Meta", "Meta removida."));
                }
                case CONTA_BANCARIA -> {
                    contaBancariaService.inativar(s.selecionadoId, userId);
                    saldoService.notificarAlteracaoSaldo(userId);
                    sessoes.remove(userId);
                    yield Optional.of(msgOk("Conta", "Conta inativada."));
                }
                case CATEGORIA -> {
                    categoriaService.deletar(userId, s.selecionadoId);
                    sessoes.remove(userId);
                    yield Optional.of(msgOk("Categoria", "Categoria removida."));
                }
                case ORCAMENTO -> {
                    orcamentoService.excluir(userId, s.selecionadoId);
                    sessoes.remove(userId);
                    yield Optional.of(msgOk("Orçamento", "Orçamento removido."));
                }
                case DESPESA_FIXA -> {
                    despesaFixaService.excluir(userId, s.selecionadoId);
                    sessoes.remove(userId);
                    yield Optional.of(msgOk("Despesa fixa", "Despesa fixa removida."));
                }
                case ASSINATURA -> {
                    assinaturaRecorrenteService.excluir(userId, s.selecionadoId);
                    sessoes.remove(userId);
                    yield Optional.of(msgOk("Assinatura", "Assinatura removida do cadastro."));
                }
                default -> {
                    sessoes.remove(userId);
                    yield Optional.of(msgErro("Fluxo", "Estado inválido; recomeça o pedido."));
                }
            };
        }
        return Optional.of(msgInfo("Confirmação", "Responde *sim* para apagar ou *não* para cancelar."));
    }

    private Optional<String> confirmarDeleteTransacao(Long userId, SessaoGestao s) {
        TransacaoDTO td = transacaoService.buscarPorId(s.selecionadoId, userId);
        if (td.getGrupoParcelaId() != null && !td.getGrupoParcelaId().isBlank()) {
            s.passo = Passo.PARCEL_DELETE_OPCAO;
            sessoes.put(userId, s);
            return Optional.of(msgInfo("Parcelamento",
                "Este lançamento faz parte de um *parcelamento*. Como queres apagar?\n"
                    + "• *1* — só esta parcela\n• *2* — esta e as seguintes\n• *3* — todo o parcelamento\n• *não* — cancelar"));
        }
        transacaoService.deletarTransacao(s.selecionadoId, userId);
        saldoService.notificarAlteracaoSaldo(userId);
        sessoes.remove(userId);
        return Optional.of(msgOk("Transação", "Transação apagada."));
    }

    private Optional<String> consumirNovoValorTransacao(Long userId, SessaoGestao s, String t) {
        BigDecimal v = parseMoney(t);
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of(msgErro("Valor", "Não percebi o valor. Envia só o número (ex.: *250* ou *180,50*)."));
        }
        TransacaoDTO dto = transacaoService.buscarPorId(s.selecionadoId, userId);
        dto.setValor(v);
        transacaoService.atualizarTransacao(s.selecionadoId, dto, userId);
        saldoService.notificarAlteracaoSaldo(userId);
        sessoes.remove(userId);
        return Optional.of(msgOk("Transação", "Valor atualizado para *" + BRL.format(v) + "*."));
    }

    private Optional<String> consumirNovoValorMeta(Long userId, SessaoGestao s, String t) {
        String[] parts = t.trim().split("\\s+");
        if (parts.length < 2) {
            return Optional.of(msgErro("Valores", "Envia *valor total* e *percentual* separados por espaço (ex.: *8000 12*)."));
        }
        BigDecimal valor = parseMoney(parts[0]);
        BigDecimal pct = parseMoney(parts[1]);
        if (valor == null || pct == null || valor.compareTo(BigDecimal.ZERO) <= 0 || pct.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of(msgErro("Valores", "Valor ou percentual inválido. Ex.: *8000 12*."));
        }
        MetaFinanceiraRequest req = new MetaFinanceiraRequest();
        MetaFinanceira existente = metaFinanceiraRepository.findByIdAndUsuarioId(s.selecionadoId, userId).orElseThrow();
        req.setDescricao(existente.getDescricao());
        req.setValorTotal(valor);
        req.setPercentualComprometimento(pct);
        req.setPrioridade(existente.getPrioridade() != null ? existente.getPrioridade() : 3);
        metaFinanceiraService.atualizar(s.selecionadoId, req, userId);
        sessoes.remove(userId);
        return Optional.of(msgOk("Meta", "Meta atualizada."));
    }

    private Optional<String> consumirNovoLimiteCartao(Long userId, SessaoGestao s, String t) {
        BigDecimal v = parseMoney(t);
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of(msgErro("Limite", "Envia o novo limite (número maior que zero)."));
        }
        CartaoCreditoDTO dto = cartaoCreditoService.buscarPorId(s.selecionadoId, userId);
        dto.setLimiteCredito(v);
        if (dto.getLimiteDisponivel() != null && dto.getLimiteDisponivel().compareTo(v) > 0) {
            dto.setLimiteDisponivel(v);
        }
        cartaoCreditoService.atualizarCartaoCredito(s.selecionadoId, dto, userId);
        sessoes.remove(userId);
        return Optional.of(msgOk("Cartão", "Limite atualizado para *" + BRL.format(v) + "*."));
    }

    private Optional<String> consumirNovoNomeCategoria(Long userId, SessaoGestao s, String t) {
        String nome = t != null ? t.trim() : "";
        if (nome.length() < 2) {
            return Optional.of(msgErro("Nome", "Envia um nome válido para a categoria."));
        }
        var existente = categoriaService.listarPorUsuario(userId).stream()
            .filter(c -> c.getId().equals(s.selecionadoId))
            .findFirst()
            .orElseThrow();
        existente.setNome(nome);
        categoriaService.atualizar(userId, s.selecionadoId, existente);
        sessoes.remove(userId);
        return Optional.of(msgOk("Categoria", "Nome atualizado para *" + nome + "*."));
    }

    private Optional<String> consumirNovoNomeConta(Long userId, SessaoGestao s, String t) {
        String nome = t != null ? t.trim() : "";
        if (nome.length() < 2) {
            return Optional.of(msgErro("Nome", "Envia um nome válido para a conta."));
        }
        var dto = contaBancariaService.buscarPorId(s.selecionadoId, userId);
        dto.setNome(nome);
        contaBancariaService.atualizar(s.selecionadoId, dto, userId);
        sessoes.remove(userId);
        return Optional.of(msgOk("Conta", "Nome atualizado para *" + nome + "*."));
    }

    private Optional<String> consumirNovoLimiteOrcamento(Long userId, SessaoGestao s, String t) {
        BigDecimal v = parseMoney(t);
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of(msgErro("Limite", "Envia o novo limite (número maior que zero)."));
        }
        Orcamento o = orcamentoRepository.findByIdAndUsuarioId(s.selecionadoId, userId).orElseThrow();
        OrcamentoRequest req = new OrcamentoRequest();
        req.setCategoriaId(o.getCategoria().getId());
        req.setValorLimite(v);
        req.setMes(o.getMes());
        req.setAno(o.getAno());
        req.setCompartilhado(o.isCompartilhado());
        orcamentoService.salvar(userId, req);
        sessoes.remove(userId);
        return Optional.of(msgOk("Orçamento", "Limite atualizado para *" + BRL.format(v) + "*."));
    }

    private Optional<String> consumirDecisaoFatura(Long userId, SessaoGestao s, String t) {
        String n = normalize(t);
        if (n.equals("nao") || n.contains("cancela")) {
            sessoes.remove(userId);
            return Optional.of(msgInfo("Cartão", "Cancelado. O cartão não foi alterado."));
        }
        if (n.contains("mover") || n.contains("transferir")) {
            List<CartaoCredito> outros = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(userId).stream()
                .filter(c -> !c.getId().equals(s.selecionadoId))
                .collect(Collectors.toList());
            if (outros.isEmpty()) {
                sessoes.remove(userId);
                return Optional.of(msgErro("Cartão", "Não há outro cartão ativo para mover as faturas. Cadastra um cartão ou responde *apagar faturas*."));
            }
            s.outrosCartoes = outros.stream().map(c -> new ItemRef(c.getId(),
                (c.getBanco() != null ? c.getBanco() : "") + " · " + c.getNome())).collect(Collectors.toList());
            s.passo = Passo.CARTAO_ESCOLHER_DESTINO;
            sessoes.put(userId, s);
            StringBuilder sb = new StringBuilder("Para qual cartão mover as faturas?\n\n");
            for (int i = 0; i < s.outrosCartoes.size(); i++) {
                sb.append(i + 1).append(". ").append(s.outrosCartoes.get(i).linha).append("\n");
            }
            sb.append("\nDigite o *número*.");
            return Optional.of(sb.toString());
        }
        if (n.contains("apagar") && n.contains("fatura")) {
            cartaoCreditoService.apagarFaturasDoCartao(s.selecionadoId, userId);
            cartaoCreditoService.deletarCartaoCredito(s.selecionadoId, userId);
            sessoes.remove(userId);
            return Optional.of(msgOk("Cartão", "Faturas removidas e cartão desativado."));
        }
        return Optional.of(msgInfo("Cartão", "Responde *mover*, *apagar faturas* ou *não*."));
    }

    private Optional<String> consumirDestinoFatura(Long userId, SessaoGestao s, String t) {
        Matcher m = APENAS_DIGITO.matcher(t);
        if (!m.matches()) {
            return Optional.of(msgInfo("Destino", "Digite o número do cartão de destino."));
        }
        int idx = Integer.parseInt(m.group(1));
        if (s.outrosCartoes == null || idx < 1 || idx > s.outrosCartoes.size()) {
            return Optional.of(msgErro("Destino", "Número inválido."));
        }
        Long destId = s.outrosCartoes.get(idx - 1).id;
        cartaoCreditoService.reatribuirFaturasParaOutroCartao(s.selecionadoId, destId, userId);
        cartaoCreditoService.deletarCartaoCredito(s.selecionadoId, userId);
        sessoes.remove(userId);
        return Optional.of(msgOk("Cartão", "Faturas movidas e cartão original desativado."));
    }

    private static String linhaTransacao(Transacao tr) {
        String dt = tr.getDataTransacao() != null ? DDM.format(tr.getDataTransacao()) : "?";
        String tipo = tr.getTipoTransacao() != null ? tr.getTipoTransacao().name() : "";
        return dt + " — " + BRL.format(tr.getValor()) + " — " + trunc(tr.getDescricao(), 48) + " (" + tipo + ")";
    }

    private static String trunc(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static YearMonth resolveMes(JsonNode cmd, String sourceText) {
        YearMonth now = YearMonth.now();
        int month = readInt(cmd, "reportMonth", 0);
        int year = readInt(cmd, "reportYear", 0);
        if (month >= 1 && month <= 12 && year >= 2000) {
            return YearMonth.of(year, month);
        }
        if (sourceText != null) {
            String low = sourceText.toLowerCase(Locale.ROOT);
            if (low.contains("mês passado") || low.contains("mes passado")) {
                return now.minusMonths(1);
            }
        }
        return now;
    }

    private static int readInt(JsonNode cmd, String field, int def) {
        if (!cmd.has(field) || cmd.get(field).isNull()) {
            return def;
        }
        try {
            return cmd.get(field).asInt(def);
        } catch (Exception e) {
            return def;
        }
    }

    private static String labelMes(YearMonth ym) {
        String m = ym.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
        return m.substring(0, 1).toUpperCase(Locale.ROOT) + m.substring(1) + " de " + ym.getYear();
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.replace("R$", "").replace("r$", "").trim();
        try {
            if (t.matches(".*\\d+[.,]\\d{3}([.,]\\d{2})?.*") || (t.contains(",") && t.lastIndexOf(',') > t.indexOf('.'))) {
                t = t.replace(".", "").replace(",", ".");
            } else {
                t = t.replace(",", ".");
            }
            return new BigDecimal(t.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
            .replace("ã", "a")
            .replace("á", "a")
            .replace("ê", "e")
            .trim();
    }

    private static String whatsAppFirstNonBlank(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p.trim();
            }
        }
        return "";
    }

    private static String msgOk(String titulo, String corpo) {
        return "✅ *" + titulo + "*\n" + corpo;
    }

    private static String msgErro(String titulo, String corpo) {
        return "❌ *" + titulo + "*\n" + corpo;
    }

    private static String msgInfo(String titulo, String corpo) {
        return "ℹ️ *" + titulo + "*\n" + corpo;
    }

    private enum TipoAlvo { TRANSACAO, META, CARTAO, CONTA_BANCARIA, CATEGORIA, ORCAMENTO, DESPESA_FIXA, ASSINATURA }
    private enum Operacao { DELETE, EDIT }
    private enum Passo {
        ESCOLHER_INDICE,
        CONFIRMAR_DELETE_UNICO,
        PARCEL_DELETE_OPCAO,
        PEDIR_NOVO_VALOR_TRANSACAO,
        PEDIR_NOVO_VALOR_META,
        PEDIR_NOVO_LIMITE_CARTAO,
        PEDIR_NOVO_NOME_CATEGORIA,
        PEDIR_NOVO_NOME_CONTA,
        PEDIR_NOVO_LIMITE_ORCAMENTO,
        CARTAO_FATURA_DECISAO,
        CARTAO_ESCOLHER_DESTINO
    }

    private static class SessaoGestao {
        TipoAlvo tipoAlvo;
        Operacao operacao;
        Passo passo;
        List<ItemRef> itens;
        List<ItemRef> outrosCartoes;
        YearMonth mesRef;
        Long selecionadoId;
    }

    private record ItemRef(Long id, String linha) {
    }

    private static final Pattern CREATE_META_INTENT_GESTAO = Pattern.compile(
        "(?is)\\b(?:cadastr(?:ar|a)|cri(?:ar|e)|registr(?:ar|a)|adicion(?:ar|a))\\b.*\\b(?:uma\\s+)?(?:nova\\s+)?meta\\b|\\bnova\\s+meta\\b");

    private static boolean parecePedidoCriarMeta(String text) {
        return text != null && !text.isBlank() && CREATE_META_INTENT_GESTAO.matcher(text).find();
    }

    private static String extrairNomeMetaGestao(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher m = Pattern.compile("(?is)\\bnome\\s+([\\p{L}0-9][\\p{L}0-9'\\- ]{0,60}?)(?:\\s+chamada|\\.|,|$)").matcher(text);
        if (m.find()) {
            return m.group(1).trim().replaceAll("(?i)\\s+chamada.*$", "").trim();
        }
        m = Pattern.compile("(?is)\\bchamada\\s+([\\p{L}0-9][\\p{L}0-9'\\- ]{0,60}?)(?:\\.|,|$)").matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        m = Pattern.compile("(?is)\\b(?:nova\\s+)?meta\\s+([\\p{L}0-9][\\p{L}0-9'\\- ]{1,60})").matcher(text);
        if (m.find()) {
            String s = m.group(1).trim();
            s = s.replaceAll("(?i)\\s+(com\\s+o\\s+nome|chamada).*$", "").trim();
            return s;
        }
        return "";
    }
}
