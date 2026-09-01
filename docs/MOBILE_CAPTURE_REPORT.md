# Relatório — Captura automática de gastos (módulo móvel)

> **Status:** implementado localmente, **sem commit/push**.  
> **Validação E2E simulada:** Android notification e iOS Wallet → `TransactionIngestionService` → `TransacaoService` com deduplicação (testes de integração).

## Architecture

| Item | Resultado |
| ---- | --------- |
| `TransactionIngestionService` (ponto único) | Implementado |
| `MobileIngestionController` → ingestão por token | Implementado |
| `MobileCaptureDeviceController` (JWT usuário) | Implementado |
| `MobileCaptureReviewController` (`NEEDS_REVIEW`) | Implementado |
| `FinancialDataIngestionProvider` (stub Open Finance) | Interface preparada, sem implementação externa |
| Feature flags `MOBILE_CAPTURE_*` | `application.properties` + `.env.example` |
| E.D.I.T.H. somente classificação (`consumo.transaction_classification`) | Implementado, sem write tool |

## Database

| Migration | Tabelas/campos | Resultado |
| --------- | -------------- | --------- |
| `V8__mobile_capture_ingestion.sql` | `mobile_capture_devices`, `mobile_capture_events`, `mobile_source_mappings`, `merchant_category_rules` | Criada |
| `transacoes` | `origem_transacao`, `external_event_id`, `merchant_*`, `ingestion_fingerprint`, etc. | Colunas adicionadas |
| Unique `(device_id, client_event_id)` | Deduplicação por evento | Constraint ativa |

## Device security

| Cenário | Resultado |
| ------- | --------- |
| Token `ce_mcd_*` gerado com `SecureRandom` | OK |
| Hash SHA-256 no banco (sem plaintext) | OK |
| Token exibido uma vez no cadastro | OK |
| Revogação / rotação | Endpoints implementados |
| Token inválido → 401 | Teste HTTP |
| HTTPS obrigatório em produção (`requireHttps`) | Configurável |
| `userId` no payload ignorado como autoridade | OK |
| Isolamento A/B (token A ≠ usuário B) | Teste HTTP |

## Android

| Parser/endpoint | Resultado |
| --------------- | --------- |
| `NubankNotificationParser` | `compra R$ 89,90 em POSTO SHELL` |
| `ItauNotificationParser` / `InterNotificationParser` | Estrutura pronta |
| `GenericBankNotificationParser` | Fallback sem inventar valor |
| `POST /api/ingestion/mobile/transactions` | OK |
| Doc MacroDroid | `docs/MOBILE_CAPTURE_ANDROID_MACRODROID.md` |

## iOS

| Payload | Resultado |
| ------- | --------- |
| `IOS_WALLET` estruturado (`amount`, `merchant`) | Parser `IosWalletStructuredParser` |
| Sem dependência de texto de notificação | OK |
| Doc Atalhos | `docs/MOBILE_CAPTURE_IOS_SHORTCUTS.md` |

## Deduplication

| Cenário | Resultado |
| ------- | --------- |
| Mesmo `client_event_id` (header/payload) | `DUPLICATE`, 0 nova transação |
| Fingerprint (usuário+origem+valor+merchant+janela hora) | Implementado |
| Pré-check antes de insert (evita violação unique) | Corrigido |
| Concorrência (race) | `DataIntegrityViolationException` tratada |

## E.D.I.T.H.

| Classificação | Offline | Resultado |
| ------------- | ------- | --------- |
| `MerchantCategoryRule` local primeiro | — | OK |
| E.D.I.T.H. `consumo.transaction_classification` | — | Valida ownership categoria |
| E.D.I.T.H. indisponível | Sim | Registra sem categoria (pendente) |
| Write tool na E.D.I.T.H. | — | **Não criada** |

## Tests

| Camada | Descobertos | Aprovados | Falharam | Ignorados |
| ------ | ----------: | --------: | -------: | --------: |
| Unit (`DeviceTokenHasher`, normalização, parsers) | 7 | 7 | 0 | 0 |
| Integração HTTP (`MobileCaptureHttpIntegrationTest`) | 6 | 6 | 0 | 0 |
| **Total módulo móvel** | **13** | **13** | **0** | **0** |

Comando executado:

```bash
mvn test -Dtest=DeviceTokenHasherTest,MerchantNormalizationServiceTest,AndroidNotificationParsingTest,MobileCaptureHttpIntegrationTest
```

## Frontend

| Item | Resultado |
| ---- | --------- |
| Perfil → Captura automática de gastos | Seção com dispositivos, adicionar Android/iPhone, credenciais únicas |
| Rota `/captura-automatica/revisao` | Confirmar / descartar / categoria / conta |
| `MobileCaptureService` (Angular) | Implementado |

## Ativação

1. Definir `MOBILE_CAPTURE_ENABLED=true` (e `MOBILE_CAPTURE_INGESTION_URL` com HTTPS em produção).
2. Cadastrar dispositivo no Perfil.
3. Configurar MacroDroid ou Atalhos conforme docs.
4. (Opcional) Mapear `package` → conta/cartão via API `source-mappings`.

## Final

**E2E simulado validado** no backend:

- Android: notificação Nubank → parser → `TransacaoService` → 1 transação; reenvio → `DUPLICATE`.
- iOS: payload `IOS_WALLET` → `TransacaoService` → 1 transação.

**Pendente para produção:** teste com dispositivo físico + `MOBILE_CAPTURE_ENABLED=true` na VPS.
