# Assets de loading

| Ficheiro | Uso |
|----------|-----|
| **`loading.svg`** | **Principal** — fundo transparente, animação SMIL (anel + LOADING). |
| `loading.gif` | Fallback — [loading-1](https://usagif.com/gif/loading-1/) (USAGIF); fundo teal opaco, tratado com `mix-blend-mode: screen` se carregar. |

Origem do GIF: https://usagif.com/wp-content/uploads/loading-1.gif

Para trocar o visual, substitua `loading.gif` e mantenha `loadingAssetUrl` em `environment*.ts`.
