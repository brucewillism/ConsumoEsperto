# Captura automática — iPhone (Atalhos / Wallet)

Este guia descreve como enviar transações do Apple Pay / Wallet para o ConsumoEsperto via **Atalhos (Shortcuts)**.

## Pré-requisitos

1. Ativar `MOBILE_CAPTURE_ENABLED=true` no servidor.
2. No ConsumoEsperto: **Perfil → Captura automática de gastos → Adicionar iPhone**.
3. Guardar **uma única vez** o `deviceToken` e o `ingestionUrl`.

Não use o JWT de login no Atalho.

## Endpoint

```
POST {ingestionUrl}
```

Headers:

- `X-CE-Device-Token: <token do dispositivo>`
- `Content-Type: application/json`
- (Opcional) `X-CE-Client-Event-Id: <uuid>`

## Payload estruturado (preferido)

No iPhone os dados chegam estruturados — **não dependa de texto de notificação**.

```json
{
  "source": "IOS_WALLET",
  "client_event_id": "<UUID>",
  "occurred_at": "2026-09-01T14:30:00",
  "amount": 89.90,
  "currency": "BRL",
  "merchant": "POSTO SHELL",
  "card_hint": "Visa •••• 1234"
}
```

| Campo | Origem no Atalho (pode variar por versão do iOS) |
|-------|--------------------------------------------------|
| `amount` | **Amount** / **Valor** da transação Wallet |
| `merchant` | **Merchant** / **Name** / nome do estabelecimento |
| `card_hint` | **Card** / **Pass** / cartão associado |
| `occurred_at` | **Current Date** / data da transação (ISO 8601) |

Os nomes exatos das variáveis no Atalhos mudam entre versões do iOS — mapeie manualmente para os campos acima.

## Exemplo mínimo

```json
{
  "source": "IOS_WALLET",
  "amount": 89.90,
  "currency": "BRL",
  "merchant": "POSTO SHELL"
}
```

## Atalho — fluxo sugerido

1. **Gatilho:** Transação Wallet / Apple Pay (quando disponível na sua versão do iOS) ou execução manual para teste.
2. **Obter detalhes** da transação (Amount, Merchant, Card).
3. **Dicionário** ou **Texto** com JSON conforme acima.
4. **Obter conteúdos de URL:**
   - Método: `POST`
   - URL: `ingestionUrl`
   - Cabeçalhos: `X-CE-Device-Token`
   - Corpo: JSON
5. (Opcional) **Gerar UUID** para `client_event_id`.

## Teste de conexão

```json
{ "source": "TEST" }
```

## Segurança

- Somente **HTTPS** em produção.
- Token por dispositivo, revogável e rotacionável no perfil.
- Não envie `userId` — o servidor resolve o usuário pelo token do dispositivo.

## Deduplicação

Reenvios com o mesmo `client_event_id` retornam `DUPLICATE` e não criam nova despesa.

## Revisão

Eventos com dados insuficientes aparecem em **Perfil → Revisão pendente**.
