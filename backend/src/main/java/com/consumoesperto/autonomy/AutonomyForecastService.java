package com.consumoesperto.autonomy;

import com.consumoesperto.dto.ForecastFinanceiroDTO;
import com.consumoesperto.dto.ProjecaoMesResumoDTO;
import com.consumoesperto.dto.SerieProjecaoSafraDTO;
import com.consumoesperto.service.ForecastFinanceiroService;
import com.consumoesperto.service.SaldoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Horizontes 30/60/90 — não unificados nesta versão (PARCIAL).
 * 30d = ForecastFinanceiroService; 60/90 = SaldoService safra.
 * E.D.I.T.H. só explica.
 */
@Service
@RequiredArgsConstructor
public class AutonomyForecastService {

    private final ForecastFinanceiroService forecastFinanceiroService;
    private final SaldoService saldoService;

    @Transactional(readOnly = true)
    public Map<String, Object> horizontes(Long usuarioId) {
        ForecastFinanceiroDTO mes = forecastFinanceiroService.calcularParaPainel(usuarioId);
        Map<String, Object> d30 = ponto(mes.getSaldoProjetado(), mes.getGastoProjetado(), mes.getNivelRisco());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("d30", d30);
        try {
            SerieProjecaoSafraDTO safra = saldoService.calcularProjecaoSafraDto(usuarioId, 3);
            List<ProjecaoMesResumoDTO> meses = safra.getMeses() == null ? List.of() : safra.getMeses();
            out.put("d60", meses.size() > 1 ? pontoSafra(meses.get(1)) : d30);
            out.put("d90", meses.size() > 2 ? pontoSafra(meses.get(2)) : out.get("d60"));
        } catch (Exception e) {
            out.put("d60", d30);
            out.put("d90", d30);
        }
        out.put("cashflowRisco", mes.getSaldoProjetado() != null
            && mes.getSaldoProjetado().compareTo(BigDecimal.ZERO) < 0);
        return out;
    }

    private static Map<String, Object> ponto(BigDecimal saldo, BigDecimal gasto, String risco) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("saldoProjetado", saldo);
        m.put("gastoProjetado", gasto);
        m.put("risco", risco);
        return m;
    }

    private static Map<String, Object> pontoSafra(ProjecaoMesResumoDTO mes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("competencia", mes.getCompetencia());
        m.put("saldoProjetado", mes.getSaldoProjetadoFimMes());
        m.put("despesasPrevistas", mes.getDespesasPrevistas());
        m.put("receitasPrevistas", mes.getReceitasPrevistas());
        return m;
    }
}
