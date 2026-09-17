# Política das visões do dashboard (Mensal × Geral)

**Última revisão:** setembro/2026  
Complementa [`POLITICA_STATUS_TRANSACAO.md`](POLITICA_STATUS_TRANSACAO.md), [`POLITICA_PROVISAO.md`](POLITICA_PROVISAO.md) e [`CALCULOS_FINANCEIROS.md`](CALCULOS_FINANCEIROS.md).

## Perguntas

| Visão | Modo API | Responde |
|-------|----------|----------|
| **Mensal** (padrão na UI) | `MONTHLY` | O que pesa ou ajuda **neste mês**? |
| **Geral** (comportamento legado da tela) | `GENERAL` | Como está a **saúde financeira total**? |

Endpoint: `GET /api/dashboard?view=MONTHLY\|GENERAL`. Sem `view`, o backend devolve **GENERAL** (compatível com consumidores antigos). A UI persiste a última escolha.

Números vêm só dos serviços já existentes (`ComposicaoProjecaoMesService`, `SaldoService.patrimonioLiquido`, `sumPassivoEmprestimoAtivo`, `LiveInvoiceProjectionService`, `SafeToSpendService`, `PrevisaoFluxoCaixaService`). **Proibido** uma segunda fórmula de projeção ou de património.

## Taxonomia única de obrigações (R1)

Cada obrigação pertence a **exactamente uma** categoria. Totais agregados somam categorias **disjuntas**.

| Categoria | O que entra | O que **não** entra |
|-----------|-------------|---------------------|
| **Cartão / fatura** | Fatura aberta/parcial/vencida: confirmado + pendente = projetado. Inclui **parcelas de compras no cartão** (`grupoParcelaId` / fatura). | Empréstimo com `emprestimo_id`; despesa fixa; assinatura; agendamento. |
| **Empréstimos / financiamentos** | Transações com `emprestimo_id` (pessoal, consignado, financiamento). | Compra parcelada no cartão (vive na fatura). |
| **Fixas / assinaturas / agendamentos** | Débitos recorrentes **fora de cartão**: despesa fixa, assinatura, agendamento de boleto/Pix. | Parcelas de fatura; parcelas de empréstimo. |

**Teste de ouro:** uma compra parcelada no cartão aparece **uma vez** na Mensal (dentro da fatura do mês) e **uma vez** na Geral (exposição de cartão). A soma dos três blocos é o total comprometido da visão.

### Consignado (`descontoEmFolha`)

| Flag | Mensal (exibição) | Mensal (caixa / safe-to-spend) | Geral |
|------|-------------------|--------------------------------|-------|
| `true` (padrão) | **Parcela do mês** visível, marcada *desconto em folha* | **Não** entra como saída de conta | Saldo devedor entra no passivo |
| `false` | Parcela do mês | Entra na projeção de caixa (`ComposicaoProjecaoMesService`) | Saldo devedor entra no passivo |

A flag **não** tira a parcela do património líquido. Ver [`CALCULOS_FINANCEIROS.md`](CALCULOS_FINANCEIROS.md) §5.

## Visão Mensal — o que mostrar

Referência: mês corrente (`AppTimeZone`). Histórico só `CONFIRMADA`; projeção rotulada como projeção (`PREVISTO` permitido).

- Receitas e despesas **confirmadas** do mês; resultado; **projeção do mês** (`SaldoService.calcularProjecaoMes` / composição bottom-up).
- Fatura do mês: confirmado, pendente, projetado, vencimento, dias até fechamento. **Só faturas cuja competência (fechamento/vencimento) cai neste mês** — fatura do mês seguinte não aparece na Mensal.
- **Parcela do mês** de cada empréstimo (quantidade e valor) — **sem** saldo devedor nem total contratado.
- Fixas, assinaturas e agendamentos do mês.
- Orçamento do mês; **safe-to-spend do mês** (rótulo explícito; fórmula de `SafeToSpendService`).
- Alertas/insights **só do mês**.

## Visão Geral — o que mostrar

- **Património decomposto (R4):** Ativos (saldos em contas) − Passivos (saldo devedor de empréstimos, por tipo) = **Património líquido** (`SaldoService.patrimonioLiquido()`).
- **Total em contas** = os mesmos ativos (não soma fatura nem empréstimo).
- **Total investido** = ativos − reservas (liquidez imediata). O catálogo de contas só tem CORRENTE / POUPANÇA / DINHEIRO — não há tipo INVESTIMENTO; o residual tende a zero. Lançamentos `TipoTransacao.INVESTIMENTO` são **saída de caixa**, não um segundo estoque (somá-los aos ativos seria dupla contagem).
- Exposição de cartão (faturas em aberto) **não** entra no património (já era a regra de caixa); entra na **dívida total**.
- **Dívida total** = saldo devedor de empréstimos + exposição de cartão (sem interseção).
- Por empréstimo: saldo devedor, total contratado, parcelas restantes, valor da parcela, tipo (consignado em folha / que debita conta / financiamento).
- Score, metas, Escudo de Energia (`mesesEscudoEnergia` já calculado na Sentinela).
- Liquidez imediata ≠ património. Métrica análoga ao safe-to-spend chama-se **folga patrimonial** (não “safe-to-spend”).

## Insights (não misturar)

- **Mensal:** categoria que mais pesou, orçamento a estourar, fatura alta, safe-to-spend baixo, parcela relevante, saldo projetado até ao fim do mês.
- **Geral:** evolução patrimonial, variação da dívida total, progresso de metas, tendência de Score, reserva/Escudo baixo.

## Notificações J.A.R.V.I.S.

Reutilizam `NotificationOrchestratorService` (opt-out, horário silencioso, digest, fila se WhatsApp offline).

| Brief | Hash de idempotência | Preferência |
|-------|----------------------|-------------|
| Semanal visão mensal (`MONTHLY_WEEKLY_BRIEF`) | `MONTHLY_WEEKLY_BRIEF:{userId}:{yyyy-'W'ww}` | `RESUMO_SEMANAL` |
| Dia 28 visão geral (`GENERAL_MONTHLY_BRIEF`) | `GENERAL_MONTHLY_BRIEF:{userId}:{yyyy-MM}` | `DIGEST_MENSAL_SENTINELA` |

E.D.I.T.H. pode narrar o texto; **nunca calcula**. Offline → template determinístico. Token Suppressor só atrás da E.D.I.T.H.

## Total da categoria recorrente

O **número** da categoria «Fixas / assinaturas / agendamentos» no comprometido do mês é o das **despesas fixas** da composição (`ComposicaoProjecaoMesService.partesObrigacoesMes().fixas()`), a mesma fonte da projeção. Assinaturas e agendamentos fora de cartão aparecem como detalhe. Agendamento com `cartaoCreditoId` pertence à fatura, não a esta categoria.

## Auditoria de reuso (R2)

| Área | O que já existia | Onde | Como as visões reutilizam |
|------|------------------|------|---------------------------|
| Dashboard UI | Central de comando, cards, safra, HUD | `frontend/.../dashboard.component.*`, `dashboard.service.ts` | Toggle recarrega `GET /api/dashboard?view=`; cards vêm do DTO |
| Projeção do mês | Composição bottom-up + Anti-Susto | `ComposicaoProjecaoMesService`, `SaldoService.calcularProjecaoMes` | Mensal: `projecaoMes` e `partesObrigacoesMes` |
| Patrimônio | Ativos − passivo de empréstimo | `SaldoService.patrimonioLiquido`, `sumPassivoEmprestimoAtivo` | Geral: decompõe ativos = líquido + passivo |
| Safe-to-spend | Margem sobre disponibilidade real | `SafeToSpendService` ← `PrevisaoFluxoCaixaService` | Só Mensal (`safeToSpendMes`). Geral usa **folga patrimonial** (liquidez imediata) |
| Faturas | Confirmado + pendente = projetado | `LiveInvoiceProjectionService`, `FaturaRepository.sumValorFaturasPendentes*` | Mensal: fatura do mês. Geral: exposição de cartão na dívida total |
| Empréstimos | Parcelas `emprestimo_id`; folha fora do caixa | `TransacaoRepository.sumParcelasEmprestimoPrevistasNoMes`, `EmprestimoService` | Mensal: parcela do mês (inclui folha, sinalizada). Geral: saldo devedor / contratado / restantes |
| Fixas / assinaturas / agendamentos | Cadastros e soma restante no mês | `DespesaFixaService`, `AssinaturaRecorrenteService`, `AgendamentoPagamentoService` | Mensal: listas + total de fixas da composição |
| Score / Sentinela / Escudo | Score, margem, meses de reserva | `ScoreService`, `SentinelaProtocolService`, `PrevisaoFluxoCaixaService.mesesEscudoEnergia` | Geral: Score, Escudo, alertas patrimoniais |
| Orçamento / metas | Limites do mês, poupança mensal | `OrcamentoService`, `MetaFinanceiraRepository` | Mensal: orçamento e impacto no mês. Geral: progresso |
| J.A.R.V.I.S. / jobs | Orquestrador, opt-out, digest, fila | `NotificationOrchestratorService`, `ProactiveFinancialJobs` | Brief semanal (segunda 08:00) e dia 28; hashes novos; mesmo canal |
| E.D.I.T.H. | Gateway + `FINANCIAL_BRIEF` | `CognitiveGatewaySelector`, `EdithSourceActions` | Narra o template; fallback determinístico. Sem Token Suppressor directo |
