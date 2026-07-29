package com.consumoesperto.integration.runtime;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida PDF baixado via HTTP contra backend em execução (profile integração).
 * Executar: INTEGRACAO_BASE_URL=http://localhost:18081 mvn test -Dtest=PdfRuntimeHttpValidationTest
 */
@EnabledIfEnvironmentVariable(named = "INTEGRACAO_BASE_URL", matches = ".+")
class PdfRuntimeHttpValidationTest {

    private final RestTemplate rest = new RestTemplate();
    private final String base = System.getenv("INTEGRACAO_BASE_URL").replaceAll("/$", "");
    private final String suffix = String.valueOf(System.nanoTime());
    private final String pass = "SenhaTeste123!";

    @Test
    void pdfMensal_runtime_conteudoDeterministico() throws Exception {
        String email = "pdf_rt_" + suffix + "@test.local";
        Map<String, Object> reg = Map.of(
            "username", email, "email", email, "password", pass, "nome", "PDF Runtime " + suffix
        );
        rest.postForEntity(base + "/api/auth/registro", reg, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> login = rest.postForEntity(base + "/api/auth/login",
            Map.of("username", email, "password", pass), Map.class).getBody();
        String token = (String) login.get("token");
        HttpHeaders auth = bearer(token);

        Map<String, Object> cat = rest.exchange(base + "/api/categorias", HttpMethod.POST,
            new HttpEntity<>(Map.of("nome", "PDFCat", "descricao", "", "cor", "#000", "icone", "tag"), auth),
            Map.class).getBody();
        Map<String, Object> conta = rest.exchange(base + "/api/contas-bancarias", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "nome", "ContaPDF", "tipo", "CORRENTE", "saldoAtual", 2000,
                "limiteChequeEspecial", 0, "ativa", true, "padrao", true
            ), auth), Map.class).getBody();

        Number catId = (Number) cat.get("id");
        Number contaId = (Number) conta.get("id");
        String dt = java.time.LocalDate.now().atStartOfDay().toString();

        rest.exchange(base + "/api/transacoes", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "descricao", "Despesa PDF RT " + suffix, "valor", 75.50, "tipoTransacao", "DESPESA",
                "categoriaId", catId.longValue(), "contaBancariaId", contaId.longValue(), "dataTransacao", dt
            ), auth), Map.class);
        rest.exchange(base + "/api/transacoes", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "descricao", "Receita PDF RT", "valor", 500, "tipoTransacao", "RECEITA",
                "categoriaId", catId.longValue(), "contaBancariaId", contaId.longValue(), "dataTransacao", dt
            ), auth), Map.class);

        YearMonth ym = YearMonth.now();
        Map<String, Object> rel = rest.exchange(
            base + "/api/relatorios/mensal?ano=" + ym.getYear() + "&mes=" + ym.getMonthValue(),
            HttpMethod.GET, new HttpEntity<>(auth), Map.class).getBody();
        assertNotNull(rel);

        ResponseEntity<byte[]> pdf = rest.exchange(
            base + "/api/relatorios/mensal.pdf?ano=" + ym.getYear() + "&mes=" + ym.getMonthValue(),
            HttpMethod.GET, new HttpEntity<>(auth), byte[].class);
        assertTrue(pdf.getStatusCode().is2xxSuccessful());
        byte[] bytes = pdf.getBody();
        assertNotNull(bytes);
        assertTrue(bytes.length > 500);
        assertTrue(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));

        Path tmp = Files.createTempFile("pdf-runtime-", ".pdf");
        try {
            Files.write(tmp, bytes);
            String text;
            try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(bytes))) {
                text = new PDFTextStripper().getText(doc);
            }
            assertNotNull(text);
            assertFalse(text.contains("RuntimeException"));
            assertTrue(text.length() > 30);
            List<Map<String, String>> checks = List.of(
                Map.of("campo", "mes_ano", "esperado", String.valueOf(ym.getYear()), "ok", String.valueOf(text.contains(String.valueOf(ym.getYear())))),
                Map.of("campo", "nao_corrompido", "esperado", "texto>30", "ok", String.valueOf(text.length() > 30))
            );
            checks.forEach(c -> assertTrue(Boolean.parseBoolean(c.get("ok")), c.get("campo")));
        } finally {
            Files.deleteIfExists(tmp);
        }

        // periodo vazio
        try {
            rest.exchange(base + "/api/relatorios/mensal.pdf?ano=2099&mes=1",
                HttpMethod.GET, new HttpEntity<>(auth), byte[].class);
            assertTrue(false, "PDF periodo vazio deveria retornar 404");
        } catch (HttpStatusCodeException ex) {
            assertTrue(ex.getStatusCode().value() == 404);
        }
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
