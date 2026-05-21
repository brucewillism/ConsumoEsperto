# Assets de loading (versionados no Git)

Coloque aqui o GIF animado do Jarvis:

- `loading.gif` — usado pelo overlay global e pelos ecrãs com `<app-loading-indicator>`.

**Importante:** o ficheiro deve ter dimensões reais (não placeholder 1×1). Se o GIF for inválido ou estiver ausente, a app usa o spinner CSS automaticamente.

No Docker Compose, estes ficheiros entram no build via `src/assets/loading/` (não use a pasta `tools/` — ela não vai para o Git).
