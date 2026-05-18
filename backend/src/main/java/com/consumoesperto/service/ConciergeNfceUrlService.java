package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protocolo Concierge — deteção de URL de consulta de nota fiscal (SEFAZ/NFC-e) e extração de itens (IA + HTML bruto).
 */
@Service
@Slf4j
public class ConciergeNfceUrlService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"')]+", Pattern.CASE_INSENSITIVE);
    private static final int HTML_MAX = 32000;

    private final RestTemplate restTemplate;
    private final OpenAiService openAiService;
    private final TransacaoService transacaoService;
    private final UsuarioRepository usuarioRepository;

    public ConciergeNfceUrlService(
        OpenAiService openAiService,
        TransacaoService transacaoService,
        UsuarioRepository usuarioRepository
    ) {
        this.openAiService = openAiService;
        this.transacaoService = transacaoService;
        this.usuarioRepository = usuarioRepository;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(20_000);
        this.restTemplate = new RestTemplate(f);
    }

    public Optional<String> detectarUrlNfce(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        Matcher m = URL_PATTERN.matcher(texto);
        while (m.find()) {
            String u = m.group().replaceAll("[),.;]+$", "");
            if (pareceUrlFiscal(u)) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    private static boolean pareceUrlFiscal(String u) {
        if (u == null) {
            return false;
        }
        String x = u.toLowerCase();
        return x.contains("nfce") || x.contains("nfe") || x.contains("sefaz") || x.contains("fazenda.")
            || (x.contains("consulta") && (x.contains("danfe") || x.contains("nfc")));
    }

    /**
     * Processa URL fiscal: obtém HTML, extrai itens via IA e grava despesas confirmadas.
     */
    public Optional<String> processarSeUrlFiscal(Long userId, String texto, JarvisProtocolService jarvis) {
        Optional<String> url = detectarUrlNfce(texto);
        if (url.isEmpty()) {
            return Optional.empty();
        }
        String u = url.get();
        log.info("[JARVIS-LOG] Concierge: URL fiscal detetada userId={} url={}", userId, u);
        try {
            URI uri = URI.create(u);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.of("Não consegui interpretar o link da nota. Confirme se copiou o URL completo, por favor.");
        }

        String html;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "ConsumoEsperto-JARVIS/1.0");
            ResponseEntity<String> resp = restTemplate.exchange(u, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            html = resp.getBody();
        } catch (Exception e) {
            log.warn("[JARVIS-LOG] Concierge: falha ao obter URL: {}", e.getMessage());
            return Optional.of("Não consegui aceder ao portal da nota (rede ou bloqueio). Tente mais tarde ou envie o cupom em imagem.");
        }
        if (html == null || html.isBlank()) {
            return Optional.of("A página da nota veio vazia. Pode enviar *captura* da nota em imagem?");
        }
        if (html.length() > HTML_MAX) {
            html = html.substring(0, HTML_MAX);
        }

        String system = "É um extrator de dados de nota fiscal brasileira (HTML de portal SEFAZ ou consumidor). "
            + "Retorne estritamente JSON sem markdown: "
            + "{\"estabelecimento\":\"string\",\"itens\":[{\"descricao\":\"string\",\"valor\":number}],\"confianca\":0-1,\"erro\":\"\"}. "
            + "Valores em reais (número decimal). Se não houver itens claros, preencha erro e confianca baixa.";
        String userPrompt = "URL: " + u + "\n\nTrecho HTML:\n" + html;

        JsonNode root;
        try {
            root = openAiService.gerarJson(userId, system, userPrompt);
        } catch (Exception e) {
            log.warn("[JARVIS-LOG] Concierge: IA extratora falhou: {}", e.getMessage());
            return Optional.of("Não consegui interpretar o conteúdo da nota automaticamente. Envie *foto* do cupom se preferir.");
        }
        double conf = root.path("confianca").asDouble(0);
        String erro = root.path("erro").asText("");
        if (conf < 0.35 || !erro.isBlank()) {
            return Optional.of("A leitura automática da nota não ficou confiável. Pode enviar *imagem* do cupom?");
        }
        String estab = root.path("estabelecimento").asText("Nota fiscal (URL)").trim();
        List<ItemLinha> linhas = new ArrayList<>();
        for (JsonNode n : root.path("itens")) {
            String d = n.path("descricao").asText("").trim();
            BigDecimal val = n.path("valor").isNumber()
                ? BigDecimal.valueOf(n.path("valor").asDouble()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            if (!d.isBlank() && val.compareTo(BigDecimal.ZERO) > 0) {
                linhas.add(new ItemLinha(d, val));
            }
        }
        if (linhas.isEmpty()) {
            return Optional.of("Não encontrei linhas de itens na nota. Confirme se o link é o de *consulta pública* completo.");
        }

        int criadas = 0;
        for (ItemLinha it : linhas) {
            TransacaoDTO dto = new TransacaoDTO();
            dto.setDescricao((estab + " — " + it.descricao()).replaceAll("\\s+", " ").trim());
            if (dto.getDescricao().length() > 200) {
                dto.setDescricao(dto.getDescricao().substring(0, 200));
            }
            dto.setValor(it.valor());
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
            dto.setDataTransacao(LocalDateTime.now());
            dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
            transacaoService.criarTransacao(dto, userId, true);
            criadas++;
        }
        String voc = jarvis != null ? jarvis.resolveVocative(userId, usuarioRepository) : "";
        log.info("[JARVIS-LOG] Concierge: gravadas {} linhas userId={}", criadas, userId);
        return Optional.of(
            (voc.isBlank() ? "" : voc + ", ")
                + "importei *" + criadas + "* itens da nota referenciada (*" + estab + "*). "
                + "Confira categorias no aplicativo e ajuste se desejar."
        );
    }

    private record ItemLinha(String descricao, BigDecimal valor) {}
}
