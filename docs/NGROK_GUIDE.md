# Guia de Uso do Ngrok - ConsumoEsperto

## O que é o Ngrok?

O ngrok é uma ferramenta que permite expor aplicações locais para a internet de forma segura. No contexto do ConsumoEsperto, ele é usado para expor a API do backend Spring Boot durante o desenvolvimento.

## Por que usar o Ngrok?

1. **Testes Remotos**: Permite testar a API de forma remota durante o desenvolvimento
2. **Compartilhamento**: Facilita o compartilhamento da API com outros desenvolvedores
3. **Integração Frontend**: Permite que o frontend Angular se conecte à API local de forma remota
4. **Debugging**: Facilita o debugging de problemas de integração

## Instalação do Ngrok

### Windows
1. Acesse: https://ngrok.com/download
2. Baixe a versão para Windows
3. Extraia o arquivo `ngrok.exe` para uma pasta no PATH ou adicione ao PATH
4. Registre-se no ngrok.com para obter um authtoken

### Configuração Inicial
```bash
# Configure seu authtoken (obtenha em https://dashboard.ngrok.com/get-started/your-authtoken)
ngrok config add-authtoken YOUR_AUTH_TOKEN
```

## Uso com ConsumoEsperto

### 1. Iniciar o Backend
```bash
cd backend
./mvnw spring-boot:run
```

### 2. Iniciar o Ngrok
```bash
# Em outro terminal
ngrok http 8080
```

### 3. Configurar o Frontend
Após iniciar o ngrok, você receberá uma URL como:
```
https://abc123.ngrok.io
```

Configure o frontend para usar esta URL:

```typescript
// Em frontend/src/environments/environment.ts
export const environment = {
  production: false,
  apiUrl: 'https://abc123.ngrok.io/api'
};
```

### 4. Testar a Integração
```bash
cd frontend
ng serve
```

## Configuração Avançada

### Usando Arquivo de Configuração
Crie um arquivo `ngrok.yml` na raiz do projeto:

```yaml
version: "2"
authtoken: YOUR_NGROK_AUTH_TOKEN
tunnels:
  consumo-esperto-api:
    addr: 8080
    proto: http
    subdomain: consumo-esperto-api
    inspect: true
```

Execute com:
```bash
ngrok start --config ngrok.yml consumo-esperto-api
```

### Scripts Automatizados

#### PowerShell (Windows)
```powershell
# Execute o script
.\scripts\start-ngrok.ps1
```

#### Bash (Linux/Mac)
```bash
# Crie um script similar
#!/bin/bash
echo "Iniciando ngrok..."
ngrok http 8080
```

## Monitoramento

### Interface Web do Ngrok
Acesse: http://localhost:4040 para ver:
- Requisições em tempo real
- Logs detalhados
- Métricas de uso

### Logs
```bash
# Ver logs detalhados
ngrok http 8080 --log=stdout
```

## Segurança

### Configurações Recomendadas
1. **Autenticação**: Configure autenticação básica se necessário
2. **HTTPS**: O ngrok fornece HTTPS automaticamente
3. **Rate Limiting**: Configure limites de taxa se necessário

### Exemplo de Configuração com Autenticação
```yaml
tunnels:
  consumo-esperto-api:
    addr: 8080
    proto: http
    auth: "username:password"
    inspect: true
```

## Troubleshooting

### Problemas Comuns

1. **Porta já em uso**
   ```bash
   # Verificar se a porta 8080 está livre
   netstat -an | findstr :8080
   ```

2. **CORS Issues**
   - Configure o CORS no backend para aceitar a URL do ngrok
   - Adicione a URL do ngrok nas configurações de CORS

3. **Token inválido**
   ```bash
   # Reconfigurar o token
   ngrok config add-authtoken YOUR_NEW_TOKEN
   ```

### Logs de Debug
```bash
# Executar com logs detalhados
ngrok http 8080 --log=stdout --log-level=debug
```

## Integração com CI/CD

### GitHub Actions
```yaml
name: Test with ngrok
on: [push]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Start ngrok
        uses: ngrok/action@v1
        with:
          authtoken: ${{ secrets.NGROK_AUTH_TOKEN }}
          port: 8080
```

## Recursos Adicionais

- [Documentação Oficial do Ngrok](https://ngrok.com/docs)
- [Dashboard do Ngrok](https://dashboard.ngrok.com/)
- [Exemplos de Configuração](https://ngrok.com/docs/using-ngrok/)

## Suporte

Para problemas específicos com ngrok:
- [Fórum do Ngrok](https://ngrok.com/community)
- [GitHub Issues](https://github.com/inconshreveable/ngrok/issues)
