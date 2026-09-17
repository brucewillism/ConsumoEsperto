package com.consumoesperto.autonomy;

import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.service.FaturaService;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.MoedaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LiveInvoiceProjectionService {

    private final FaturaService faturaService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> atuais(Long usuarioId) {
        List<FaturaDTO> faturas = faturaService.buscarPorUsuarioId(usuarioId);
        LocalDate hoje = AppTimeZone.hoje();
        List<Map<String, Object>> out = new ArrayList<>();
        for (FaturaDTO f : faturas) {
            if (Boolean.TRUE.equals(f.getPaga())
                || (f.getStatus() != null && "PAGA".equalsIgnoreCase(f.getStatus()))
                || (f.getStatusFatura() != null && "PAGA".equalsIgnoreCase(f.getStatusFatura().name()))) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("cartaoNome", f.getNomeCartao());
            BigDecimal confirmado = MoedaUtil.nz(f.getValorConfirmado() != null ? f.getValorConfirmado() : f.getValorTotal());
            BigDecimal pendente = MoedaUtil.nz(f.getValorPendente());
            BigDecimal projetado = f.getValorProjetado() != null
                ? f.getValorProjetado()
                : confirmado.add(pendente);
            m.put("valorConfirmado", confirmado);
            m.put("valorPago", f.getValorPago());
            m.put("valorPendente", pendente);
            m.put("valorProjetado", projetado);
            m.put("dataVencimento", f.getDataVencimento());
            m.put("dataFechamento", f.getDataFechamento());
            long dias = 0;
            if (f.getDataFechamento() != null) {
                dias = ChronoUnit.DAYS.between(hoje, f.getDataFechamento().toLocalDate());
            } else if (f.getDataVencimento() != null) {
                dias = ChronoUnit.DAYS.between(hoje, f.getDataVencimento().toLocalDate());
            }
            m.put("diasAteFechamento", dias);
            out.add(m);
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }
}
