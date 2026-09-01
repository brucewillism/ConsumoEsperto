package com.consumoesperto.mobilecapture.parser;

import com.consumoesperto.mobilecapture.dto.MobileTransactionIngestionRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AndroidNotificationParsingTest {

  private final MobileNotificationParsingService service = new MobileNotificationParsingService(
      List.of(
          new NubankNotificationParser(),
          new ItauNotificationParser(),
          new InterNotificationParser(),
          new GenericBankNotificationParser()
      )
  );

  @Test
  void nubank_compraAprovada_extraiValorEMerchant() {
    MobileTransactionIngestionRequest req = new MobileTransactionIngestionRequest();
    req.setSource("ANDROID_NOTIFICATION");
    req.setPackageName("com.nu.production");
    req.setNotificationTitle("Compra aprovada");
    req.setNotificationText("Compra de R$ 89,90 em POSTO SHELL");

    Optional<ParsedMobileTransaction> parsed = service.parse(req);
    assertTrue(parsed.isPresent());
    assertEquals(new BigDecimal("89.90"), parsed.get().getAmount());
    assertTrue(parsed.get().getMerchant().contains("POSTO SHELL"));
    assertEquals("NubankNotificationParser", parsed.get().getParserName());
  }

  @Test
  void iosWallet_payloadEstruturado() {
    MobileTransactionIngestionRequest req = new MobileTransactionIngestionRequest();
    req.setSource("IOS_WALLET");
    req.setAmount(new BigDecimal("89.90"));
    req.setMerchant("POSTO SHELL");
    req.setCurrency("BRL");

    Optional<ParsedMobileTransaction> parsed = service.parse(req);
    assertTrue(parsed.isPresent());
    assertEquals(new BigDecimal("89.90"), parsed.get().getAmount());
    assertEquals("POSTO SHELL", parsed.get().getMerchant());
  }

  @Test
  void notificacaoDesconhecida_semValor_naoInventa() {
    MobileTransactionIngestionRequest req = new MobileTransactionIngestionRequest();
    req.setSource("ANDROID_NOTIFICATION");
    req.setPackageName("com.banco.desconhecido");
    req.setNotificationText("Bem-vindo ao app");

    assertTrue(service.parse(req).isEmpty());
  }
}
