package com.consumoesperto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Payload único de {@code GET /api/dashboard}. Secções estáveis para Mensal e Geral.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardViewDTO {
    private String viewMode;
    private String periodo;
    @Builder.Default
    private Map<String, Object> cards = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Object> metricas = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Object> compromissos = new LinkedHashMap<>();
    @Builder.Default
    private List<Map<String, String>> insights = new ArrayList<>();
    @Builder.Default
    private List<Map<String, String>> alertas = new ArrayList<>();

    public static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    public static Map<String, String> line(String codigo, String titulo, String detalhe) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("titulo", titulo);
        m.put("detalhe", detalhe);
        return m;
    }

    public static Map<String, Object> cardItem(String id, String titulo, BigDecimal valor, String subtitulo, String sentido) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("titulo", titulo);
        m.put("valor", nz(valor));
        m.put("subtitulo", subtitulo);
        m.put("sentido", sentido);
        return m;
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
