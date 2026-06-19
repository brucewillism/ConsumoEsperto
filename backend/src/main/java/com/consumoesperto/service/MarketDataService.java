package com.consumoesperto.service;

import com.consumoesperto.dto.MarketIndicatorsDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

/**
 * Oráculo externo — Selic/IPCA (BCB) e USD/BRL (AwesomeAPI), com timeout curto para não bloquear o Sentinela.
 */
@Service
@Slf4j
public class MarketDataService {

    /** Série BCB SGS — taxa média de juros crédito PF consignado público (% a.a.). */
    private static final String SERIE_CONSIGNADO_BCB = "25497";
    private static final String SERIE_SELIC_BCB = "432";
    /** Spread estimado sobre Selic quando a série 25497 estiver indisponível. */
    private static final BigDecimal SPREAD_CONSIGNADO_FALLBACK_AA = new BigDecimal("8");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MarketDataService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(3))
            .build();
        this.objectMapper = objectMapper;
    }

    public MarketIndicatorsDTO buscarIndicadoresResiliente() {
        MarketIndicatorsDTO.MarketIndicatorsDTOBuilder b = MarketIndicatorsDTO.builder().dadosParciais(false);
        boolean parcial = false;
        BigDecimal selic = null;
        try {
            selic = bcbUltimoValorNumerico(SERIE_SELIC_BCB);
            b.selicAa(selic);
        } catch (Exception e) {
            log.debug("BCB Selic indisponível: {}", e.getMessage());
            parcial = true;
        }
        b.taxaMediaConsignadoAa(resolverTaxaConsignado(selic));
        BigDecimal ipca = null;
        for (String codigo : List.of("433", "13522", "4449")) {
            try {
                ipca = bcbUltimoValorNumerico(codigo);
                if (ipca != null && ipca.abs().compareTo(new BigDecimal("0.001")) > 0) {
                    break;
                }
            } catch (Exception e) {
                log.debug("BCB IPCA série {}: {}", codigo, e.getMessage());
            }
        }
        if (ipca == null) {
            parcial = true;
        } else {
            b.ipcaMes(ipca);
        }
        BigDecimal usd = null;
        try {
            usd = awesomeDolar();
            b.dolarBrl(usd);
        } catch (Exception e) {
            log.debug("AwesomeAPI USD: {}", e.getMessage());
            parcial = true;
        }
        b.fonteResumo("BCB SGS · AwesomeAPI (USD/BRL)");
        b.dadosParciais(parcial || usd == null);
        return b.build();
    }

    /**
     * Fator multiplicador do burn diário para categorias de consumo recorrente (réplica conservadora do IPCA mensal).
     * Limite superior ~1,2% para evitar distorção extrema.
     */
    public BigDecimal fatorCorrecaoConsumoRecorrente(MarketIndicatorsDTO indicadores) {
        if (indicadores == null || indicadores.getIpcaMes() == null) {
            return BigDecimal.ONE;
        }
        BigDecimal m = indicadores.getIpcaMes().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        if (m.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal delta = m.multiply(new BigDecimal("0.40"));
        delta = delta.min(new BigDecimal("0.012"));
        return BigDecimal.ONE.add(delta).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Taxa média consignado público (% a.a.) — série 25497; fallback Selic + 8% a.a.
     * Nunca lança exceção — retorna referência conservadora se tudo falhar.
     */
    public BigDecimal getTaxaMediaConsignadoResiliente() {
        BigDecimal selic = null;
        try {
            BigDecimal consignado = bcbUltimoValorNumerico(SERIE_CONSIGNADO_BCB);
            if (consignado != null && consignado.compareTo(BigDecimal.ZERO) > 0) {
                return consignado.setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.debug("BCB consignado série {} indisponível: {}", SERIE_CONSIGNADO_BCB, e.getMessage());
        }
        try {
            selic = bcbUltimoValorNumerico(SERIE_SELIC_BCB);
        } catch (Exception e) {
            log.debug("BCB Selic fallback consignado: {}", e.getMessage());
        }
        return resolverTaxaConsignado(selic);
    }

    private BigDecimal resolverTaxaConsignado(BigDecimal selicCache) {
        try {
            BigDecimal consignado = bcbUltimoValorNumerico(SERIE_CONSIGNADO_BCB);
            if (consignado != null && consignado.compareTo(BigDecimal.ZERO) > 0) {
                return consignado.setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.debug("BCB consignado: {}", e.getMessage());
        }
        BigDecimal selic = selicCache;
        if (selic == null) {
            try {
                selic = bcbUltimoValorNumerico(SERIE_SELIC_BCB);
            } catch (Exception ignored) {
                // fallback final abaixo
            }
        }
        if (selic != null && selic.compareTo(BigDecimal.ZERO) > 0) {
            return selic.add(SPREAD_CONSIGNADO_FALLBACK_AA).setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal("25.00");
    }

    private BigDecimal bcbUltimoValorNumerico(String codigoSerie) throws Exception {
        String url = "https://api.bcb.gov.br/dados/serie/bcdata.sgs." + codigoSerie + "/dados/ultimos/1?formato=json";
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        String body = resp.getBody();
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode arr = objectMapper.readTree(body);
        if (!arr.isArray() || arr.size() == 0) {
            return null;
        }
        String valor = arr.get(0).path("valor").asText("").trim().replace(",", ".");
        if (valor.isBlank()) {
            return null;
        }
        return new BigDecimal(valor);
    }

    private BigDecimal awesomeDolar() throws Exception {
        String url = "https://economia.awesomeapi.com.br/json/last/USD-BRL";
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        JsonNode n = root.path("USDBRL");
        String bid = n.path("bid").asText("").replace(",", ".");
        if (bid.isBlank()) {
            return null;
        }
        return new BigDecimal(bid).setScale(4, RoundingMode.HALF_UP);
    }
}
