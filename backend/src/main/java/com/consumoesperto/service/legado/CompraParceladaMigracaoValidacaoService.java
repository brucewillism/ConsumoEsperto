package com.consumoesperto.service.legado;

import com.consumoesperto.repository.CompraParceladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pré-validação da migração CompraParcelada → transações parceladas.
 * Não altera dados; apenas reporta contagens e inconsistências.
 */
@Service
@RequiredArgsConstructor
public class CompraParceladaMigracaoValidacaoService {

    private final CompraParceladaRepository compraParceladaRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> relatorioPreMigracao(Long usuarioId) {
        var compras = compraParceladaRepository.findByCartaoCreditoUsuarioId(usuarioId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", compras.size());
        out.put("validos", compras.stream().filter(c -> c.getNumeroParcelas() != null && c.getNumeroParcelas() > 0).count());
        out.put("inconsistentes", compras.stream().filter(c -> c.getValorTotal() == null || c.getCartaoCredito() == null).count());
        out.put("mensagem", "Migração não executada — consulte docs/PLANO_CONVERSAO_COMPRA_PARCELADA.md");
        return out;
    }
}
