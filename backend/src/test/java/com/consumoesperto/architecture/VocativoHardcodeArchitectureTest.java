package com.consumoesperto.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Impede regressão: literais de saudação/vocativo com gênero só podem existir em
 * {@link com.consumoesperto.service.jarvis.TratamentoUsuarioService} e no fluxo de coleta WhatsApp.
 */
class VocativoHardcodeArchitectureTest {

    private static final Set<String> ALLOWED_FILES = Set.of(
        "TratamentoUsuarioService.java",
        "JarvisTratamentoWhatsappService.java"
    );

    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");

    private static final Pattern BANNED_IN_LITERAL = Pattern.compile(
        "(?i)\\b(senhor|senhora|chefe|chefa|bem-vind[oa])\\b");

    @Test
    void nenhumVocativoComGeneroForaDoHubCentral() throws IOException {
        Path mainJava = Path.of("src", "main", "java");
        if (!Files.isDirectory(mainJava)) {
            mainJava = Path.of("backend", "src", "main", "java");
        }
        List<String> violacoes = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(mainJava)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !ALLOWED_FILES.contains(p.getFileName().toString()))
                .forEach(p -> scanFile(p, violacoes));
        }

        if (!violacoes.isEmpty()) {
            fail("Literais de vocativo/saudação com gênero fora do TratamentoUsuarioService:\n"
                + String.join("\n", violacoes));
        }
    }

    private static void scanFile(Path file, List<String> violacoes) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue;
                }
                if (trimmed.matches("case\\s+\"(senhor|senhora)\".*")) {
                    continue;
                }
                Matcher m = STRING_LITERAL.matcher(line);
                while (m.find()) {
                    String literal = m.group();
                    if (BANNED_IN_LITERAL.matcher(literal).find()) {
                        violacoes.add(file + ":" + (i + 1) + " → " + trimmed);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
