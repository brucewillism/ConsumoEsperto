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
        try {
            BigDecimal selic = bcbUltimoValorNumerico("432");
            b.selicAa(selic);
        } catch (Exception e) {
            log.debug("BCB Selic indisponível: {}", e.getMessage());
            parcial = true;
        }
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
