# Frontend — modais e overlay (Angular Material / CDK)

Como os diálogos (`MatDialog`) funcionam no ConsumoEsperto e o que evitar ao alterar estilos globais.

---

## Sintomas que este guia cobre

- Scroll (roda do rato, touchpad) não funciona dentro do modal ou no `mat-select`.
- Clicar na barra de scroll fecha o modal.
- Nenhum clique funciona dentro ou fora do modal (regressão global).

---

## Arquitetura actual

### 1. CSS obrigatório do CDK

O ficheiro **`node_modules/@angular/cdk/overlay-prebuilt.css`** está incluído em `frontend/angular.json` (secção `styles`).

Sem este CSS, a cadeia de `pointer-events` do CDK não funciona:

- contentor do overlay: `pointer-events: none`
- painel e backdrop: `pointer-events: auto`

**Não remova** este import nem substitua por hacks manuais de `pointer-events` em `styles.scss`.

### 2. Configuração global do diálogo

Em `frontend/src/app/app.config.ts`:

- `scrollStrategy: noop()` — o scroll da página é bloqueado por classe CSS, não por `position: fixed` agressivo.
- `disableClose: true` — evita fechar ao clicar fora por defeito (comportamento controlado por diálogo).

### 3. Classe `ce-modal-open`

`frontend/src/app/shared/ce-overlay-scroll.util.ts` adiciona/remove `ce-modal-open` em `<html>` quando há diálogo aberto.

Em `frontend/src/styles.scss`:

```scss
html.ce-modal-open body {
  overflow: hidden;
}

html.ce-modal-open .shell-loading-layer {
  visibility: hidden;
}
```

### 4. Z-index do overlay vs loading global

O overlay CDK usa z-index **10150** em `styles.scss` (acima do padrão 1300).

A camada **`.shell-loading-layer`** (`app.component.scss`) tinha z-index **10050** — quando o loading global estava activo, ficava **por cima** do modal e bloqueava cliques.

Solução: z-index do overlay elevado + esconder loading layer com `ce-modal-open`.

### 5. Utilitários de diálogo

`frontend/src/app/shared/ce-form-dialog.util.ts`:

- `openCeFormDialog` — abre diálogos de formulário com padrão consistente.
- `wireCeDialogBehavior` — estabiliza campos de formulário no diálogo.

**Evitar** reintroduzir wheel trap manual, `elementFromPoint` no backdrop ou `position: fixed` no `body` sem necessidade.

---

## Checklist ao alterar modais

1. `overlay-prebuilt.css` presente em `angular.json`?
2. Overlay z-index > `.shell-loading-layer`?
3. Com modal aberto, `html` tem classe `ce-modal-open`?
4. Sem regras globais que forcem `pointer-events: none` no `cdk-overlay-container`?
5. Após deploy frontend: **Ctrl+Shift+R** (cache do browser).

---

## Deploy de correções no frontend

```bash
git pull
docker compose up -d --build frontend
```

Em desenvolvimento local: `cd frontend && npm start` (porta **14200**).

---

## Ficheiros relevantes

| Ficheiro | Papel |
|----------|-------|
| `frontend/angular.json` | Import `overlay-prebuilt.css` |
| `frontend/src/styles.scss` | z-index overlay, `ce-modal-open` |
| `frontend/src/app/app.config.ts` | `MatDialog` defaults |
| `frontend/src/app/shared/ce-overlay-scroll.util.ts` | Toggle `ce-modal-open` |
| `frontend/src/app/shared/ce-form-dialog.util.ts` | Abertura padronizada de diálogos |
| `frontend/src/app/app.component.scss` | `.shell-loading-layer` |

**Última revisão:** junho/2026 (correcção raiz: commit `a650982`)
