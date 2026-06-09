package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.service.PdfTextExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaturaPdfLayoutDetector {

    private final PdfTextExtractionService pdfTextExtractionService;
    private final List<FaturaPdfLayoutStrategy> strategies;
    private final GenericoFaturaPdfLayoutStrategy generico;

    public FaturaPdfLayoutStrategy detectar(byte[] pdfBytes) {
        String texto = pdfTextExtractionService.extrairTexto(pdfBytes);
        return detectarTexto(texto);
    }

    public FaturaPdfLayoutStrategy detectarTexto(String textoPdf) {
        String norm = FaturaPdfLayoutSupport.norm(textoPdf);
        if (norm.length() < 40) {
            log.warn("Texto PDF muito curto para detecção de layout ({} chars); usando genérico.", norm.length());
            return generico;
        }
        FaturaPdfLayoutStrategy escolhido = strategies.stream()
            .filter(s -> !(s instanceof GenericoFaturaPdfLayoutStrategy))
            .sorted(Comparator.comparingInt(FaturaPdfLayoutStrategy::prioridade).reversed())
            .filter(s -> s.reconhece(norm))
            .findFirst()
            .orElse(generico);
        log.info("Layout de fatura detectado: {} (prioridade={})", escolhido.layout(), escolhido.prioridade());
        return escolhido;
    }
}
