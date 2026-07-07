package com.consumoesperto.service.jarvis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JarvisFastPathGoldenSetTest {

    private JarvisFastPathParser parser;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<String> failures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        parser = new JarvisFastPathParser();
        failures = new ArrayList<>();
    }

    @Test
    void fastPathGoldenSetAcuraciaMinima() throws Exception {
        int total = 0;
        int passed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/jarvis-golden-set.jsonl"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                total++;
                JsonNode spec = objectMapper.readTree(line);
                if (evaluate(spec)) {
                    passed++;
                }
            }
        }
        double accuracy = total == 0 ? 0 : (passed * 100.0 / total);
        if (!failures.isEmpty()) {
            fail(String.format(Locale.US,
                "Fast-path golden set: %.1f%% (%d/%d). Falhas:%n%s",
                accuracy, passed, total, String.join(System.lineSeparator(), failures)));
        }
        assertTrue(accuracy >= 85.0, "Acurácia mínima 85% — obteve " + accuracy + "%");
    }

    private boolean evaluate(JsonNode spec) {
        String input = spec.path("input").asText("");
        if (Boolean.TRUE.equals(spec.path("ambiguous").asBoolean(false))) {
            if (!parser.isAmbiguous(input)) {
                failures.add("[" + input + "] deveria ser ambíguo");
                return false;
            }
            return true;
        }
        if (Boolean.TRUE.equals(spec.path("noParse").asBoolean(false))) {
            if (parser.tryParse(input).isPresent()) {
                failures.add("[" + input + "] não deveria parsear no fast-path");
                return false;
            }
            return true;
        }
        Optional<JsonNode> parsed = parser.tryParse(input);
        if (parsed.isEmpty()) {
            failures.add("[" + input + "] deveria parsear");
            return false;
        }
        JsonNode cmd = parsed.get();
        String expectedAction = spec.path("action").asText("");
        if (!expectedAction.equals(cmd.path("action").asText())) {
            failures.add("[" + input + "] action esperada " + expectedAction + " got " + cmd.path("action").asText());
            return false;
        }
        if (spec.has("amount")) {
            BigDecimal expected = new BigDecimal(spec.path("amount").asText());
            BigDecimal got = cmd.path("amount").decimalValue();
            if (expected.compareTo(got) != 0) {
                failures.add("[" + input + "] amount esperado " + expected + " got " + got);
                return false;
            }
        }
        if (spec.has("descriptionContains")) {
            String needle = spec.path("descriptionContains").asText("").toLowerCase(Locale.ROOT);
            String desc = cmd.path("description").asText("").toLowerCase(Locale.ROOT);
            if (!desc.contains(needle)) {
                failures.add("[" + input + "] descrição deveria conter «" + needle + "» — got «" + desc + "»");
                return false;
            }
        }
        if (spec.has("minConf")) {
            double min = spec.path("minConf").asDouble();
            if (cmd.path("confianca").asDouble(0) < min) {
                failures.add("[" + input + "] confiança abaixo de " + min);
                return false;
            }
        }
        return true;
    }
}
