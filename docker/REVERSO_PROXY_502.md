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

---

## 403 `HTTPS obrigatório` (Atalhos iOS / MacroDroid)

O telemóvel chama `https://consumoesperto.brucew07.com.br/api/...`. O **Nginx/Apache termina o TLS** e fala HTTP com o Spring (`8087`). Sem `X-Forwarded-Proto: https`, o filtro de ingestão vê `request.isSecure()=false` e responde 403.

O Spring Boot 2.7 está com `server.forward-headers-strategy=FRAMEWORK` (env `SERVER_FORWARD_HEADERS_STRATEGY` no Compose). Isso **só funciona se o proxy enviar os headers**.

### Nginx — bloco a ajustar

Não copie `$http_x_forwarded_proto` do cliente (o Atalhos **não** envia esse header). Use `$scheme` do vhost HTTPS:

```nginx
location /api/ {
    proxy_pass         http://127.0.0.1:8087;
    proxy_http_version 1.1;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_set_header   X-Forwarded-Host  $host;
    proxy_read_timeout 360s;
}
```

Ficheiro de exemplo no repo: [`nginx-api-proxy.conf.example`](nginx-api-proxy.conf.example).

Depois: `nginx -t && systemctl reload nginx`.

Confirme o header a chegar ao Spring:

```bash
docker logs consumo_backend --tail 80 | grep https_obrigatorio
```

O WARN inclui `scheme`, `isSecure` e os `X-Forwarded-*` recebidos (sem tokens).

### Dev / local (HTTP)

Não desligue a exigência em produção. No perfil `dev` / `dev-evolution` já vai `false`. Fora disso:

```
MOBILE_CAPTURE_REQUIRE_HTTPS=false
INGEST_NOTIFICACAO_REQUIRE_HTTPS=false
```

