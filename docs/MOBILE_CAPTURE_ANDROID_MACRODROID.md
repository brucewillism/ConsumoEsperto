# Captura automática — Android (MacroDroid)

Este guia descreve como enviar notificações bancárias do Android para o ConsumoEsperto **sem captura de tela** e **sem OCR**.

## Pré-requisitos

1. Ativar `MOBILE_CAPTURE_ENABLED=true` no servidor.
2. No ConsumoEsperto: **Perfil → Captura automática de gastos → Adicionar Android**.
3. Guardar **uma única vez**:
   - `ingestionUrl` (endpoint HTTPS)
   - `deviceToken` (credencial exclusiva do aparelho)

Nunca use o JWT de login do ConsumoEsperto no MacroDroid.

## Endpoint

```
POST {ingestionUrl}
```

Exemplo: `https://consumoesperto.brucew07.com.br/api/ingestion/mobile/transactions`

### Headers

| Header | Obrigatório | Descrição |
|--------|-------------|-----------|
| `X-CE-Device-Token` | Sim | Token exibido no cadastro do dispositivo |
| `X-CE-Client-Event-Id` | Não | ID único gerado no celular (recomendado para deduplicação) |
| `Content-Type` | Sim | `application/json` |

## Payload oficial (JSON)

```json
{
  "source": "ANDROID_NOTIFICATION",
  "client_event_id": "{not_id}",
  "package": "{not_app_package}",
  "notification_title": "{not_title}",
  "notification_text": "{not_text}",
  "notification_big_text": "{not_text_big}"
}
```

### Magic Text MacroDroid

Substitua no corpo JSON:

| Placeholder MacroDroid | Campo JSON |
|------------------------|------------|
| `{notification}` | (não enviar diretamente — use os campos abaixo) |
| `{not_title}` | `notification_title` |
| `{not_text}` | `notification_text` |
| `{not_text_big}` | `notification_big_text` |
| `{not_app_package}` | `package` |
| `{not_app_name}` | (opcional; o backend usa `package`) |

O backend extrai **valor** e **estabelecimento** do texto da notificação quando possível. Não envie `userId`.

## Exemplo — Nubank

Notificação:

> Compra aprovada  
> Compra de R$ 89,90 em POSTO SHELL

JSON enviado:

```json
{
  "source": "ANDROID_NOTIFICATION",
  "package": "com.nu.production",
  "notification_title": "Compra aprovada",
  "notification_text": "Compra de R$ 89,90 em POSTO SHELL"
}
```

## Teste de conexão

Envie sem criar transação:

```json
{ "source": "TEST" }
```

No perfil, o dispositivo mostrará **Último teste: OK**.

## MacroDroid — passos resumidos

1. **Gatilho:** Notificação recebida → selecione o app do banco.
2. **Ação:** HTTP Request → POST para `ingestionUrl`.
3. **Headers:** `X-CE-Device-Token: <seu token>`.
4. **Body:** JSON acima com Magic Text.
5. (Opcional) Gerar UUID local e enviar em `client_event_id` / header `X-CE-Client-Event-Id`.

## Segurança

- Use **HTTPS** em produção.
- Revogue o dispositivo se perder o telefone.
- Rotacione o token se suspeitar de vazamento.

## Mapeamento conta/cartão

Em **Perfil → Captura automática**, configure mapeamentos por `package` do banco (ex.: Nubank → cartão Nubank). Se a notificação trouxer final do cartão, use regra por `card_last4`.

## Revisão manual

Se o parser não tiver confiança suficiente, o evento ficará em **Captura automática → Revisão pendente** (`NEEDS_REVIEW`).
