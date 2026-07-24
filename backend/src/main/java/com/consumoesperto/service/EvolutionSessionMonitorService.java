package com.consumoesperto.service;

import com.consumoesperto.dto.EvolutionHealthDTO;
import com.consumoesperto.dto.EvolutionSessaoDetalheDTO;
import com.consumoesperto.util.EvolutionUrlSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionSessionMonitorService {

    private final EvolutionSessionMetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    public EvolutionHealthDTO obterHealth(boolean incluirDetalhe) {
        List<EvolutionInstanceRow> instances = fetchInstancesFromEvolution();
        int ativas = 0;
        int desconectadas = 0;
        List<EvolutionSessaoDetalheDTO> detalhes = new ArrayList<>();
        List<String> instaveis = new ArrayList<>();

        for (EvolutionInstanceRow row : instances) {
            if (EvolutionSessionMetricsService.isConnectedStatus(row.connectionStatus())) {
                ativas++;
                metricsService.recordConnected(row.instanceName());
            } else {
                desconectadas++;
            }

            EvolutionSessionMetricsService.InstanceSnapshot snap =
                metricsService.snapshot(row.instanceName());

            if (incluirDetalhe) {
                EvolutionSessaoDetalheDTO d = new EvolutionSessaoDetalheDTO();
                d.setInstancia(row.instanceName());
                d.setStatus(row.connectionStatus());
                d.setAtiva(EvolutionSessionMetricsService.isConnectedStatus(row.connectionStatus()));
                d.setUptimeSegundos(snap.uptimeSegundos());
                d.setMemoriaEstimadaMb(snap.memoriaEstimadaMb());
                d.setMensagensEnviadas(snap.mensagensEnviadas());
                d.setMensagensRecebidas(snap.mensagensRecebidas());
                d.setMensagensEnviadasHoje(snap.mensagensEnviadasHoje());
                d.setMensagensRecebidasHoje(snap.mensagensRecebidasHoje());
                d.setDesconexoesHoje(snap.desconexoesHoje());
                d.setReconexoesHoje(snap.reconexoesHoje());
                d.setFalhasHoje(snap.falhasHoje());
                d.setLatenciaMediaMs(snap.latenciaMediaMs());
                d.setLatenciaP95Ms(snap.latenciaP95Ms());
                d.setIdadeUltimaAtividadeSegundos(snap.idadeUltimaAtividadeSegundos());
                d.setInstavel(snap.instavel());
                d.setMotivoInstabilidade(snap.motivoInstabilidade());
                detalhes.add(d);
            }
            if (snap.instavel()) {
                instaveis.add(row.instanceName());
            }
        }

        EvolutionHealthDTO dto = new EvolutionHealthDTO();
        dto.setSessoesAtivas(ativas);
        dto.setSessoesDesconectadas(desconectadas);
        dto.setMensagensHoje(metricsService.totalMensagensHoje());
        dto.setReconexoesHoje(metricsService.totalReconexoesHoje());
        dto.setFalhasHoje(metricsService.totalFalhasHoje());
        dto.setLatenciaMediaMs(metricsService.latenciaMediaGlobalMs());
        dto.setLatenciaP95Ms(metricsService.latenciaP95GlobalMs());
        dto.setColetadoEm(Instant.now());
        if (incluirDetalhe) {
            dto.setSessoes(detalhes);
            dto.setSessoesInstaveis(instaveis);
        }
        return dto;
    }

    private List<EvolutionInstanceRow> fetchInstancesFromEvolution() {
        List<EvolutionInstanceRow> out = new ArrayList<>();
        if (evolutionUrl == null || evolutionUrl.isBlank()
            || evolutionApiKey == null || evolutionApiKey.isBlank()) {
            log.debug("[EvolutionMonitor] Evolution API não configurada — health só com métricas locais.");
            return out;
        }
        try {
            long start = System.nanoTime();
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/fetchInstances");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", evolutionApiKey.trim());
            String body = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            ).getBody();
            metricsService.recordApiLatency("_global_", (System.nanoTime() - start) / 1_000_000L);
            if (body == null || body.isBlank()) {
                return out;
            }
            JsonNode root = objectMapper.readTree(body);
            collectInstances(root, out);
        } catch (Exception e) {
            metricsService.recordFetchFailure();
            log.warn("[EvolutionMonitor] Falha fetchInstances: {}", e.getMessage());
        }
        return out;
    }

    private void collectInstances(JsonNode node, List<EvolutionInstanceRow> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectInstances(item, out);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        Optional<EvolutionInstanceRow> row = parseInstanceRow(node);
        row.ifPresent(out::add);

        node.fields().forEachRemaining(e -> {
            if (!"instance".equals(e.getKey()) && !"name".equals(e.getKey())) {
                collectInstances(e.getValue(), out);
            }
        });
    }

    private Optional<EvolutionInstanceRow> parseInstanceRow(JsonNode node) {
        String name = firstNonBlank(
            node.path("name").asText(""),
            node.path("instanceName").asText(""),
            node.path("instance").path("instanceName").asText("")
        );
        if (name.isBlank()) {
            return Optional.empty();
        }
        String status = firstNonBlank(
            node.path("connectionStatus").asText(""),
            node.path("state").asText(""),
            node.path("instance").path("state").asText(""),
            node.path("status").asText("")
        );
        if (status.isBlank()) {
            status = "unknown";
        }
        return Optional.of(new EvolutionInstanceRow(name.trim(), status.trim()));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private record EvolutionInstanceRow(String instanceName, String connectionStatus) {}
}
