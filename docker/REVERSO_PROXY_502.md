# 502 Bad Gateway (Apache / Nginx ↔ backend Docker)

O erro **«502 Bad Gateway»** ao chamar `https://seu-domínio/api/...` quase sempre significa: **o proxy no host HTTPS não conseguiu obter uma resposta válida** do Spring Boot atrás da porta **`8087`**.

## O que verificar

1. **O backend já está UP e a responder dentro do servidor**
   ```bash
   curl -sf http://127.0.0.1:8087/api/auth/status && echo OK
   ```
   - Se falhar aqui → veja logs: `docker logs consumo_backend --tail 200`
   - O primeiro arranque pode demorar (Flyway, contexto Spring); espere até ~2 min após deploy.

2. **O Apache está a apontar para a porta certa (`8087`, não `8080` nem `8181`)**
   - `:8181` é o container **só Angular** (Nginx estático).
   - `8087` é o **Spring Boot**.

   Exemplo (ajuste ao teu VirtualHost):
   ```apache
   ProxyPreserveHost On
   RequestHeader set X-Forwarded-Proto "https"

   ProxyPass        /api/ http://127.0.0.1:8087/api/
   ProxyPassReverse /api/ http://127.0.0.1:8087/api/
   ```

3. **Tempo máximo no proxy**
   Se o JVM demora a subir ou pedidos ficam pendurados no boot, aumente timeouts no proxy (Apache: `ProxyTimeout`, Nginx: `proxy_read_timeout`).

4. **`docker-compose` atual**
   O serviço `backend` inclui um **healthcheck** em `GET /api/auth/status`. O frontend só sobe quando o backend estiver «healthy». Isto ajuda contra 502 só por clicar antes do Spring estar pronto.
