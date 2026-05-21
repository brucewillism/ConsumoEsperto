# Assets de loading (versionados no Git)

| Ficheiro | Uso |
|----------|-----|
| **`loading.svg`** | **Principal** — anel LOADING, fundo **transparente**, animação SMIL. |
| `loading.gif` | Fallback opcional (se existir). Preferir GIF com fundo transparente. |

Coloque ficheiros em `frontend/src/assets/loading/`. Não use a pasta `tools/` — ela não vai para o Git/Docker.

Para substituir o visual: edite `loading.svg` ou troque por outro SVG/PNG/WebP transparente e atualize `loadingAssetUrl` em `environment*.ts`.
