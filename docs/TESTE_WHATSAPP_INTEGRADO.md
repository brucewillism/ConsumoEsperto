# Roteiro de teste integrado — WhatsApp / Evolution / Jarvis

## Pré-requisitos (BLOQUEADO EXTERNAMENTE sem estes itens)

| Variável | Descrição |
|----------|-----------|
| `EVOLUTION_URL` | URL da Evolution (ex.: `http://localhost:8080`) |
| `EVOLUTION_APIKEY` | Chave de autenticação |
| `EVOLUTION_INSTANCE` | Nome da instância pareada |
| Backend Spring | Perfil `dev-evolution`, porta **8081** |
| PostgreSQL | Banco `consumoesperto` |
| Usuário app | WhatsApp vinculado em Perfil ou `usuario_ai_config` |

## Webhook

- **URL:** `http://<host>:8081/api/public/evolution/webhook`
- **Evento:** `messages.upsert`
- **Header:** conforme config Evolution (`apikey`)

## Testes internos (VALIDADO via Maven)

- `EvolutionWebhookDedupServiceTest` — deduplicação
- `AgendamentoIdempotenteRegressionTest` — idempotência job
- `JarvisFastPathGoldenSetTest` — comandos determinísticos
- `MemoriaCapturaFalhaAlertaTest` — fallback memória

## Teste manual — texto

1. Enviar: `qual meu saldo?`
2. Log esperado: `Evolution webhook` + resposta com saldo
3. Duplicar mesma mensagem (mesmo `messageId`) → segunda ignorada

## Teste manual — transação

1. Enviar: `gastei 50 no mercado hoje`
2. Confirmar se solicitado
3. Verificar transação em `/transacoes`

## Teste manual — PDF fatura

1. Enviar PDF de fatura suportada (Nubank/Inter/Itaú)
2. Confirmar importação no app
3. Verificar lançamentos na fatura

## Idempotência

- Reenviar webhook idêntico → status 200, sem duplicar transação
- Ver logs `[WEBHOOK-DEDUP]` ou equivalente

## IA indisponível

- Desligar chave OpenAI/Gemini
- Comandos determinísticos (saldo, faturas) devem responder
- OCR/chat deve usar fallback ou mensagem clara de indisponibilidade
