package com.consumoesperto.service;

import com.consumoesperto.dto.motor.MotorFinanceiroInteligenteDTO;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioPerfilComportamental;
import com.consumoesperto.repository.UsuarioPerfilComportamentalRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.motor.AdvisorInvestimentoEngine;
import com.consumoesperto.service.motor.ForecastProbabilisticoEngine;
import com.consumoesperto.service.motor.MetaInteligenteEngine;
import com.consumoesperto.service.motor.MotorFinanceiroColetaService;
import com.consumoesperto.service.motor.MotorFinanceiroSnapshot;
import com.consumoesperto.service.motor.PerfilComportamentalEngine;
import com.consumoesperto.service.motor.ScoreExplicavelEngine;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MotorFinanceiroService {

    private final MotorFinanceiroColetaService coletaService;
    private final UsuarioPerfilComportamentalRepository perfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final OpenAiService openAiService;

    @Transactional(readOnly = true)
    public MotorFinanceiroInteligenteDTO calcular(Long usuarioId, boolean incluirNarrativaIa) {
        MotorFinanceiroSnapshot snapshot = coletaService.coletar(usuarioId);
        BigDecimal gastoLazer = coletaService.gastoLazerMedioMensal(usuarioId);

        PerfilComportamentalEngine.Resultado perfil = PerfilComportamentalEngine.classificar(snapshot);
        ForecastProbabilisticoEngine.Resultado forecast = ForecastProbabilisticoEngine.calcular(snapshot);
        ScoreExplicavelEngine.Resultado score = ScoreExplicavelEngine.calcular(snapshot);
        List<MetaInteligenteEngine.MetaResultado> metas = MetaInteligenteEngine.analisar(snapshot, gastoLazer);
        AdvisorInvestimentoEngine.Recomendacao advisor = AdvisorInvestimentoEngine.recomendar(
            perfil.perfil(), snapshot);

        MotorFinanceiroInteligenteDTO dto = montarDto(snapshot, perfil, forecast, score, metas, advisor);
        dto.setCalculadoEm(AppTimeZone.agora());

        if (incluirNarrativaIa) {
            dto.setNarrativaIa(narrar(usuarioId, dto));
        }
        return dto;
    }

    @Transactional
    public MotorFinanceiroInteligenteDTO calcularEPersistirPerfil(Long usuarioId, boolean incluirNarrativaIa) {
        MotorFinanceiroInteligenteDTO dto = calcular(usuarioId, incluirNarrativaIa);
        persistirPerfilSeMudou(usuarioId, dto.getPerfilComportamental());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historicoPerfil(Long usuarioId) {
        return perfilRepository.findTop10ByUsuarioIdOrderByCalculadoEmDesc(usuarioId).stream()
            .map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("perfil", p.getPerfil());
                m.put("confiancaPct", p.getConfiancaPct());
                m.put("perfilAnterior", p.getPerfilAnterior());
                m.put("calculadoEm", p.getCalculadoEm());
                return m;
            })
            .collect(Collectors.toList());
    }

    private void persistirPerfilSeMudou(Long usuarioId, MotorFinanceiroInteligenteDTO.PerfilComportamentalDTO perfilDto) {
        if (perfilDto == null) {
            return;
        }
        String perfilAtual = perfilDto.getPerfil();
        var ultimo = perfilRepository.findTopByUsuarioIdOrderByCalculadoEmDesc(usuarioId);
        if (ultimo.isPresent() && perfilAtual.equals(ultimo.get().getPerfil())) {
            return;
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            return;
        }
        UsuarioPerfilComportamental reg = new UsuarioPerfilComportamental();
        reg.setUsuario(usuario);
        reg.setPerfil(perfilAtual);
        reg.setConfiancaPct(perfilDto.getConfiancaPct());
        reg.setPerfilAnterior(ultimo.map(UsuarioPerfilComportamental::getPerfil).orElse(null));
        reg.setCalculadoEm(AppTimeZone.agora());
        perfilRepository.save(reg);
    }

    private MotorFinanceiroInteligenteDTO montarDto(
        MotorFinanceiroSnapshot s,
        PerfilComportamentalEngine.Resultado perfil,
        ForecastProbabilisticoEngine.Resultado forecast,
        ScoreExplicavelEngine.Resultado score,
        List<MetaInteligenteEngine.MetaResultado> metas,
        AdvisorInvestimentoEngine.Recomendacao advisor
    ) {
        MotorFinanceiroInteligenteDTO dto = new MotorFinanceiroInteligenteDTO();

        MotorFinanceiroInteligenteDTO.PerfilComportamentalDTO pDto =
            new MotorFinanceiroInteligenteDTO.PerfilComportamentalDTO();
        pDto.setPerfil(perfil.perfil().name());
        pDto.setConfiancaPct(perfil.confiancaPct());
        Map<String, Integer> pontos = new LinkedHashMap<>();
        perfil.pontuacaoPorPerfil().forEach((k, v) -> pontos.put(k.name(), v));
        pDto.setPontuacaoPorPerfil(pontos);
        dto.setPerfilComportamental(pDto);

        MotorFinanceiroInteligenteDTO.ForecastInteligenteDTO fDto =
            new MotorFinanceiroInteligenteDTO.ForecastInteligenteDTO();
        fDto.setSaldoPrevisto(s.saldoProjetadoFimMes());
        fDto.setDespesasPrevistas(s.despesasPrevistasMes());
        fDto.setReceitasPrevistas(s.receitasPrevistasMes());
        fDto.setChanceMesPositivoPct(forecast.chanceMesPositivoPct());
        fDto.setChanceChequeEspecialPct(forecast.chanceChequeEspecialPct());
        fDto.setChanceEstourarOrcamentoPct(forecast.chanceEstourarOrcamentoPct());
        fDto.setExplicacaoDeterministica(forecast.explicacaoDeterministica());
        dto.setForecastInteligente(fDto);

        MotorFinanceiroInteligenteDTO.ScoreExplicavelDTO sDto =
            new MotorFinanceiroInteligenteDTO.ScoreExplicavelDTO();
        sDto.setScoreTotal(score.scoreTotal());
        sDto.setComponentes(score.componentes().stream().map(c -> {
            MotorFinanceiroInteligenteDTO.ComponenteScoreDTO cd =
                new MotorFinanceiroInteligenteDTO.ComponenteScoreDTO();
            cd.setNome(c.nome());
            cd.setPontos(c.pontos());
            cd.setMaximo(c.maximo());
            cd.setDetalhe(c.detalhe());
            cd.setComoRecuperar(c.comoRecuperar());
            return cd;
        }).collect(Collectors.toList()));
        dto.setScoreExplicavel(sDto);

        dto.setMetasInteligentes(metas.stream().map(m -> {
            MotorFinanceiroInteligenteDTO.MetaInteligenteDTO md =
                new MotorFinanceiroInteligenteDTO.MetaInteligenteDTO();
            md.setMetaId(m.metaId());
            md.setDescricao(m.descricao());
            md.setProbabilidadeSucessoPct(m.probabilidadeSucessoPct());
            md.setRitmoAtualMensal(m.ritmoAtualMensal());
            md.setRitmoNecessarioMensal(m.ritmoNecessarioMensal());
            md.setDiferencaMensal(m.diferencaMensal());
            md.setRecomendacaoDeterministica(m.recomendacaoDeterministica());
            return md;
        }).collect(Collectors.toList()));

        MotorFinanceiroInteligenteDTO.AdvisorInvestimentoDTO aDto =
            new MotorFinanceiroInteligenteDTO.AdvisorInvestimentoDTO();
        aDto.setPerfilInvestidor(advisor.perfilInvestidor().name());
        aDto.setProdutosCompativeis(advisor.produtosCompativeis().stream()
            .map(Enum::name).collect(Collectors.toList()));
        aDto.setTextoDeterministico(advisor.textoDeterministico());
        aDto.setAvisoLegal(advisor.avisoLegal());
        dto.setAdvisorInvestimento(aDto);

        return dto;
    }

    private String narrar(Long usuarioId, MotorFinanceiroInteligenteDTO dto) {
        StringBuilder dados = new StringBuilder();
        if (dto.getPerfilComportamental() != null) {
            dados.append("Perfil: ").append(dto.getPerfilComportamental().getPerfil())
                .append(" (confiança ").append(dto.getPerfilComportamental().getConfiancaPct()).append("%)\n");
        }
        if (dto.getForecastInteligente() != null) {
            var f = dto.getForecastInteligente();
            dados.append("Forecast: mês positivo ").append(f.getChanceMesPositivoPct()).append("%, ")
                .append("cheque especial ").append(f.getChanceChequeEspecialPct()).append("%, ")
                .append("estourar orçamento ").append(f.getChanceEstourarOrcamentoPct()).append("%\n");
            dados.append("Explicação: ").append(f.getExplicacaoDeterministica()).append("\n");
        }
        if (dto.getScoreExplicavel() != null) {
            dados.append("Score total: ").append(dto.getScoreExplicavel().getScoreTotal()).append("/100\n");
        }

        String system = "Você é o J.A.R.V.I.S. Os cálculos JÁ FORAM FEITOS — NÃO recalcule nem invente números.\n"
            + "Transforme os dados em um resumo claro em português (3–5 frases), tom direto e educativo.\n"
            + "Não dê recomendação personalizada de investimento.";
        String fallback = dto.getForecastInteligente() != null
            ? dto.getForecastInteligente().getExplicacaoDeterministica()
            : "Resumo financeiro calculado com base nos seus dados cadastrados.";
        return openAiService.gerarTexto(usuarioId, system,
            "DADOS CALCULADOS (não recalcule, apenas redija):\n" + dados, fallback);
    }
}
