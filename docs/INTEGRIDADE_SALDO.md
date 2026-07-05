# Integridade de saldo, auditoria e débito automático

Referência das proteções em torno de `ContaBancaria.saldo_atual`: escrita atômica, trilha de auditoria, detecção/reparo de divergências, alertas operacionais e débito automático de obrigações fixas.

**Última revisão:** julho/2026

---

## 1. Escrita atômica de saldo (concorrência)

Toda mutação de saldo passa pelo ponto único **`SaldoMovimentacaoService`**, que trava a conta com `SELECT ... FOR UPDATE` (`ContaBancariaRepository.findByIdForUpdate`, lock pessimista) antes de aplicar o delta. Isso elimina *lost updates* quando app, WhatsApp e jobs batem na mesma conta ao mesmo tempo.

- Transferências entre contas travam **ambas** as contas em ordem de ID (evita deadlock).
- Débitos validam cheque especial (`temSaldoSuficiente`) dentro do lock.
- Teste de integração com Testcontainers: `SaldoConcorrenciaPostgresIntegrationTest` (N threads concorrentes, nenhuma escrita perdida).

## 2. Trilha de auditoria — `movimentacao_saldo_log`

Tabela **append-only** gravada a cada mutação de saldo (criada via `SchemaAutoPatchService`, patch idempotente):

| Coluna | Conteúdo |
|--------|----------|
| `conta_id`, `usuario_id`, `transacao_id` (nullable) | Quem/onde |
| `delta`, `saldo_antes`, `saldo_depois` | Quanto (encadeado) |
| `origem` | `APP` / `WHATSAPP` / `JOB` / `REPARO` / `IMPORTACAO` / `SISTEMA` |
| `tipo_operacao` | `CRIACAO`, `EDICAO`, `EXCLUSAO`, `CREDITO_DIRETO`, `TRANSFERENCIA_SAIDA/ENTRADA`, `RECONCILIACAO` |
| `criado_em` | `TIMESTAMPTZ` (America/Sao_Paulo) |

- A origem é propagada por `SaldoMovimentacaoContexto` (ThreadLocal) — webhook Evolution marca `WHATSAPP`, jobs marcam `JOB`, reparo marca `REPARO`.
- Consulta por conta: `GET /api/reparo-financeiro/conta/{contaId}/movimentacoes`.
- Expurgo diário 04:45 (`MovimentacaoSaldoLogPurgeService`), retenção `SALDO_AUDIT_RETENTION_DAYS` (padrão **730** dias, mínimo 90).
- Falha ao gravar auditoria **não** bloqueia a operação financeira (log ERROR).

## 3. Detecção de divergências (reconciliação)

**`SaldoIntegridadeService`** compara `saldo_atual` com a fórmula da fonte da verdade (`saldo_inicial` + transações CONFIRMADAS + transferências):

- Job diário **03:30** (`consumoesperto.saldo.integridade.cron`); correção automática desligada por padrão (`consumoesperto.saldo.integridade.auto-corrigir=false`).
- Relatório read-only: `GET /api/contas-bancarias/integridade`.
- Quando encontra divergência, o relatório referencia a **última movimentação** do audit log (quando/qual origem) e dispara alerta operacional (secção 5).

## 4. Reparo financeiro (fail-closed)

**`SaldoReparoService`** + **`ReparoFinanceiroController`** (`/api/reparo-financeiro`):

| Endpoint | Função |
|----------|--------|
| `GET /relatorio` | Dry-run read-only: contas divergentes + faturas PAGAS com `valorPago` ≠ soma dos `PAGAMENTO_FATURA` reais |
| `POST /conta/{contaId}` | Reparo pontual de uma conta (recomputa pela fórmula da fonte da verdade) |
| `POST /faturas` | Corrige `valorPago` das faturas para a soma dos pagamentos reais |
| `GET /conta/{contaId}/movimentacoes` | Trilha de auditoria da conta |

Pré-condições para **aplicar** (senão devolve só o dry-run):

1. `SALDO_REPARO_ENABLED=true` no servidor (padrão **false**);
2. `confirmar=true` e `backupConfirmado=true` no corpo do POST.

O reparo é **idempotente** (segunda execução não muda nada) e grava no audit log com origem `REPARO`. Fluxo recomendado: relatório → backup do Postgres → ligar flag → aplicar por conta → desligar flag.

## 5. Alertas operacionais

**`AlertaOperacionalService`** — baseline é log `ERROR [ALERTA-OP]` estruturado; opcionalmente faz POST JSON num webhook (ntfy, Discord, etc.), com cooldown anti-tempestade:

| Alerta | Gatilho |
|--------|---------|
| `DIVERGENCIA_SALDO` | Job de reconciliação encontra contas divergentes |
| `WEBHOOK_AUTH_FALHA` | Webhook Evolution rejeitado (ver [`WEBHOOK_AUTH_EVOLUTION.md`](WEBHOOK_AUTH_EVOLUTION.md)) |

Config (`.env` / `application.properties`):

```
ALERTAS_WEBHOOK_ENABLED=false     # true para ativar o POST externo
ALERTAS_WEBHOOK_URL=              # endpoint que recebe o JSON
ALERTAS_COOLDOWN_MINUTES=15
ALERTAS_TIMEOUT_MS=5000
```

Sem webhook configurado nada quebra — os alertas ficam nos logs.

## 6. Débito automático de obrigações fixas

As despesas fixas (**Perfil → Obrigações Fixas**) por padrão só entram na projeção *Futuro provável*. Com **débito automático** ligado, viram gasto real no vencimento:

- Campos novos em `despesas_fixas`: `debito_automatico` (boolean, padrão false) e `conta_bancaria_id` (nullable → conta padrão do usuário).
- Job diário **06:30** (`DespesaFixaDebitoAutomaticoService`) — meia hora após o job de agendamentos para não concorrer pelo mesmo saldo:
  1. No dia efetivo do vencimento (dia 31 → 30/28 em meses curtos), cria transação DESPESA CONFIRMADA `Despesa fixa: <descrição>` e debita a conta pelo fluxo normal (lock + audit log, origem `JOB`);
  2. **Idempotente** pela chave natural (usuário + descrição + data + valor) — reprocessar o dia não debita 2×;
  3. Saldo insuficiente ou conta inativa → não debita e avisa no WhatsApp; sucesso também notifica com o saldo restante.
- No app: seletor **Conta a debitar** + checkbox **Débito automático** no modal de Obrigações Fixas; a lista mostra ⚡ + conta nas ligadas.

**Atenção:** com débito automático ligado, não lance o mesmo pagamento manualmente no dia — duplicaria o gasto.

## 7. Edição de saldo no app

Na tela **Contas**, o botão «Recalcular saldo» foi removido (realinhava só o histórico interno e nunca mudava o valor exibido — confundia). Agora:

- **Editar** → campo **«Saldo atual (R$)»** já vem preenchido; corrigir e salvar chama `POST /api/contas-bancarias/{id}/sincronizar-saldo`, que ajusta o saldo e realinha `saldo_inicial` (auditado como `RECONCILIACAO`).
- Correção em lote continua em «Corrigir saldos» quando há anomalia detectada.

## 8. Rede de regressão

Testes que travam os bugs financeiros críticos (P0) em `backend/src/test/java/com/consumoesperto/regressao/`:

| Teste | Bug coberto |
|-------|-------------|
| `ExclusaoLoteEstornoRegressionTest` | Exclusão em lote estorna só CONFIRMADAS, sem estorno duplo |
| `ReparoPosBugPreservaAberturaRegressionTest` | Reparo preserva `saldo_inicial` |
| `AgendamentoIdempotenteRegressionTest` | Agendamento não debita 2× e não bloqueia pagamento legítimo |
| `FaturaPagaSemCaixaRegressionTest` | Fatura PAGA via API não inventa `valorPago` |
| `CompetenciaCompraCartaoRegressionTest` | Compra pós-fechamento vai para o ciclo seguinte |
| `RendaMediaMovel90DiasRegressionTest` | Janela de 90 dias normalizada para 30 |

Integração com Postgres real (Testcontainers; requer Docker/Podman): `backend/src/test/java/com/consumoesperto/integration/` — concorrência de saldo, encadeamento do audit log e dedup de webhook. Sem Docker os testes se auto-desabilitam (`@EnabledIf`).
