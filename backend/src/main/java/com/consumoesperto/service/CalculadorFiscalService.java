package com.consumoesperto.service;

import com.consumoesperto.dto.BaseContrachequeFiscalDTO;
import com.consumoesperto.dto.ResultadoDecimoTerceiroDTO;
import com.consumoesperto.model.ContrachequeImportado;
import com.consumoesperto.model.RendaConfig;
import com.consumoesperto.model.TipoRecebimento13;
import com.consumoesperto.repository.ContrachequeImportadoRepository;
import com.consumoesperto.repository.RendaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Cálculos fiscais/CLT com base no último contracheque (ou renda configurada).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalculadorFiscalService {

    private static final BigDecimal CINQUENTA_PORCENTO = new BigDecimal("0.50");
    private static final int ESCALA = 2;
    private static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_UP;

    private final ContrachequeImportadoRepository contrachequeImportadoRepository;
    private final RendaConfigRepository rendaConfigRepository;

    @Transactional(readOnly = true)
    public Optional<BaseContrachequeFiscalDTO> obterBaseContracheque(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }

        List<ContrachequeImportado> confirmados =
            contrachequeImportadoRepository.findByUsuarioIdAndStatusOrderByDataCriacaoDesc(
                usuarioId, ContrachequeImportado.Status.CONFIRMADO);
        if (!confirmados.isEmpty()) {
            ContrachequeImportado cc = confirmados.get(0);
            BigDecimal bruto = nz(cc.getSalarioBruto());
            BigDecimal liquido = nz(cc.getSalarioLiquido());
            if (bruto.compareTo(BigDecimal.ZERO) > 0 && liquido.compareTo(BigDecimal.ZERO) > 0) {
                return Optional.of(BaseContrachequeFiscalDTO.builder()
                    .salarioBruto(scale(bruto))
                    .salarioLiquido(scale(liquido))
                    .descontosImposto(scale(bruto.subtract(liquido)))
                    .mesReferencia(cc.getMes())
                    .anoReferencia(cc.getAno())
                    .fonte("contracheque")
                    .estimado(false)
                    .build());
            }
        }

        return rendaConfigRepository.findByUsuarioId(usuarioId)
            .filter(rc -> nz(rc.getSalarioBruto()).compareTo(BigDecimal.ZERO) > 0)
            .map(rc -> {
                BigDecimal bruto = nz(rc.getSalarioBruto());
                BigDecimal liquido = nz(rc.getSalarioLiquido());
                if (liquido.compareTo(BigDecimal.ZERO) <= 0) {
                    liquido = bruto;
                }
                return BaseContrachequeFiscalDTO.builder()
                    .salarioBruto(scale(bruto))
                    .salarioLiquido(scale(liquido))
                    .descontosImposto(scale(bruto.subtract(liquido)))
                    .fonte("renda_config")
                    .estimado(true)
                    .build();
            });
    }

    /**
     * 13º — regras CLT:
     * <ul>
     *   <li>Líquido total estimado = líquido mensal do contracheque (descontos tributários completos).</li>
     *   <li>1ª parcela (duas parcelas): exatamente 50% do bruto, sem qualquer desconto.</li>
     *   <li>2ª parcela: líquido total − 1ª parcela (retenções acumuladas na 2ª).</li>
     *   <li>Parcela única: líquido total estimado no mês configurado.</li>
     * </ul>
     */
    public ResultadoDecimoTerceiroDTO calcularDecimoTerceiro(BaseContrachequeFiscalDTO base, TipoRecebimento13 tipo) {
        if (base == null || tipo == null) {
            return ResultadoDecimoTerceiroDTO.builder().build();
        }
        BigDecimal bruto = nz(base.getSalarioBruto());
        BigDecimal liquidoMensal = nz(base.getSalarioLiquido());
        if (bruto.compareTo(BigDecimal.ZERO) <= 0 || liquidoMensal.compareTo(BigDecimal.ZERO) <= 0) {
            return ResultadoDecimoTerceiroDTO.builder().build();
        }

        BigDecimal liquido13 = scale(liquidoMensal);

        if (tipo == TipoRecebimento13.PARCELA_UNICA) {
            return ResultadoDecimoTerceiroDTO.builder()
                .liquidoTotalEstimado(liquido13)
                .parcelaUnicaLiquida(liquido13)
                .build();
        }

        BigDecimal primeira = scale(bruto.multiply(CINQUENTA_PORCENTO));
        BigDecimal segunda = scale(liquido13.subtract(primeira));
        if (segunda.compareTo(BigDecimal.ZERO) < 0) {
            log.warn(
                "CalculadorFiscal: 2ª parcela do 13º negativa (bruto={}, liquido={}, 1ª={}) — ajustando para zero.",
                bruto, liquido13, primeira);
            segunda = BigDecimal.ZERO.setScale(ESCALA, ARREDONDAMENTO);
        }

        return ResultadoDecimoTerceiroDTO.builder()
            .liquidoTotalEstimado(liquido13)
            .primeiraParcelaBruta(primeira)
            .segundaParcelaLiquida(segunda)
            .build();
    }

    public BigDecimal calcularRestituicaoIr(BigDecimal valorConfigurado) {
        return scale(nz(valorConfigurado));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(ESCALA, ARREDONDAMENTO);
    }
}
