package com.consumoesperto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CnpjEnrichmentService {

    private static final String BRASIL_API_CNPJ = "https://brasilapi.com.br/api/cnpj/v1/";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Optional<CnpjEnrichment> enrich(String rawCnpj) {
        String digits = normalizeCnpj(rawCnpj);
        if (digits.length() != 14) {
            return Optional.empty();
        }
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(BRASIL_API_CNPJ + digits, String.class);
            String body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(body);
            String razao = text(root, "razao_social");
            String fantasia = text(root, "nome_fantasia");
            String logradouro = text(root, "logradouro");
            String numero = text(root, "numero");
            String bairro = text(root, "bairro");
            String municipio = text(root, "municipio");
            String uf = text(root, "uf");
            String cep = text(root, "cep");
            String nomeExibicao = !fantasia.isBlank() ? fantasia : razao;
            String endereco = montarEndereco(logradouro, numero, bairro, municipio, uf, cep);
            if (nomeExibicao.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new CnpjEnrichment(digits, nomeExibicao, razao, fantasia, endereco));
        } catch (HttpClientErrorException.NotFound e) {
            log.info("CNPJ não encontrado na BrasilAPI: {}", digits);
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Falha ao consultar BrasilAPI para CNPJ {}: {}", digits, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Erro ao interpretar resposta BrasilAPI: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public String normalizeCnpj(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    private static String text(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return "";
        }
        return root.get(field).asText("").trim();
    }

    private static String montarEndereco(String logradouro, String numero, String bairro, String municipio, String uf, String cep) {
        StringBuilder sb = new StringBuilder();
        if (!logradouro.isBlank()) {
            sb.append(logradouro);
        }
        if (!numero.isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(numero);
        }
        if (!bairro.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(bairro);
        }
        if (!municipio.isBlank() || !uf.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(municipio);
            if (!uf.isBlank()) {
                sb.append("/").append(uf);
            }
        }
        if (!cep.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" CEP ");
            }
            sb.append(cep);
        }
        return sb.toString().trim();
    }

    public record CnpjEnrichment(
        String cnpjDigits,
        String nomeExibicao,
        String razaoSocial,
        String nomeFantasia,
        String enderecoFormatado
    ) {}
}
