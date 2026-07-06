# Funcionalidades do ConsumoEsperto

Guia completo do que o sistema faz e **como** cada parte funciona.  
**Última revisão:** julho/2026

**Documentos complementares:** [`VISAO_GERAL.md`](VISAO_GERAL.md) (arquitetura) · [`CALCULOS_FINANCEIROS.md`](CALCULOS_FINANCEIROS.md) (fórmulas) · [`JARVIS_PROTOCOLOS.md`](JARVIS_PROTOCOLOS.md) (bot) · catálogo vivo: `GET /api/whatsapp/paridade`

---

## Visão em uma frase

App web + WhatsApp (J.A.R.V.I.S.) partilham a mesma base PostgreSQL: regista receitas/despesas, cartões, faturas, metas e orçamentos; projeta o mês; protege saldo com lock e audit trail; automatiza PDF, OCR, débito de fixas e protocolos proativos.

---

## 1. Autenticação e perfil

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Login / registo | `/login`, `/register` | JWT; sessão no app |
| Google OAuth | Login | Conta Google vinculada |
| Perfil | `/perfil` | Nome, preferências J.A.R.V.I.S., Google Calendar |
| Obrigações fixas | `/perfil` | Despesas mensais recorrentes; opcional **débito automático** numa conta (job 06:30) |
| Config IA | `/perfil` ou `/api/config/ia` | Chaves Groq/OpenAI/etc. por utilizador |

---

## 2. Contas bancárias e saldo

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Multicarteira | `/contas` | Várias contas; saldo total = soma das ativas |
| Transferências | `/contas` | Entre contas; lock nas duas contas (ordem de ID) |
| Ajuste de saldo | Editar conta | `POST .../sincronizar-saldo` → ledger `AJUSTE_MANUAL` / `RECONCILIACAO` |
| Patrimônio líquido | Dashboard | Contas − passivo empréstimo (todas as parcelas PREVISTO) — ver [`CALCULOS_FINANCEIROS.md`](CALCULOS_FINANCEIROS.md) |
| Integridade | Job 03:30 | Compara saldo persistido vs fórmula; alerta se divergir |
| Reparo | `/api/reparo-financeiro` | Dry-run por padrão; aplicar com flag + backup |

**Regra de ouro:** nenhum código altera `saldo_atual` fora de `SaldoMovimentacaoService` (exceto reparo com origem `REPARO`).

---

## 3. Transações (receitas e despesas)

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| CRUD | `/transacoes` | DESPESA, RECEITA, INVESTIMENTO |
| Status | Política T1 | Só **CONFIRMADA** entra em médias históricas; **PREVISTO** só em projeções |
| Pagamento de fatura | Bloqueado no CRUD | Tipo `PAGAMENTO_FATURA` só via fluxo de pagamento de fatura |
| Parcelamento | Transações | `grupoParcelaId`, exclusão UM/FUTURAS/TUDO |
| Recorrência | Job | Gera ocorrências idempotentes |
| WhatsApp | Texto natural | «gastei 45 no mercado» → NLP → transação CONFIRMADA |

---

## 4. Cartões de crédito

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Cadastro | `/cartoes` | Limite, fechamento, vencimento, banco |
| Limite comprometido | Resumo cartão | Faturas ABERTA, PARCIAL, VENCIDA, **PREVISTA** |
| Compras | Transações na fatura | Alocação no ciclo correto (nunca fatura vencida) |
| Parcelas futuras | Importação / compra | Idempotentes; `MoedaUtil.distribuirParcelas` |
| CompraParcelada (legado) | Deprecated | Ver [`PLANO_CONVERSAO_COMPRA_PARCELADA.md`](PLANO_CONVERSAO_COMPRA_PARCELADA.md) |

---

## 5. Faturas de cartão

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Linha do tempo | `/faturas` | Agrupadas por mês de fechamento |
| **Mês atual** | UI | Expandido com badge «Mês atual» |
| **Histórico pago** | UI | Condensado por mês; clique expande |
| Importação PDF | WhatsApp / app | Extração IA → confirmação → lançamentos; dedup tolerante |
| Pagamento | Modal «Pagar com conta» | Cria `PAGAMENTO_FATURA` + debita conta + marca fatura |
| Quitação externa | API fatura | `origemQuitacao=EXTERNA` — libera limite, **sem** movimento de caixa |
| PREVISTA | Projeção | Total sincroniza ao receber lançamentos — [`POLITICA_STATUS_FATURA_PREVISTA.md`](POLITICA_STATUS_FATURA_PREVISTA.md) |

---

## 6. Categorias, orçamentos e metas

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Categorias | `/categorias` | Classificação de lançamentos; sugestão automática |
| Orçamentos | `/orcamentos` | Teto mensal por categoria; alertas no dashboard |
| Metas | `/metas` | % da renda estimada; progresso por **valor acumulado**; aviso se soma >100% |
| Protocolo contenção | Dashboard / WhatsApp | Após importar fatura, sugere tetos se gasto subiu vs média 3 meses |
| Otimização protocolo | Dashboard | Rebalanceia tetos não essenciais se colisão de projeção |

---

## 7. Renda, contracheque e fiscal

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Renda configurada | `/renda` | Salário esperado, dia de pagamento |
| Contracheque PDF | `/renda` ou WhatsApp | Extrai valores; **reconcilia** salário já lançado no mês (não duplica) |
| Média móvel 90d | Projeções / metas | Exclui crédito de empréstimo |
| Planejamento fiscal | Perfil + provisões | 13º/IR como PREVISTO; rótulo «estimativa simplificada» |
| Obrigações vencidas | Projeção | Rolam para ano seguinte |

Backlog fiscal ampliado: [`BACKLOG_FISCAL_ESTIMATIVA.md`](BACKLOG_FISCAL_ESTIMATIVA.md).

---

## 8. Empréstimos

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Consignado | WhatsApp | Crédito CONFIRMADO + parcelas PREVISTO; confirmação se valor atípico |
| Desconto em folha | Default `true` | Parcela não debita conta nem passivo — premissa: salário líquido |
| Cancelamento | WhatsApp | Soft delete; estorna créditos confirmados acumulados |
| Advisor | WhatsApp / simulações | «Vale a pena consignado?» — cálculo determinístico + narrativa IA |

---

## 9. Dashboard e projeções

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Patrimônio líquido | Card HUD | Ver secção 2 |
| Score J.A.R.V.I.S. | Card HUD | Gamificação por eventos (`ScoreService`) |
| Escudo de energia | Card HUD | Meses de autonomia (saldo ÷ gasto do mês) |
| Trajetória de caixa | Gráfico | Patrimônio hoje + projeção diária até fim do mês |
| Safra M/M+1/M+2 | Gráfico cascata | Saldo fim de mês alimenta o mês seguinte |
| Chat IA | Painel dashboard | Mesmo motor que WhatsApp (`/api/ia-chat`) |
| Ticker mercado | Dashboard | IPCA/Selic; fator no burn da Sentinela |

---

## 10. Simulações, investimentos e relatórios

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Simulação de compra | `/simulacoes` | Média de gasto por **meses com dado** (não `/6` fixo) |
| Advisor grande compra | WhatsApp | Parcelamento vs à vista |
| Investimentos | `/investimentos` | Compara saldo ocioso vs CDB/Selic (dados de mercado) |
| Relatórios PDF | `/relatorios` | Resumo, categorias, exportação IR |
| Amortização sazonal | API | Debt snowball com sazonalidade |

---

## 11. Assinaturas e agendamentos

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Assinaturas | `/assinaturas` | Netflix, Spotify, etc.; após 2 cobranças consecutivas com valor novo → atualiza e notifica |
| Agendamentos | API | Pagamentos futuros idempotentes |

---

## 12. Grupo familiar

| Funcionalidade | Onde | Como funciona |
|----------------|------|----------------|
| Convites | `/familia` | Partilha orçamentos e balanço |
| Racha-contas | `/familia` | Quem deve a quem |

Ver [`MODULO_FAMILIA.md`](MODULO_FAMILIA.md).

---

## 13. WhatsApp (J.A.R.V.I.S.)

| Entrada | Fluxo |
|---------|--------|
| Texto | NLP → comando → resposta formatada `JarvisProtocolService` |
| Áudio | Transcrição → mesmo pipeline |
| Foto | OCR cupom → confirmação sim/não |
| PDF fatura | Importação → varredura → confirmação → lançamentos |
| PDF contracheque | Renda + reconciliação salário |
| Memória | «Anote isso» → pgvector + ciclo de vida |

**Vincular número:** só no app (`/whatsapp-config`, QR Evolution).  
**Webhook:** `POST /api/public/evolution/webhook` (autenticado — [`WEBHOOK_AUTH_EVOLUTION.md`](WEBHOOK_AUTH_EVOLUTION.md)).

Estados pendentes (`sim`/`não`): fatura, cupom, consignado, contenção, Modo Viagem, metas.

---

## 14. Memória semântica (J.A.R.V.I.S.) — app + WhatsApp

| Funcionalidade | Como funciona |
|----------------|----------------|
| Captura automática | Toda mensagem WhatsApp / chat IA → `MemoriaCapturaAutomaticaService` (com guardrails anti-documento) |
| Comandos explícitos | WhatsApp: «anote isso» / «esquece isso» |
| Insights no app | Card no dashboard (`/jarvis/memoria/insights`) |
| Painel tático | Timeline com **refutar** e **restaurar** entradas (`JarvisMemoriaService`) |
| PLANO_FUTURO | Provisão com `mes_alvo` na Sentinela |
| Hábitos | Detecção com suporte mínimo (CONFIRMADA, N dias distintos) |
| RAG híbrido | pgvector + recência + reforço |
| Ciclo de vida | Decaimento, arquivamento, invalidação por exclusão de transação |

Testes: `MemoriaSemanticaPostgresIntegrationTest` (Docker).

---

## 15. Jobs automáticos (cron)

| Horário | Job | Função |
|---------|-----|--------|
| 03:30 | Integridade saldo | Detecta divergências |
| 04:45 | Expurgo audit log | Retenção configurável |
| 06:30 | Débito fixas | Obrigações com débito automático |
| Dia 5 | Sentinela | Relatório disponibilidade real (WhatsApp) |
| Semanal | Modo Viagem | Google Calendar → sugestão de teto |
| Diário | Webhook dedup | Expurga entregas antigas |

---

## 16. Onde está o código

| Área | Pacote / ficheiro principal |
|------|----------------------------|
| Saldo / patrimônio | `SaldoService`, `SaldoMovimentacaoService` |
| Projeções | `SaldoService`, `ComposicaoProjecaoMesService`, `PrevisaoFluxoCaixaService` |
| Faturas | `FaturaService`, `FaturaConciliacaoService`, `FaturaPdfImportService` |
| WhatsApp | `WhatsAppCommandService`, `JarvisProtocolService` |
| Empréstimo | `EmprestimoService` |
| Memória | `CerebroSemanticoService`, `MemoriaCicloVidaService` |
| Catálogo paridade | `WhatsAppAppParityService` |
| Schema runtime | `SchemaAutoPatchService` |
| Regressão | `backend/src/test/java/com/consumoesperto/regressao/` |

---

## 17. Canais: app vs WhatsApp

| Só app | Ambos | Só WhatsApp |
|--------|-------|-------------|
| Vincular QR, Score, Família | Transações, contas, cartões, faturas, metas, orçamentos | OCR cupom, áudio, memória «anote isso», registo consignado |

Lista completa: **`GET /api/whatsapp/paridade`** ou página `/whatsapp-config`.
