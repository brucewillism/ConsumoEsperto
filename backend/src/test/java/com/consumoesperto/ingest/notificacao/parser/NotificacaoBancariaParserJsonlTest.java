package com.consumoesperto.ingest.notificacao.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conjunto versionado de amostras reais/sintéticas. Para um formato novo do banco:
 * 1. Copie o texto da notificação (tela Captura automática → log).
 * 2. Acrescente uma linha em {@code src/test/resources/notificacoes-bancarias.jsonl}.
 * 3. Rode esta classe; ajuste o parser se o destino esperado falhar.
 */
class NotificacaoBancariaParserJsonlTest {

    private final NotificacaoBancariaParser parser = new NotificacaoBancariaParser();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void amostrasVersionadas_correspondemAoEsperado() throws Exception {
        int linhas = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream("/notificacoes-bancarias.jsonl")),
            StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                linhas++;
                JsonNode n = mapper.readTree(line);
                String id = n.path("id").asText();
                ParsedNotificacaoBancaria parsed = parser.parse(
                    n.path("app").asText(),
                    n.path("titulo").asText(null),
                    n.path("texto").asText()
                );
                JsonNode exp = n.get("esperado");
                assertEquals(exp.path("destino").asText(), parsed.destino(), () -> id + " destino " + parsed);
                if (exp.has("valor")) {
                    assertNotNull(parsed.valor(), id);
                    assertEquals(0, new BigDecimal(exp.path("valor").asText()).compareTo(parsed.valor()), id);
                }
                if (exp.has("estabelecimento")) {
                    assertNotNull(parsed.estabelecimento(), id);
                    assertTrue(
                        parsed.estabelecimento().contains(exp.path("estabelecimento").asText()),
                        () -> id + " merchant=" + parsed.estabelecimento()
                    );
                }
                if (exp.has("tipo")) {
                    assertEquals(exp.path("tipo").asText(), parsed.tipo(), id);
                }
                if (exp.has("canal")) {
                    assertEquals(exp.path("canal").asText(), parsed.canal(), id);
                }
                if (exp.has("tipoTransacao")) {
                    assertEquals(exp.path("tipoTransacao").asText(), parsed.tipoTransacao(), id);
                }
            }
        }
        assertTrue(linhas >= 10, "conjunto de amostras demasiado pequeno");
    }
}
