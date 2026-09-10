# Captura automática de gastos por notificação bancária

O ConsumoEsperto lança a compra **sozinha** quando o celular envia o texto da notificação do banco. O J.A.R.V.I.S. avisa no WhatsApp com opção de corrigir categoria, mudar conta/cartão ou apagar (estorno de saldo). **Não há confirmação prévia.**

Servidor de produção de referência: `https://consumoesperto.brucew07.com.br`.

No app: **Captura automática** (`/captura-automatica`) — gerar token, mapear banco ↔ conta/cartão, ver o log das recebidas.

Ative no servidor: `INGEST_NOTIFICACAO_ENABLED=true` (e `INGEST_NOTIFICACAO_URL` com a URL pública HTTPS).

---

## Contrato HTTP (iOS e Android)

```
POST https://consumoesperto.brucew07.com.br/api/ingest/notificacao
```

| Header | Valor |
|--------|--------|
| `Content-Type` | `application/json` |
| `X-Ingest-Token` | token gerado **uma vez** na tela Captura automática |

Não envie JWT nem `usuarioId`. O utilizador sai do token.

### Corpo

```json
{
  "origem": "ANDROID_MACRODROID",
  "app": "nubank",
  "titulo": "{not_title}",
  "texto": "{not_text}",
  "recebidoEm": "2026-09-09T11:13:00-03:00",
  "idExterno": "{not_id}"
}
```

| Campo | Notas |
|-------|--------|
| `origem` | `ANDROID_MACRODROID` ou `IOS_ATALHOS` |
| `app` | `nubank`, `itau` ou `outro` (fixe no macro por banco; não use o package Android) |
| `titulo` / `texto` | título e corpo da notificação |
| `recebidoEm` | ISO-8601 com fuso; se omitir, o servidor usa `America/Sao_Paulo` |
| `idExterno` | opcional; no MacroDroid use `{not_id}` para evitar reenvio duplicado |

Resposta: **202 Accepted** `{ "id": 123, "status": "RECEBIDA" }`. O processamento é assíncrono.

Token inválido ou revogado → **401** (o token nunca é registado em log).

---

## Android — MacroDroid (passo a passo)

O utilizador **não programa app nativo**. Duas macros (Nubank e Itaú) são o caminho mais simples, cada uma com `app` fixo.

### 1. Instalar e permissões

1. Instale o **MacroDroid** (Play Store).
2. Abra MacroDroid → conceda **acesso às notificações** (o Android pede isto na primeira macro com gatilho de notificação).
3. Definições do Android → Apps → MacroDroid → **Bateria** → **Sem restrições** (senão o Android mata o serviço e as compras deixam de chegar).
4. Opcional: defina o MacroDroid como não optimizado em «Optimização de bateria».

### 2. Token no ConsumoEsperto

1. No app, abra **Captura automática**.
2. Clique **Gerar token** e **Copiar token** (só aparece uma vez).
3. Mapeie: Nubank / crédito → cartão Nubank; Nubank / PIX → conta Nubank; o mesmo para Itaú.

### 3. Macro Nubank

1. **Gatilho** → **Notificação recebida**.
   - Aplicação: **Nubank** (`com.nu.production`).
   - Deixe o texto do título/corpo em branco (todas as notificações do app); o servidor descarta promoções e «fatura fechou».
2. **Acção** (antes do HTTP, para o JSON não partir com aspas no estabelecimento):
   - **Definir variável** `titulo` = `{not_title}`
   - **Definir variável** `texto` = `{not_text}`
   - **Texto** (manipular variável): em `texto` e `titulo`, substitua `"` por `'` e `\` por `/`.
3. **Acção** → **HTTP Request**
   - Método: **POST**
   - URL: `https://consumoesperto.brucew07.com.br/api/ingest/notificacao`
   - Content-Type: `application/json`
   - Header extra: nome `X-Ingest-Token`, valor = o token copiado
   - Corpo (texto):

```json
{
  "origem": "ANDROID_MACRODROID",
  "app": "nubank",
  "titulo": "{v=titulo}",
  "texto": "{v=texto}",
  "idExterno": "{not_id}"
}
```

No MacroDroid o magic text de variáveis costuma ser `{v=nome}` (ou `{lv=nome}` em versões antigas). Confirme em **Adicionar magic text** → Variáveis. Os campos da notificação são:

| Magic text | Uso |
|------------|-----|
| `{not_title}` | Título |
| `{not_text}` | Texto |
| `{not_text_big}` | Texto expandido (opcional; pode concatenar em `texto`) |
| `{not_app_package}` | Não precisa enviar; o `app` vai fixo `nubank` |
| `{not_id}` | `idExterno` |

4. Guarde a macro e deixe-a **activa**.

### 4. Macro Itaú

Igual à do Nubank, filtrando o app **Itaú** e com `"app": "itau"` no JSON.

### 5. Teste

Faça uma compra pequena. No app, **Últimas notificações** deve mostrar status **Lançada** (ou **Ignorada** se for promoção). O WhatsApp recebe o aviso. Responda `apagar` se não for isso.

---

## iOS — Atalhos (automação já existente)

Ajuste a automação para este contrato (`origem` **obrigatoriamente** `IOS_ATALHOS`):

- URL: `https://consumoesperto.brucew07.com.br/api/ingest/notificacao`
- Método POST, `Content-Type: application/json`
- Header `X-Ingest-Token`
- JSON:

```json
{
  "origem": "IOS_ATALHOS",
  "app": "nubank",
  "titulo": "<Título da Notificação>",
  "texto": "<Corpo da Notificação>",
  "recebidoEm": "<Data ISO da notificação>",
  "idExterno": "<Identificador da notificação se existir>"
}
```

No Atalhos, use as variáveis da automação «Quando receber notificação» (título, corpo, app). Mapeie o app Nubank/Itaú para `"nubank"` / `"itau"`.

**Limitação do iOS:** automações de notificação podem pedir um toque para correr (o iOS não entrega tudo em segundo plano). Se a automação não disparar sozinha, use um atalho no menu de partilha ou confirme o toque quando a notificação chegar.

---

## Sem mapeamento conta/cartão

O lançamento usa a **conta padrão** do utilizador (preferência na tela, ou a conta marcada como padrão no perfil). O aviso no WhatsApp menciona isso para o utilizador associar o app do banco.

---

## Solução de problemas

| Sintoma | O que fazer |
|---------|-------------|
| 403 HTTPS obrigatório | O Atalhos já usa `https://`. O Nginx precisa de `proxy_set_header X-Forwarded-Proto $scheme;` — ver [`docker/REVERSO_PROXY_502.md`](../docker/REVERSO_PROXY_502.md) |
| Token inválido (401) | Regenerar na tela; o valor antigo deixa de valer; não cole espaços |
| Status **Não reconhecida** | Copie o texto do log e acrescente uma linha em `backend/src/test/resources/notificacoes-bancarias.jsonl` (ver comentário em `NotificacaoBancariaParserJsonlTest`) |
| Status **Ignorada** | Promoção, fatura fechou, login, saldo — é esperado |
| Compra duplicada | Já existia lançamento no WhatsApp (±2 h, mesmo valor) ou a mesma notificação foi reenviada (`idExterno`) |
| WhatsApp silêncio de madrugada | Horário silencioso na tela (padrão 22:00–07:00); o aviso acumula e sai depois |

---

## Relação com a captura móvel antiga

O pipeline `POST /api/ingestion/mobile/transactions` (`X-CE-Device-Token`) continua a existir. **Este guia e a tela nova usam** `POST /api/ingest/notificacao`. Não misture os dois tokens.
