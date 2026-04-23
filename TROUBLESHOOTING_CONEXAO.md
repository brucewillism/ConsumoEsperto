# 🔧 Troubleshooting - Erro de Conexão Frontend/Backend

## ❌ Erro Atual
```
Erro de conexão: Não foi possível conectar ao servidor. 
Verifique se o Ngrok está rodando e se a URL está correta.
```

## ✅ Checklist de Verificação

### 1. **Backend está rodando?**
- ✅ Verifique no IntelliJ IDEA se o backend iniciou sem erros
- ✅ Procure por: `Started ConsumoEspertoApplication` no console
- ✅ Verifique se está rodando na porta **8080**

### 2. **Ngrok está rodando?**
- ✅ Abra um terminal e execute: `ngrok http 8080`
- ✅ Copie a URL HTTPS gerada (ex: `https://xxxxx.ngrok-free.app`)
- ✅ A URL deve ser **HTTPS**, não HTTP

### 3. **URLs estão sincronizadas?**
Verifique se a URL do Ngrok está configurada em:

**Frontend:**
- `frontend/src/environments/environment.ts`
- `frontend/src/environments/environment.prod.ts`
- Deve ser: `https://SUA_URL_NGROK.ngrok-free.app/api`

**Backend:**
- `backend/src/main/resources/application.properties`
- Linha: `ngrok.url=${NGROK_URL:https://SUA_URL_NGROK.ngrok-free.app}`

### 4. **Teste Manual da Conexão**

Abra o navegador e acesse:
```
https://SUA_URL_NGROK.ngrok-free.app/api/public/health
```

Se funcionar, você verá uma resposta JSON. Se não funcionar:
- ❌ Backend não está rodando
- ❌ Ngrok não está rodando
- ❌ URL incorreta

### 5. **Verificar Console do Navegador**

Abra o DevTools (F12) e vá na aba **Console**:
- Procure por erros de CORS
- Procure por erros de rede
- Verifique se a URL da requisição está correta

### 6. **Verificar Console do Backend**

No IntelliJ IDEA, procure por:
- Erros de inicialização
- Erros de conexão com banco de dados
- Mensagens de CORS

## 🔍 Comandos Úteis

### Verificar se o backend está rodando:
```bash
curl http://localhost:8080/api/public/health
```

### Verificar se o Ngrok está rodando:
```bash
curl http://localhost:4040/api/tunnels
```

### Testar conexão via Ngrok:
```bash
curl https://SUA_URL_NGROK.ngrok-free.app/api/public/health
```

## 🚀 Solução Rápida

1. **Pare tudo** (Ctrl+C nos terminais)
2. **Inicie o backend** no IntelliJ IDEA
3. **Inicie o Ngrok**: `ngrok http 8080`
4. **Copie a URL HTTPS** do Ngrok
5. **Atualize as URLs** nos arquivos de configuração
6. **Reinicie o frontend** no Visual Studio Code

## 📝 Notas Importantes

- O Ngrok **gratuito** gera uma nova URL a cada reinício
- Sempre use a URL **HTTPS** do Ngrok, não HTTP
- O header `ngrok-skip-browser-warning: true` já está configurado no interceptor
- O CORS está configurado para aceitar `*.ngrok-free.app`

