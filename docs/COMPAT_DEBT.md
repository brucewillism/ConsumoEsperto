# Dívida de compatibilidade (v1.1, seção 13)

Toda ponte anterior ao contrato fica visível: marcada na métrica e com data-alvo
de remoção. Sem isso a ponte vira permanente.

Datas ancoradas em 2026-09-07 (rodada 2). Hoje: 2026-09-08.
**Nenhuma data-alvo passou.** Nenhuma dívida desta lista virou permanente
por prazo.

---

## 1. Envelope sintetizado no Tool Bridge legado

**O que é.** A E.D.I.T.H. atual chama `POST /api/internal/edith/tools` com
`X-Edith-*` e, em geral, **sem** `X-Eco-Trace-Id`. O filtro de borda sintetiza
um envelope (`EcoEnvelopeFactory`) em vez de rejeitar a chamada.

**Por que existe.** Matar o Tool Bridge até a E.D.I.T.H. migrar o envelope
quebraria o chat. Sintetizar foi a escolha da Fase 0; o contrato v1.1 passou a
exigir a marca `envelope_synthesized: true` na métrica `eco_span`.

**O que precisa acontecer para sair.** A E.D.I.T.H. envia `X-Eco-*` (trace da
borda, span novo, deadline absoluto, sensitivity). ConsumoEsperto deixa de
sintetizar quando o header chega. Remoção da síntese é fail-closed: request
sem envelope no Tool Bridge vira `INVALID_INPUT` / recusa de contrato, não
gera `tr_` novo.

**Estado em 2026-09-08.** Este repo **aceita** `X-Eco-*` quando chega
(`EcoEnvelopeDeadlineHttpTest` manda `X-Eco-Trace-Id`). O cliente HTTP daqui
para a E.D.I.T.H. **envia** `X-Eco-*` no hop de conversa. O Tool Bridge
legado, nas suítes que imitam a E.D.I.T.H. de hoje, ainda autentica com
`X-Edith-*` e **sem** `X-Eco-Trace-Id` — o filtro sintetiza. A E.D.I.T.H.
ainda não mandou o envelope canônico neste hop. Dívida intacta.

**Métrica.** `envelope_synthesized=true` em `eco_span`.

**Data-alvo de remoção:** 2026-12-07.

---

## 2. HMAC da seção 9 ainda não aplicado (esquema antigo)

**O que é.** O Tool Bridge autentica com o esquema anterior (`X-Edith-*` /
`X-API-Key` / HMAC legado do callback). O HMAC canônico da seção 9 do
contrato (serviço a serviço, headers `X-Eco-*`) **não** está em vigor neste
repo.

**Por que existe.** A E.D.I.T.H. e o ConsumoEsperto ainda falam o contrato de
callback da integração anterior. Trocar o HMAC só deste lado derruba o
bridge.

**O que precisa acontecer para sair.** Os quatro repos alinharem o HMAC da
seção 9; ConsumoEsperto passa a verificar esse HMAC **depois** do kill de
deadline e **antes** do banco. O esquema `X-Edith-*` sai junto.

**Métrica.** Enquanto o esquema antigo autenticar o hop: `auth_scheme=legacy`
(já emitido no Tool Bridge / `ia-chat`).

**Data-alvo de remoção:** 2026-11-07.

---

## 3. `X-Eco-User-Id` preenchido só depois do JWT

**O que é.** O `EcoEnvelopeFilter` corre **antes** da autenticação Spring.
Na borda Angular o header de usuário ainda não é confiável (o JWT é a fonte).
`EcoEnvelopeHolder.bindUser` preenche `X-Eco-User-Id` depois que o controller
vê o `UserPrincipal`.

**Por que existe.** Filtro de envelope precisa existir cedo para matar
deadline no Tool Bridge sem HMAC/banco. Na borda web, o user id verdadeiro só
existe após o JWT.

**O que precisa acontecer para sair.** Ou o filtro de envelope passa a rodar
depois do JWT na borda autenticada (sem atrasar o kill de deadline do Tool
Bridge, que não usa sessão de usuário), ou a borda Angular envia um user id
opaco já no envelope e o backend **confirma** contra o JWT (mismatch =
`SCOPE_DENIED`). Não aceitar `X-Eco-User-Id` do cliente sem conferir o token.

**Data-alvo de remoção:** 2026-10-21.

---

## 4. WhatsApp não instrumentado como borda

**O que é.** O webhook Evolution / comandos WhatsApp não criam envelope
`X-Eco-*` na entrada. Não há `trace_id` estável nem deadline de borda nesse
canal.

**Por que existe.** Fora do escopo da rodada 2 (pedido explícito: registrar
como dívida e seguir).

**O que precisa acontecer para sair.** O handler WhatsApp passa a ser borda:
nasce `trace_id`, `X-Eco-Mode` (comando curto → `INTERACTIVE`; texto livre →
`BALANCED`), deadlines absolutos, `sensitivity` sobe para `FINANCIAL` quando
o comando toca dado financeiro. Sem isso o Control Center não vê o hop.

**Data-alvo de remoção:** 2026-10-21.

---

## 5. `POST /api/ia-chat` como ponte de capability

**O que é.** Botões rápidos agora chamam `POST /api/capabilities/{id}:invoke`
com o ID da capability. O endpoint antigo ainda executa a **mesma**
implementação (`LocalFinanceCapabilityService`) se o cliente mandar
`capability` no body — ponte para clientes que ainda falam frase+hint.

Texto livre **não** resolve mais capability por regex; segue para a E.D.I.T.H.
em modo `BALANCED` (escape hatch da v1.1).

**Por que existe.** Não quebrar quem ainda posta em `/api/ia-chat` com o
campo `capability`. Dois caminhos iguais divergem; a ponte é temporária.

**O que precisa acontecer para sair.** Só o Angular atual (e nenhum outro
cliente) no ar; então `/api/ia-chat` deixa de aceitar `capability` e vira
apenas chat texto livre / comandos `tutorial|ajuda|sair|menu`.

**Métrica.** `auth_scheme=legacy` em todo hop `/api/ia-chat`. O invoke novo
marca `auth_scheme=session`.

**Data-alvo de remoção:** 2026-12-07.

---

## 6. `finance.category.summary` — RESOLVIDO (rodada 3)

Era agregado em memória (`buscarPorPeriodo` + heap). Agora
`GROUP BY` no SQL (`TransacaoRepository.sumDespesasPorCategoriaCapability`),
teto 12/30, DTO de totais. Saído da lista viva.

Não remedimos p95 Postgres desta tool nesta rodada (não era o recorte do
breakdown dos 19,8 ms). O número do manifesto (`CATEGORY_SUMMARY = 40`)
continua conservador até uma amostra Postgres dedicada.
