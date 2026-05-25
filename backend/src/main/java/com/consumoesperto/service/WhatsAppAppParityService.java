package com.consumoesperto.service;

import com.consumoesperto.dto.WhatsAppParityItemDTO;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Catálogo único de paridade: o que o utilizador pode fazer no WhatsApp (J.A.R.V.I.S.)
 * e nas telas do app — mesma base para API, página WhatsApp e resposta {@code ajuda/menu}.
 */
@Service
public class WhatsAppAppParityService {

    private static final List<WhatsAppParityItemDTO> CATALOGO = List.of(
        item("transacoes-despesa", "Despesas", "/transacoes", "Transações", "BOTH",
            List.of(
                "gastei 45,90 no mercado",
                "paguei 120 no cartão Nubank",
                "fiz um pix de 80 da conta Nubank",
                "mercado 200 no Itaú em 3x sem juros"
            ),
            List.of("Nova despesa", "Editar / apagar", "Filtrar por mês e categoria", "Vincular cartão ou conta"),
            "PIX e débito em conta não usam fatura do cartão."
        ),
        item("transacoes-receita", "Receitas", "/transacoes", "Transações", "BOTH",
            List.of("recebi 3500 salário na conta Nubank", "receita 500 freelance"),
            List.of("Nova receita", "Editar / apagar", "Filtrar por período"),
            null
        ),
        item("contas", "Contas bancárias", "/contas", "Contas", "BOTH",
            List.of("cria conta Nubank corrente saldo 1500", "cadastra carteira dinheiro"),
            List.of("Criar / editar / inativar conta", "Saldo e conta padrão", "Transferências entre contas"),
            "Despesas com PIX devem citar a conta ou o banco."
        ),
        item("cartoes", "Cartões de crédito", "/cartoes", "Cartões", "BOTH",
            List.of(
                "cartão Nubank final 1234 vence dia 10 limite 5000",
                "edita limite do Nubank para 6000",
                "ativar cartão Itaú",
                "apague meu cartão Inter"
            ),
            List.of("Cadastrar cartão", "Limite e fechamento", "Inativar / reativar"),
            "Apagar cartão no WhatsApp pode pedir mover ou apagar faturas."
        ),
        item("faturas", "Faturas", "/faturas", "Faturas", "BOTH",
            List.of(
                "quanto gastei no Nubank?",
                "resumo da fatura do Inter",
                "enviar PDF da fatura (documento)"
            ),
            List.of("Ver faturas abertas/fechadas", "Lançamentos por fatura", "Conciliação"),
            "PDF de fatura: envie o ficheiro no WhatsApp ou use Importações no app."
        ),
        item("categorias", "Categorias", "/categorias", "Categorias", "BOTH",
            List.of("cria categoria Pets", "edita categoria Alimentação cor azul"),
            List.of("Criar com cor da paleta", "Editar nome, cor e ícone", "Apagar"),
            null
        ),
        item("orcamentos", "Orçamentos mensais", "/orcamentos", "Orçamentos", "BOTH",
            List.of("orçamento 800 em Alimentação", "limite 500 para Lazer este mês"),
            List.of("Definir limite por categoria/mês", "Acompanhar consumo vs limite"),
            null
        ),
        item("metas", "Metas financeiras", "/metas", "Metas", "BOTH",
            List.of(
                "cadastra meta viagem 8000 15%",
                "quanto tempo para TV 2000 usando 10% da renda",
                "apague meta geladeira"
            ),
            List.of("Criar meta", "Valor objetivo e prazo", "Simulador de prazo"),
            "Simulação de compra também em Simulações."
        ),
        item("renda", "Renda e salário", "/renda", "Renda", "BOTH",
            List.of(
                "salário bruto 8000 INSS 600 plano 400 IRRF 500 dia 5",
                "enviar PDF do contracheque"
            ),
            List.of("Configurar salário e descontos", "Dia de pagamento", "Importar holerite PDF"),
            null
        ),
        item("despesas-fixas", "Despesas fixas (recorrentes)", "/perfil", "Perfil", "BOTH",
            List.of("salve essa despesa fixa de 250 para internet dia 10", "tenho recorrência?"),
            List.of("Secção Despesas fixas no Perfil", "Dia de vencimento e valor"),
            "Pergunta «tenho recorrência?» lista assinaturas repetidas."
        ),
        item("relatorios", "Relatórios e PDF", "/relatorios", "Relatórios", "BOTH",
            List.of("gera PDF de maio", "relatório do mês passado"),
            List.of("Relatórios por período", "Exportar PDF no app"),
            "PDF no WhatsApp requer Evolution ligada ao número."
        ),
        item("simulacoes", "Simulações", "/simulacoes", "Simulações", "BOTH",
            List.of("quero comprar notebook 4500 comprometendo 12% do salário"),
            List.of("Simular prazo de meta/compra", "Percentual da renda"),
            null
        ),
        item("investimentos", "Onde investir", "/investimentos", "Investimentos", "BOTH",
            List.of("onde investir o saldo?", "poupança vs CDB vs Tesouro"),
            List.of("Sugestões Selic / IPCA", "Cenários de rendimento"),
            null
        ),
        item("dashboard-previsao", "Previsão do mês", "/dashboard", "Dashboard", "BOTH",
            List.of("como vou fechar o mês?", "vou ficar no vermelho?", "previsão deste mês"),
            List.of("Resumo e gráficos", "Projeção e previsão (carregamento em fases)"),
            null
        ),
        item("importacoes", "Importações pendentes", "/importacoes-pendentes", "Importações", "BOTH",
            List.of("confirmar importação de fatura (sim/não)", "confirmar contracheque"),
            List.of("Rever PDFs importados", "Confirmar ou descartar"),
            "Fluxos iniciados por PDF ou cupom no WhatsApp."
        ),
        item("whatsapp-ocr", "Cupom / nota (foto)", "", "", "WHATSAPP_ONLY",
            List.of("enviar foto do cupom fiscal", "confirmar com sim após leitura"),
            List.of(),
            "No app use Transações → novo lançamento manual."
        ),
        item("whatsapp-audio", "Comandos por voz", "", "", "WHATSAPP_ONLY",
            List.of("mensagem de áudio: «gastei 30 no mercado»"),
            List.of(),
            "Transcrição automática (Groq) no servidor."
        ),
        item("whatsapp-jarvis", "Memória J.A.R.V.I.S.", "", "", "WHATSAPP_ONLY",
            List.of("Jarvis, anote isso: não comprar eletrónica em janeiro"),
            List.of(),
            "Contexto semântico para respostas futuras."
        ),
        item("whatsapp-ia-chat", "Chat IA no app", "/dashboard", "Dashboard", "BOTH",
            List.of("perguntas em linguagem natural (mesmo motor do WhatsApp)"),
            List.of("Painel J.A.R.V.I.S. / chat no dashboard"),
            "Web: POST /api/ia-chat — mesmas regras que o bot."
        ),
        item("familia", "Família", "/familia", "Família", "APP_ONLY",
            List.of(),
            List.of("Grupo familiar", "Partilha de visão (conforme plano)"),
            "Gestão de família apenas no app por agora."
        ),
        item("score", "Score e nível", "/score", "Score", "APP_ONLY",
            List.of(),
            List.of("Pontuação e nível de autorização"),
            "Visível na barra lateral e no perfil."
        ),
        item("perfil", "Perfil e IA", "/perfil", "Perfil", "BOTH",
            List.of("configurar tratamento Jarvis (via app)"),
            List.of("Foto, nome, preferências J.A.R.V.I.S.", "Chaves de IA por utilizador"),
            "Vincular WhatsApp em WhatsApp no menu."
        ),
        item("whatsapp-vinculo", "Vincular WhatsApp", "/whatsapp-config", "WhatsApp", "APP_ONLY",
            List.of("usar o mesmo número no chat «Eu» / consigo mesmo"),
            List.of("Vincular número", "QR Code Evolution", "Desvincular"),
            "Instância dedicada ce-u[id] por conta."
        )
    );

    public List<WhatsAppParityItemDTO> listarTudo() {
        return CATALOGO;
    }

    public List<WhatsAppParityItemDTO> listarPorRota(String rota) {
        if (rota == null || rota.isBlank()) {
            return List.of();
        }
        String norm = normalizarRota(rota);
        return CATALOGO.stream()
            .filter(i -> i.getRotaApp() != null && !i.getRotaApp().isBlank())
            .filter(i -> norm.equals(normalizarRota(i.getRotaApp()))
                || norm.startsWith(normalizarRota(i.getRotaApp()) + "/"))
            .collect(Collectors.toList());
    }

    public boolean isPedidoAjudaOuMenu(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String t = normalize(texto.trim());
        if (t.length() > 80) {
            return false;
        }
        return t.equals("ajuda")
            || t.equals("help")
            || t.equals("menu")
            || t.equals("comandos")
            || t.equals("o que posso fazer")
            || t.equals("oque posso fazer")
            || t.equals("o que consigo fazer")
            || t.equals("lista de comandos")
            || t.equals("sincronizar")
            || t.equals("paridade")
            || t.startsWith("ajuda ")
            || t.startsWith("menu ")
            || t.contains("o que posso fazer no whats")
            || t.contains("o que da pra fazer");
    }

    public String montarMensagemAjudaWhatsapp() {
        StringBuilder sb = new StringBuilder();
        sb.append("*Menu — App e WhatsApp*\n");
        sb.append("O mesmo cadastro alimenta as duas interfaces. Exemplos por área:\n\n");

        String secaoAtual = "";
        for (WhatsAppParityItemDTO i : CATALOGO) {
            if ("WHATSAPP_ONLY".equals(i.getCanal()) && !secaoAtual.equals("Z")) {
                secaoAtual = "Z";
                sb.append("*Só no WhatsApp*\n");
            }
            String menu = i.getMenuApp() != null && !i.getMenuApp().isBlank() ? i.getMenuApp() : "—";
            sb.append("• *").append(i.getTitulo()).append("*");
            if (i.getRotaApp() != null && !i.getRotaApp().isBlank()) {
                sb.append(" → app: *").append(menu).append("*");
            }
            sb.append("\n");
            if (i.getExemplosWhatsapp() != null && !i.getExemplosWhatsapp().isEmpty()) {
                sb.append("  WA: «").append(i.getExemplosWhatsapp().get(0)).append("»\n");
            }
            if (i.getNota() != null && !i.getNota().isBlank()) {
                sb.append("  _").append(i.getNota()).append("_\n");
            }
        }
        sb.append("\n_Detalhe completo: app → menu *WhatsApp* → «O que fazer em cada tela»._\n");
        sb.append("Envie *ajuda [tema]* (ex.: *ajuda cartões*) ou abra a secção no app.");
        return sb.toString();
    }

    public String montarMensagemAjudaTema(String texto) {
        String tema = extrairTemaAjuda(texto);
        if (tema.isBlank()) {
            return montarMensagemAjudaWhatsapp();
        }
        List<WhatsAppParityItemDTO> hits = CATALOGO.stream()
            .filter(i -> correspondeTema(i, tema))
            .collect(Collectors.toList());
        if (hits.isEmpty()) {
            return "Não encontrei o tema *" + tema + "*.\n\n" + montarMensagemAjudaWhatsapp();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(capitalizar(tema)).append("* — app e WhatsApp\n\n");
        for (WhatsAppParityItemDTO i : hits) {
            formatarItemDetalhado(sb, i);
        }
        return sb.toString();
    }

    private void formatarItemDetalhado(StringBuilder sb, WhatsAppParityItemDTO i) {
        sb.append("*").append(i.getTitulo()).append("*\n");
        if (i.getRotaApp() != null && !i.getRotaApp().isBlank()) {
            sb.append("App (*").append(i.getMenuApp()).append("*): ");
            if (i.getAcoesApp() != null && !i.getAcoesApp().isEmpty()) {
                sb.append(String.join("; ", i.getAcoesApp()));
            } else {
                sb.append("formulários e listagens na tela.");
            }
            sb.append("\n");
        }
        if (i.getExemplosWhatsapp() != null && !i.getExemplosWhatsapp().isEmpty()) {
            sb.append("WhatsApp:\n");
            for (String ex : i.getExemplosWhatsapp()) {
                sb.append("• «").append(ex).append("»\n");
            }
        }
        if (i.getNota() != null && !i.getNota().isBlank()) {
            sb.append("_").append(i.getNota()).append("_\n");
        }
        sb.append("\n");
    }

    private static boolean correspondeTema(WhatsAppParityItemDTO i, String tema) {
        StringBuilder sb = new StringBuilder();
        sb.append(i.getTitulo()).append(' ').append(i.getMenuApp()).append(' ').append(i.getId()).append(' ')
            .append(i.getRotaApp());
        if (i.getExemplosWhatsapp() != null) {
            for (String ex : i.getExemplosWhatsapp()) {
                sb.append(' ').append(ex);
            }
        }
        String blob = normalize(sb.toString());
        String temaNorm = normalize(tema);
        if (blob.contains(temaNorm)) {
            return true;
        }
        for (String tok : temaNorm.split("\\s+")) {
            if (tok.length() >= 3 && !blob.contains(tok)) {
                return false;
            }
        }
        return !temaNorm.isBlank();
    }

    private static String extrairTemaAjuda(String texto) {
        String t = normalize(texto == null ? "" : texto);
        for (String prefix : new String[]{"ajuda ", "menu ", "comandos "}) {
            if (t.startsWith(prefix)) {
                return t.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static String capitalizar(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String normalizarRota(String rota) {
        String r = rota.trim();
        if (!r.startsWith("/")) {
            r = "/" + r;
        }
        if (r.length() > 1 && r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private static WhatsAppParityItemDTO item(
        String id,
        String titulo,
        String rotaApp,
        String menuApp,
        String canal,
        List<String> exemplosWhatsapp,
        List<String> acoesApp,
        String nota
    ) {
        return WhatsAppParityItemDTO.builder()
            .id(id)
            .titulo(titulo)
            .rotaApp(rotaApp)
            .menuApp(menuApp)
            .canal(canal)
            .exemplosWhatsapp(exemplosWhatsapp != null ? List.copyOf(exemplosWhatsapp) : List.of())
            .acoesApp(acoesApp != null ? List.copyOf(acoesApp) : List.of())
            .nota(nota)
            .build();
    }

    private static String normalize(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
