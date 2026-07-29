package com.consumoesperto.fiscal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiscalTabelaSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void reset() {
        TabelaFiscalAnoRegistry.resetCacheForTests();
    }

    @Test
    void json2025Valido() throws Exception {
        JsonNode root = carregar("fiscal/tabelas/2025.json");
        assertDoesNotThrow(() -> FiscalTabelaSchemaValidator.validarArquivo(root, 2025));
    }

    @Test
    void json2026Valido() throws Exception {
        JsonNode root = carregar("fiscal/tabelas/2026.json");
        assertDoesNotThrow(() -> FiscalTabelaSchemaValidator.validarArquivo(root, 2026));
    }

    @Test
    void registryCarrega2025e2026() {
        assertTrue(TabelaFiscalAnoRegistry.obter(2025).isPresent());
        assertTrue(TabelaFiscalAnoRegistry.obter(2026).isPresent());
        var v2026 = TabelaFiscalAnoRegistry.obterVersao(2026, java.time.LocalDate.of(2026, 1, 1)).orElseThrow();
        assertNotNull(v2026.reducaoAdicional());
        assertEqualsTipo("EMPREGADO", v2026.tipoSeguradoInss().name());
    }

    @Test
    void anoDivergenteRejeita() throws Exception {
        JsonNode root = carregar("fiscal/tabelas/2026.json");
        assertThrows(IllegalStateException.class, () -> FiscalTabelaSchemaValidator.validarArquivo(root, 2025));
    }

    private static void assertEqualsTipo(String esperado, String obtido) {
        org.junit.jupiter.api.Assertions.assertEquals(esperado, obtido);
    }

    private JsonNode carregar(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return MAPPER.readTree(in);
        }
    }
}
