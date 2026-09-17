package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.repository.MobileCaptureEventRepository;
import com.consumoesperto.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class MobileIngestionDeduplicationServiceTest {

    @Mock private MobileCaptureEventRepository eventRepository;
    @Mock private TransacaoRepository transacaoRepository;

    private MobileIngestionDeduplicationService service;

    @BeforeEach
    void setup() {
        service = new MobileIngestionDeduplicationService(eventRepository, transacaoRepository);
    }

    @Test
    void dezHorasEDezEQuatroNaoCompartilhamFingerprint() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 13, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 13, 10, 4, 0);
        String a = service.buildFingerprint(
            1L, OrigemTransacao.IOS_WALLET, 9L, null, 2L,
            new BigDecimal("89.90"), "POSTO X", t1);
        String b = service.buildFingerprint(
            1L, OrigemTransacao.IOS_WALLET, 9L, null, 2L,
            new BigDecimal("89.90"), "POSTO X", t2);
        assertNotEquals(a, b);
    }
}
