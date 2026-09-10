# Matriz de degradação — estado real (2026-09-08)

O resolver por regex de texto livre foi removido na rodada 2. O substituto
correto é embedding na E.D.I.T.H. Esse substituto **não tem data** (a
E.D.I.T.H. pulou as Fases 1 e 2 para atacar latência de provider).

Antes: caminho local ruim, porém existente. Agora: texto livre só existe se
a E.D.I.T.H. estiver no ar, ou se o `LegacyCognitiveGateway` responder via
pipeline J.A.R.V.I.S. (comandos estruturados / fallback de parse).

`finance:write` continua desabilitado. Capabilities determinísticas
(`local_resolvable`) não passam por LLM.

---

## Canais

| Situação | Texto livre no chat | Botão / capability `INSTANT` | Núcleo financeiro (dashboard, CRUD, fatura, PDF/CSV, relatório) |
|---|---|---|---|
| E.D.I.T.H. desligada (`enabled=false`) | `LegacyCognitiveGateway` (`mode=LOCAL`). Sem embedding. Fast-path de comando funciona. Frase aberta cai no parse local; sem chave de LLM devolve texto de “motor indisponível + use comandos diretos”, **não silêncio**. Badge: **Modo local**. | `POST /api/capabilities/{id}:invoke` — independe da E.D.I.T.H. | Independente. Já coberto por teste HTTP. |
| E.D.I.T.H. ligada e operacional | `BALANCED` na E.D.I.T.H. (sem regex local). | Idem, local. | Independente. |
| E.D.I.T.H. ligada, **fora** (não operacional / circuit), **fallback ligado** (default) | `LegacyCognitiveGateway` (`mode=LEGACY`). O chat **responde**. Badge: **Modo degradado** + aviso discreto no painel. | Local. | Independente. |
| E.D.I.T.H. ligada, fora, **fallback desligado** | `mode=DEGRADED`, texto: *“O assistente cognitivo está temporariamente indisponível. Suas finanças continuam acessíveis no app.”* Sem silêncio, sem 500 genérico. Badge: **E.D.I.T.H. indisponível**. | Local. | Independente. |
| SSE da tarefa falha no meio | O painel troca o texto da bolha para a mesma frase de indisponibilidade. Badge: **E.D.I.T.H. indisponível**. | — | Independente. |

---

## O que o texto livre **não** faz hoje

Não há resolução local de “onde estou gastando?” para `finance.category.summary`
a partir da frase. Sem a E.D.I.T.H., essa pergunta só sai se o usuário usar o
botão (capability id) ou um comando estruturado que o legado ainda parseie.

Isso é o buraco deixado pela retirada do regex. Não foi tapado com embedding.
A matriz não finge que o legado “entende” linguagem natural.

---

## Núcleo vs assistente

Nada de dashboard, relatório (`GET /api/relatorios/mensal`), importação de
PDF/CSV (`GET /api/importacoes/faturas/pendentes`), lançamento
(`GET /api/transacoes`), contas, cartões ou faturas chama a E.D.I.T.H. para
ler ou gravar o Postgres financeiro. Se a E.D.I.T.H. cair, o app financeiro
continua. O que cai é o chat em linguagem natural.

Testes: `EdithFeatureFlagHttpTest.nucleoFinanceiroNaoDependeDaEdith`,
`edithDesligada_textoLivreRespondeViaLegacyGateway`,
`LegacyCognitiveGatewayHttpTest`.

---

## Mensagem visível

| Estado | O usuário vê |
|---|---|
| `LOCAL` | Badge “Modo local”. Sem banner. |
| `DEGRADED` | Badge “Modo degradado” + faixa: assistente em modo reduzido; finanças no app. Corpo da resposta preenchido (legado ou a frase de indisponibilidade). |
| `EDITH_UNAVAILABLE` | Badge “E.D.I.T.H. indisponível” + a mesma faixa. Texto explícito, não erro genérico de “núcleo de inferência”. |
| `ONLINE` | Badge “Online”. |

O `LegacyCognitiveGateway` **não é código morto**: com a flag desligada, ou
com fallback e hub fora, `POST /api/ia-chat` sem `capability` passa por ele
e devolve `resposta` não vazia.
