package com.consumoesperto.service;

import com.consumoesperto.config.AlertasOperacionaisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertaOperacionalServiceTest {

    @Mock private ObjectProvider<AlertaEmailSender> emailProvider;
    @Mock private AlertaEmailSender emailSender;

    private AlertasOperacionaisProperties props;
    private AlertaOperacionalService service;

    @BeforeEach
    void setup() {
        props = new AlertasOperacionaisProperties();
        props.setWebhookEnabled(false);
        props.setEmailEnabled(true);
        props.setEmailDestino("ops@example.com");
        props.setEmailFrom("app@example.com");
        props.setCooldownMinutes(15);
        when(emailProvider.getIfAvailable()).thenReturn(emailSender);
        service = new AlertaOperacionalService(props, emailProvider);
    }

    @Test
    void emailDisparaUmaVezDentroDoCooldown() {
        service.alertar(AlertaOperacionalService.TIPO_WHATSAPP_DESCONECTADO, "caiu", "assunto", true);
        service.alertar(AlertaOperacionalService.TIPO_WHATSAPP_DESCONECTADO, "caiu de novo", "assunto", true);
        verify(emailSender, times(1)).enviar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void recuperacaoIgnoraCooldownDoTipoProprio() {
        service.alertar(AlertaOperacionalService.TIPO_WHATSAPP_RECUPERADO, "ok", "rec", false);
        service.alertar(AlertaOperacionalService.TIPO_WHATSAPP_RECUPERADO, "ok2", "rec", false);
        verify(emailSender, times(2)).enviar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void falhaSmtpNaoPropaga() {
        doThrow(new RuntimeException("smtp down")).when(emailSender)
            .enviar(anyString(), anyString(), anyString(), anyString());
        assertDoesNotThrow(() ->
            service.alertar(AlertaOperacionalService.TIPO_WHATSAPP_DESCONECTADO, "caiu"));
    }

    @Test
    void emailDesligadoNaoChamaSender() {
        props.setEmailEnabled(false);
        service.alertar(AlertaOperacionalService.TIPO_WHATSAPP_DESCONECTADO, "caiu");
        verify(emailSender, never()).enviar(anyString(), anyString(), anyString(), anyString());
    }
}
