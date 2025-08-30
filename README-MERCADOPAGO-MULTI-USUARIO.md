# 🔄 Sistema Multi-Usuário do Mercado Pago

## 📋 **Visão Geral**

O sistema agora funciona de forma **multi-usuário** e **automática**. Cada usuário logado terá sua própria configuração do Mercado Pago criada automaticamente quando necessário.

## 🚀 **Como Funciona**

### **1. Configuração Automática**
- ✅ **Não precisa de scripts específicos por usuário**
- ✅ **Configuração é criada automaticamente na primeira vez que o usuário acessa**
- ✅ **Funciona para qualquer usuário logado**
- ✅ **Cada usuário tem sua própria configuração isolada**

### **2. Fluxo Automático**
```
Usuário faz login → Sistema verifica configuração → Se não existe, cria automaticamente → Usuário pode configurar credenciais
```

### **3. Configuração Padrão Criada**
Quando um usuário acessa pela primeira vez, o sistema cria automaticamente:

```sql
banco: 'MERCADOPAGO'
client_id: '4223603750190943' (padrão)
client_secret: 'CONFIGURAR_CLIENT_SECRET' (placeholder)
user_id: '209112973' (padrão)
ativo: true
```

## 🛠️ **Configuração Necessária**

### **Passo 1: Execute o Script de Estrutura**
```sql
-- Execute este script uma vez para criar a tabela:
-- backend/src/main/resources/sql/ensure-bank-api-configs-table.sql
```

### **Passo 2: Configure o Client Secret**
Cada usuário deve configurar seu próprio Client Secret:

1. **Acesse:** https://www.mercadopago.com.br/developers
2. **Vá em:** "Suas aplicações"
3. **Copie o Client Secret real**
4. **Use a tela de configuração do frontend** para salvar

### **Passo 3: Teste**
- Faça login com qualquer usuário
- O sistema criará configuração automaticamente
- Configure o Client Secret via frontend
- Teste as APIs

## 🔧 **Arquivos Modificados**

### **Backend:**
- `MercadoPagoService.java` - Criação automática de configuração
- `BankApiConfig.java` - Modelo com campo `userId`

### **SQL:**
- `ensure-bank-api-configs-table.sql` - Script de estrutura genérico

## 📊 **Estrutura da Tabela**

```sql
bank_api_configs
├── id (BIGSERIAL)
├── banco (VARCHAR) - 'MERCADOPAGO'
├── usuario_id (BIGINT) - ID do usuário logado
├── client_id (VARCHAR) - Client ID do Mercado Pago
├── client_secret (VARCHAR) - Client Secret (configurável)
├── user_id (VARCHAR) - User ID do Mercado Pago
├── api_url (VARCHAR) - URL da API
├── ativo (BOOLEAN) - Se a configuração está ativa
└── outros campos de configuração...
```

## 🎯 **Benefícios**

1. **Multi-usuário:** Cada usuário tem configuração isolada
2. **Automático:** Não precisa de intervenção manual
3. **Seguro:** Credenciais separadas por usuário
4. **Flexível:** Fácil de configurar via frontend
5. **Escalável:** Funciona para qualquer número de usuários

## ⚠️ **Importante**

- **Client Secret padrão:** `CONFIGURAR_CLIENT_SECRET` (placeholder)
- **Usuário deve configurar:** Via tela de configuração do frontend
- **Não hardcode:** Credenciais vêm do banco de dados
- **Isolamento:** Cada usuário só vê suas próprias configurações

## 🚀 **Próximos Passos**

1. Execute o script SQL de estrutura
2. Teste com usuário logado
3. Configure Client Secret via frontend
4. Teste as APIs do Mercado Pago

---

**🎉 Sistema agora é totalmente multi-usuário e automático!**
