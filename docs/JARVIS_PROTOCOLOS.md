# J.A.R.V.I.S. — Protocolos avançados

Referência dos protocolos financeiros além do CRUD básico (despesas, metas, faturas): **Advisor**, **empréstimo consignado**, **Sentinela**, **planejamento fiscal**, **áudio** e jobs proactivos.

**Última revisão:** junho/2026 · **Código:** `backend/src/main/java/com/consumoesperto/service/`

---

## 1. Conselheiro financeiro (Advisor)

Análise determinística de compras, parcelamentos e consignado, com narração opcional por IA.

| Componente | Ficheiro |
|------------|----------|
| Cálculo (Selic, IPCA, taxa BCB consignado, comprometimento de renda) | `FinancialAdviceCalculator.java` |
| Contexto (renda, reserva, empréstimos activos) | `JarvisContextoFinanceiroService.java` |
| Taxa média consignado (série BCB 25497) | `MarketDataService.java` |
| Narração em linguagem natural | `OpenAiService.narrarConselho` |

### WhatsApp

Comandos interpretados como `GET_FINANCIAL_ADVICE`:

- «vale a pena consignado de 10 mil em 24x?»
- «devo pegar esse empréstimo de 15 mil?»
- «quero comprar notebook 4500 em 12x, compensa?»

Resposta inclui parcela estimada, taxa de mercado, % da renda comprometida e recomendação (prosseguir / cautela / evitar).

### App

Mesmo motor via chat IA (`POST /api/ia-chat`) e página `/simulacoes`.

---

## 2. Empréstimo consignado

Registo atómico: **crédito CONFIRMADA** na conta + **parcelas PREVISTO** futuras, agrupadas por `emprestimo_id` (UUID).

| Componente | Ficheiro |
|------------|----------|
| Registo, preview, cancelamento | `EmprestimoService.java` |
| Parsing de texto/comando | `PropostaEmprestimoConsignado.java` |
| Handler WhatsApp | `WhatsAppCommandService` (`RECORD_CONSIGNMENT_LOAN`) |

### WhatsApp — registar (já contratado)

Exemplos:

```
fiz um empréstimo consignado no valor de 13.937,77 com 78 parcelas de 464
peguei consignado de 10 mil em 24x de 520, caiu no Itaú
```

Fluxo:

1. Parser extrai valor tomado, quantidade de parcelas, valor da parcela (ou estima pela taxa BCB) e conta (explícita ou padrão).
2. Se valor atípico, parcela estimada ou conta ambígua → **pede confirmação** (`sim` / `não`).
3. Persistência: 1 RECEITA + N DESPESAS PREVISTO (datas mensais).
4. Resposta com saldo actualizado e % de renda comprometida.

### WhatsApp — cancelar

```
cancela consignado
desfaz empréstimo
```

Marca todas as transações do último `emprestimo_id` como excluídas e estorna o crédito na conta.

### API REST

Não há controller dedicado — o registo é **WhatsApp-only** por agora. Consulta de empréstimos via transações (`GET /api/transacoes` filtrando por descrição ou futuro endpoint).

### Implementação técnica

- Coluna `transacoes.emprestimo_id` criada por `SchemaAutoPatchService`.
- Parcelas PREVISTO são gravadas em **lote** (`saveAll`) para evitar timeout de transacção (30 s) — não passam por categorização IA por parcela.
- Despesas com `emprestimoId` **não** disparam `sugerirCategoria` (OpenAI) em `TransacaoService`.

### Estados pendentes

Após preview de registo, `sim` / `não` têm prioridade sobre outras filas de confirmação.

---

## 3. Protocolo Sentinela

Monitorização de **disponibilidade real** (saldo após obrigações fixas, parcelas e provisões) e alertas de risco.

| Componente | Ficheiro |
|------------|----------|
| Margem OK / MODERADO / CRÍTICO | `SentinelaProtocolService.java` |
| Disponibilidade real e relatório WhatsApp | `PrevisaoFluxoCaixaService.java` |
| Colchão sazonal (13º, IR) | `SentinelaBufferSazonalService.java` |
| Alerta reactivo pós-despesa | `FinancialProactiveService.avisarRiscoVermelho` |
| Job dia 5 | `ProactiveFinancialJobs.enviarDisponibilidadeRealDiaCinco` |

### WhatsApp — consulta

Texto contendo, por exemplo:

- `sentinela`
- `disponibilidade real`
- `fluxo de caixa` + `real` / `líquido`

Devolve relatório de margem e projeção.

### WhatsApp — proactivo

- **Dia 5 de cada mês, 09:30** (America/Sao_Paulo): relatório automático de disponibilidade real.
- **Após despesa confirmada:** se forecast indicar risco vermelho, alerta tático (cooldown configurável — ver §6).

Config: `consumoesperto.jarvis.alerta-risco-cooldown-minutes` (default **30** min).

---

## 4. Planejamento fiscal (13º / IR)

Provisões PREVISTO para receitas sazonais e confirmação via WhatsApp.

| Componente | Ficheiro |
|------------|----------|
| Sincronização de provisões | `PlanejamentoFiscalService.java` |
| API app | `PlanejamentoFiscalController` → `/api/planejamento-fiscal` |
| Confirmação WhatsApp | `CONFIRM_FISCAL_PROVISION` |

App: configurar IR/13º em `/perfil` ou via API; WhatsApp confirma provisões com `sim` / `não`.

---

## 5. Áudio no WhatsApp

| Etapa | Serviço |
|-------|---------|
| Download mídia Evolution | `EvolutionMediaService.java` |
| Transcrição (Whisper / Groq / OpenAI) | `SpeechToTextService.java` → `OpenAiService.transcribeAudio` |
| Resposta em voz (opcional) | `TextToSpeechService.java` (ElevenLabs) → PTT Evolution |

### Entrada (STT)

Utilizador envia áudio → transcrição → mesmo pipeline de comandos de texto.

Modelos por utilizador: `usuario_ai_config` (`groqWhisperModel`, `openaiWhisperModel`).

### Saída (TTS — opcional)

```env
JARVIS_WHATSAPP_VOICE_REPLY=true
ELEVENLABS_API_KEY=...
ELEVENLABS_VOICE_ID=...
```

Quando activo, respostas longas podem ser enviadas também como áudio PTT.

---

## 6. Jobs proactivos (cron)

Horário: **America/Sao_Paulo** (salvo indicação contrária).

| Horário | Job | Serviço |
|---------|-----|---------|
| 06:00 | Agendamentos de pagamento | `AgendamentoPagamentoService` |
| 07:15 | Auditoria provisões «fantasma» | `ProactiveFinancialJobs` |
| 08:00 | Despesas fixas, assinaturas, recorrências | vários |
| 09:00 | Salário automático, watcher fechamento fatura | `SalarioAutomaticoScheduler`, `FaturaFechamentoWatcherService` |
| **09:30 dia 5** | **Sentinela — disponibilidade real** | `ProactiveFinancialJobs` |
| 10:00 seg | Amortização sazonal, Cronos Modo Viagem | `ProactiveFinancialJobs`, `CronosJarvisService` |
| 18:00 dom | Resumo semanal WhatsApp | `ProactiveFinancialJobs` |
| 18:30 dia 1 | Relatório mensal score | `ProactiveFinancialJobs` |
| cada hora | Limpar sessões de contexto | `UsuarioSessaoContextoService` |
| 02:00 | Backup, transações recorrentes | `BackupAutomaticoService`, `TransacaoRecorrenciaService` |

Watcher fechamento fatura: `consumoesperto.watcher.fechamento.cron=0 0 9 * * *` (09:00 diário).

---

## 7. Outros protocolos JARVIS

| Protocolo | Onde | Canal |
|-----------|------|-------|
| Contenção (tetos pós-fatura) | `ContencaoJarvisService` | WhatsApp + dashboard |
| Modo Viagem (Cronos) | `CronosJarvisService` | WhatsApp + dashboard |
| Otimização de metas | `AutomacaoTaticaService` | App: `PATCH /api/jarvis/otimizar-metas` |
| Amortização sazonal (debt snowball) | `AmortizacaoSazonalService` | App: `/api/amortizacao-sazonal/simulacao` |
| Memória semântica | `CerebroSemanticoService` | WhatsApp: «Jarvis, anote isso: …» |
| Feedback JARVIS | `JarvisProtocolController` | App: `POST /api/jarvis/feedback` |

---

## 8. Schema da base de dados

Não há ficheiros Flyway em `db/migration/` — o schema evolui em runtime via **`SchemaAutoPatchService`** (patches idempotentes no `@PostConstruct`).

Tabelas/colunas relevantes para estes protocolos:

- `transacoes.emprestimo_id`
- `usuario_configuracao_fiscal`
- `sugestoes_contencao_jarvis`
- `memoria_semantica_jarvis` (+ pgvector ou BYTEA fallback)
- `transacao_semantica_index` (RAG por transacções)

Ver também [`CONFIGURACAO_AMBIENTE.md`](../CONFIGURACAO_AMBIENTE.md) (secção pgvector).

---

## 9. Manutenção

Ao alterar comportamento visível ao utilizador:

1. Actualizar `WhatsAppAppParityService.CATALOGO`.
2. Actualizar este ficheiro e [`VISAO_GERAL.md`](VISAO_GERAL.md).
3. Se mudarem cron ou properties → [`CONFIGURACAO_AMBIENTE.md`](../CONFIGURACAO_AMBIENTE.md) e `.env.example`.
