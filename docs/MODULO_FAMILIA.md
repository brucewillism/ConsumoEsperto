# Módulo Família — ConsumoEsperto

Gestão de **grupo familiar/casal**: convites, orçamentos partilhados e **racha-contas** (débitos internos entre membros).

**Rota no app:** `/familia`  
**API base:** `/api/familia`  
**Backend:** `GrupoFamiliarService`, `GrupoFamiliarController`, `SplitBillService`

---

## O que é partilhado

| Dado | Partilhado no grupo? |
|------|----------------------|
| Orçamentos marcados como **«Compartilhado com família/casal»** | Sim |
| Transações, cartões, faturas, renda, metas | Não |
| Dashboard completo de outro membro | Não |

A partilha é **opt-in por orçamento**, não automática para toda a conta.

---

## Fluxo típico

### 1. Criar o grupo

Em **Família** (`/familia`), o utilizador cria um grupo (ex.: «Orçamento do Casal»).  
API: `POST /api/familia` com nome do grupo.

### 2. Convidar membros

**Enviar convite** abre um modal com e-mail ou WhatsApp do convidado.  
API: `POST /api/familia/convites`

O convidado vê o pedido em **Família → Convites recebidos** e aceita ou recusa.  
API: `GET /api/familia/convites` · `POST /api/familia/convites/{membroId}/responder` com `{ "aceitar": true }`

Estados do membro: pendente → **ACEITO** (ou recusado).

### 3. Partilhar orçamentos

Em **Orçamentos** (`/orcamentos`), ao criar ou editar um orçamento, marque **«Compartilhado com família/casal»** (`compartilhado: true`).

Orçamentos partilhados aparecem na secção **«Barras compartilhadas»** em `/familia` para todos os membros aceites do grupo.  
API: `GET /api/familia/orcamentos-compartilhados?mes=&ano=`

Se a secção estiver vazia com membros ACEITO, nenhum orçamento foi marcado como compartilhado ainda.

### 4. Racha-contas (WhatsApp ou app)

O J.A.R.V.I.S. regista divisões de despesas entre membros (ex.: «racha 150 com a Esposa»).  
**Balanço do Grupo** em `/familia` mostra quem deve a quem.  
API: `GET /api/familia/balanco` · `POST /api/familia/debitos/{debitoId}/liquidar`

---

## APIs REST

| Método | Path | Função |
|--------|------|--------|
| `GET` | `/api/familia` | Grupo do utilizador autenticado |
| `POST` | `/api/familia` | Criar grupo |
| `POST` | `/api/familia/convites` | Convidar por e-mail/WhatsApp |
| `GET` | `/api/familia/convites` | Convites pendentes para mim |
| `POST` | `/api/familia/convites/{membroId}/responder` | Aceitar/recusar |
| `GET` | `/api/familia/orcamentos-compartilhados` | Orçamentos partilhados do grupo |
| `GET` | `/api/familia/balanco` | Balanço racha-contas |
| `POST` | `/api/familia/debitos/{debitoId}/liquidar` | Marcar débito como liquidado |

---

## Paridade WhatsApp

Família é **APP_ONLY** no catálogo (`WhatsAppAppParityService`): convites e gestão do grupo são feitos no app.  
Comandos de **racha-contas** no WhatsApp atualizam débitos internos visíveis no balanço do grupo.

---

## Frontend

| Ficheiro | Função |
|----------|--------|
| `frontend/src/app/pages/familia/` | Página principal do módulo |
| `frontend/src/app/pages/familia/convidar-familiar-dialog.*` | Modal de convite |
| `frontend/src/app/pages/orcamentos/novo-orcamento-dialog.*` | Checkbox `compartilhado` |

---

## Limitações atuais

- Um utilizador pertence a **um** grupo por vez (modelo actual).
- Partilha limitada a **orçamentos**; não há visão consolidada de transações do parceiro.
- Convite por WhatsApp depende do número estar registado ou identificável no sistema.

**Última revisão:** junho/2026
