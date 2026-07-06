package com.consumoesperto.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.consumoesperto.config.MemoriaJarvisProperties;
import com.consumoesperto.model.OrigemConteudo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Ajustes finos, item 3 — falha no caminho assíncrono da captura não pode ser engolida:
 * exceção forçada produz log ERROR estruturado e falhas repetidas na última hora
 * disparam o alerta operacional (cooldown fica no próprio AlertaOperacionalService).
 */
@ExtendWith(MockitoExtension.class)
class MemoriaCapturaFalhaAlertaTest {

    private static final String TEXTO_PLANO = "vou gastar R$ 2.000,00 em julho com a cirurgia";

    @Mock private CerebroSemanticoService cerebroSemanticoService;
    @Mock private AlertaOperacionalService alertaOperacionalService;

    private MemoriaCapturaAutomaticaService service;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        service = new MemoriaCapturaAutomaticaService(
            cerebroSemanticoService, new MemoriaJarvisProperties(), alertaOperacionalService);
        logger = (Logger) LoggerFactory.getLogger(MemoriaCapturaAutomaticaService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void excecaoNaCaptura_produzLogErrorEstruturado() {
        doThrow(new IllegalStateException("boom"))
            .when(cerebroSemanticoService).gravarMemoria(anyLong(), anyString(), any(), any());

        service.capturarDeConversaAsync(7L, TEXTO_PLANO, null, OrigemConteudo.TEXTO_USUARIO);

        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("userId=7")
                && e.getFormattedMessage().contains("TEXTO_USUARIO")
                && e.getFormattedMessage().contains("IllegalStateException")),
            "falha na captura deve gerar log ERROR com usuário, origem e causa");
        // Sem conteúdo sensível da mensagem no log
        assertTrue(logs.list.stream()
                .noneMatch(e -> e.getFormattedMessage().contains("cirurgia")),
            "o log não deve vazar o texto da mensagem do usuário");
    }

    @Test
    void falhasRepetidasNaUltimaHora_disparamAlertaOperacional() {
        doThrow(new IllegalStateException("boom"))
            .when(cerebroSemanticoService).gravarMemoria(anyLong(), anyString(), any(), any());

        service.capturarDeConversaAsync(7L, TEXTO_PLANO, null, OrigemConteudo.TEXTO_USUARIO);
        service.capturarDeConversaAsync(7L, TEXTO_PLANO, null, OrigemConteudo.TEXTO_USUARIO);
        verify(alertaOperacionalService, never()).alertar(anyString(), anyString());

        service.capturarDeConversaAsync(7L, TEXTO_PLANO, null, OrigemConteudo.TEXTO_USUARIO);
        verify(alertaOperacionalService, atLeastOnce())
            .alertar(eq(AlertaOperacionalService.TIPO_MEMORIA_CAPTURA_FALHA), contains("3 vez(es)"));
    }
}
