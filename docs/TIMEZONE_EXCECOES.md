# Exceções de timezone (`now()` fora de `AppTimeZone`)

Migrados para `AppTimeZone` (fuso `America/Sao_Paulo`): projeções, saldo, faturas, renda, detecção de recorrência, colchão sazonal, empréstimos.

## Permanecem com `now()` de propósito

| Uso | Justificativa |
|-----|---------------|
| `dataCriacao` / `dataAtualizacao` em entidades (`Transacao`, `Usuario`, …) | Timestamp técnico de auditoria, não data de negócio |
| `ApiError.timestamp`, `AuditLog.createdAt` | Log/telemetria |
| `UsuarioSessaoContextoService.expiraEm` | TTL de sessão (duração relativa) |
| `NotificacaoService` janela 24h | Duração técnica de lembrete |
| `MovimentacaoSaldoLog.criadoEm` | Já usa `OffsetDateTime.now(AppTimeZone.BR)` |
| `Instant.now()` em métricas HTTP | Duração de request |

Qualquer **nova** data de vencimento, competência, projeção ou filtro de período deve usar `AppTimeZone`.
