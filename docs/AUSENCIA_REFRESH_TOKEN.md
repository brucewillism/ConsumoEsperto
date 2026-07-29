# Ausência de endpoint `/api/auth/refresh`

**Classificação:** `AUSENTE POR DECISÃO DE ARQUITETURA`

## Contrato atual

`AuthController` expõe apenas:

- `POST /api/auth/login`
- `POST /api/auth/registro`

Não existe `GET/POST /api/auth/refresh`. O smoke trata esse fluxo como **funcionalidade ausente**, não como falha.

## Compatibilidade com o restante do sistema

| Aspecto | Comportamento atual | Impacto |
| --- | --- | --- |
| Duração do JWT | `jwt.expiration=86400000` (24 h) via `application.properties` | Sessão expira após 24 h; usuário refaz login |
| Refresh JWT (config) | `jwt.refresh-expiration` existe em `application-secure.properties`, mas **sem endpoint** que o utilize | Config legado/planejamento; não exposto na API pública |
| Logout | Frontend limpa token local (`AuthService`); backend stateless | Logout efetivo no cliente |
| Expiração | Token inválido → HTTP 401; frontend redireciona para `/login` | Comportamento esperado |
| Login Google | `OAuth2Controller` emite novo JWT após OAuth | Fluxo independente de refresh |
| WhatsApp / integrações | Autenticação por webhook/token de serviço, não JWT de usuário web | Não depende de refresh web |

## Decisão

Manter **sem refresh token** nesta versão. Renovação de sessão = novo login (credencial ou Google). Isso é compatível com arquitetura stateless JWT de curta/média duração (24 h).

## Quando revisar

Implementar refresh apenas se houver requisito explícito de sessão longa sem re-login (ex.: mobile nativo, PWA offline prolongado). Não implementar nesta execução de validação.
