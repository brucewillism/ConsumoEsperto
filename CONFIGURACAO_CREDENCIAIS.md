# 🔐 Configuração de Credenciais do Mercado Pago

## 📋 **Visão Geral**

O sistema agora permite configurar as credenciais do Mercado Pago **diretamente pela interface web**, eliminando a necessidade de editar arquivos `.properties`.

## 🎯 **Como Configurar**

### **1. Acesse a Página de Configuração**
- Navegue para: `/bank-config` no frontend
- Ou procure por "Configuração de APIs Bancárias" no menu

### **2. Configure o Mercado Pago**
- Clique no card do **Mercado Pago**
- Preencha os campos:
  - **Client ID**: `4223603750190943` (já preenchido)
  - **Client Secret**: `SEU_CLIENT_SECRET_REAL` ⚠️ **OBRIGATÓRIO**
  - **User ID**: `209112973` (já preenchido)
  - **API URL**: `https://api.mercadopago.com/v1` (já preenchido)
  - **Auth URL**: `https://api.mercadopago.com/authorization` (já preenchido)
  - **Token URL**: `https://api.mercadopago.com/oauth/token` (já preenchido)
  - **Redirect URI**: `https://29e1b0b32eb8.ngrok-free.app/api/auth/mercadopago/callback` (já preenchido)
  - **Scope**: `read,write` (já preenchido)

### **3. Salve e Ative**
- Clique em **"Salvar"**
- Clique em **"Ativar"** para tornar a configuração ativa
- Clique em **"Testar"** para verificar a conexão

## 🔑 **Onde Obter as Credenciais**

### **Mercado Pago Developers**
1. Acesse: [https://www.mercadopago.com.br/developers](https://www.mercadopago.com.br/developers)
2. Faça login na sua conta
3. Vá em **"Suas aplicações"**
4. Copie o **Client ID** e **Client Secret**

## 🗄️ **Estrutura do Banco**

### **Tabela: `bank_api_configs`**
```sql
CREATE TABLE bank_api_configs (
    id BIGSERIAL PRIMARY KEY,
    banco VARCHAR(50) NOT NULL,           -- 'MERCADOPAGO'
    usuario_id BIGINT,                    -- ID do usuário
    client_id VARCHAR(100) NOT NULL,      -- Client ID da aplicação
    client_secret VARCHAR(500) NOT NULL,  -- Client Secret da aplicação
    user_id VARCHAR(100),                 -- User ID do Mercado Pago
    api_url VARCHAR(500) NOT NULL,        -- URL base da API
    auth_url VARCHAR(500),                -- URL de autorização OAuth2
    token_url VARCHAR(500),               -- URL para troca de token
    redirect_uri VARCHAR(500),            -- URI de redirecionamento
    scope VARCHAR(200),                   -- Escopo de permissões
    sandbox BOOLEAN DEFAULT FALSE,        -- Modo sandbox/produção
    ativo BOOLEAN DEFAULT TRUE,           -- Status ativo/inativo
    timeout_ms INTEGER DEFAULT 30000,     -- Timeout em milissegundos
    max_retries INTEGER DEFAULT 3,        -- Máximo de tentativas
    retry_delay_ms INTEGER DEFAULT 1000,  -- Delay entre tentativas
    data_criacao TIMESTAMP,               -- Data de criação
    data_atualizacao TIMESTAMP            -- Data de atualização
);
```

## 🔄 **Mudanças no Sistema**

### **Antes (Arquivo .properties)**
```properties
bank.api.mercadopago.client-id=4223603750190943
bank.api.mercadopago.client-secret=APP_USR-4223603750190943-XXXXXX
bank.api.mercadopago.user-id=209112973
```

### **Agora (Interface Web + Banco)**
- ✅ Credenciais configuradas pela interface web
- ✅ Armazenadas no banco de dados
- ✅ Configurações por usuário
- ✅ Ativação/desativação dinâmica
- ✅ Teste de conexão integrado

## 🚀 **Benefícios**

1. **Segurança**: Credenciais não ficam expostas em arquivos
2. **Flexibilidade**: Cada usuário pode ter suas próprias credenciais
3. **Manutenibilidade**: Interface intuitiva para configuração
4. **Teste**: Botão de teste integrado
5. **Auditoria**: Histórico de mudanças no banco

## ⚠️ **Importante**

- **NUNCA** compartilhe seu Client Secret
- Mantenha as credenciais seguras
- Use HTTPS em produção
- Teste sempre antes de ativar
- Monitore os logs para detectar problemas

## 🆘 **Suporte**

Se encontrar problemas:
1. Verifique os logs do backend
2. Teste a conexão pela interface
3. Verifique se as credenciais estão corretas
4. Confirme se a aplicação está ativa no Mercado Pago

---

**🎉 Agora você pode configurar o Mercado Pago de forma segura e intuitiva pela interface web!**
