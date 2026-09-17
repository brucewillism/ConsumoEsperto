package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.dto.DisponibilidadeRealDTO;
import com.consumoesperto.service.PrevisaoFluxoCaixaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safe-to-spend determinístico. E.D.I.T.H. não calcula o valor.
 */
@Service
@RequiredArgsConstructor
public class SafeToSpendService {

    private final PrevisaoFluxoCaixaService previsaoFluxoCaixaService;
    private final FinancialAutonomyProperties properties;

    @Transactional(readOnly = true)
    public Map<String, Object> calcular(Long usuarioId) {
        DisponibilidadeRealDTO d = previsaoFluxoCaixaService.calcularDisponibilidadeReal(usuarioId);
        BigDecimal bruto = nz(d.getDisponivelAposObrigacoes());
        BigDecimal margin = properties.getSafetyMargin() == null
            ? BigDecimal.ZERO
            : properties.getSafetyMargin();
        BigDecimal safe = bruto.subtract(bruto.multiply(margin)).setScale(2, RoundingMode.HALF_UP);
        if (safe.signum() < 0) {
            safe = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saldo", d.getSaldoBancarioAtual());
        out.put("obrigacoes", d.getTotalObrigacoes());
        out.put("disponivelAposObrigacoes", bruto);
        out.put("margemSeguranca", margin);
        out.put("safeToSpend", safe);
        out.put("diasRestantesNoMes", d.getDiasRestantesNoMes());
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
