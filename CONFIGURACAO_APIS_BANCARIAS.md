# 🔐 Configuração de APIs Bancárias por Usuário - ConsumoEsperto

## 📋 Visão Geral

O ConsumoEsperto agora suporta **configurações de APIs bancárias por usuário**, permitindo que cada usuário tenha suas próprias credenciais e configurações para cada banco, mantendo total isolamento e segurança.

## ✨ Funcionalidades Principais

### 🔑 **Configurações por Usuário**
- Cada usuário pode configurar suas próprias credenciais bancárias
- Isolamento completo entre usuários
- Suporte a múltiplos bancos por usuário
- Configurações ativas/inativas por usuário

### 🏦 **Bancos Suportados**
- **Mercado Pago** - Cartões de crédito, saldo e transações
- **Itaú** - Open Banking completo
- **Inter** - APIs Open Banking
- **Nubank** - Cartões e conta digital

### 🛠️ **Recursos de Gestão**
- Interface web intuitiva para configuração
- Teste de conexão em tempo real
- Ativação/desativação de configurações
- Criação de configurações padrão
- Dashboard com estatísticas por usuário

## 🚀 Como Usar

### 1. **Acessar a Tela de Configuração**

#### **Opção A: Pelo Menu Principal**
1. Acesse a página inicial do ConsumoEsperto
2. Clique no botão **"Configurar APIs"** no menu superior
3. Ou clique em qualquer botão **"Configurar"** na seção de bancos

#### **Opção B: URL Direta**
```
http://localhost:8080/bank-config.html
```

### 2. **Criar Configurações Padrão (Recomendado)**

1. Na tela de configuração, clique em **"Criar Configurações Padrão"**
2. O sistema criará automaticamente configurações para todos os bancos
3. As credenciais padrão serão preenchidas (você pode editá-las depois)

### 3. **Configurar Credenciais Reais**

#### **Mercado Pago**
```
Client ID: 4223603750190943
Client Secret: APP_USR-4223603750190943-XXXXXX
User ID: 209112973
```

#### **Outros Bancos**
- Obtenha credenciais no portal de desenvolvedores de cada banco
- Configure as URLs de API conforme documentação oficial
- Defina escopos de acesso necessários

### 4. **Gerenciar Configurações**

- **Editar**: Clique no menu de cada configuração → "Editar"
- **Testar**: Clique em "Testar Conexão" para verificar se está funcionando
- **Ativar/Desativar**: Use o toggle para ativar ou desativar configurações
- **Remover**: Delete configurações que não são mais necessárias

## 🔧 Configuração Técnica

### **Estrutura do Banco de Dados**

A tabela `bank_api_configs` foi atualizada para incluir:

```sql
-- Campo principal para usuário
usuario_id NUMBER(19) NOT NULL

-- Constraint de unicidade composta
UNIQUE (usuario_id, bank_code)

-- Índices para performance
CREATE INDEX idx_bank_config_usuario_id ON bank_api_configs(usuario_id);
CREATE INDEX idx_bank_config_usuario_bank_code ON bank_api_configs(usuario_id, bank_code);
```

### **Scripts de Atualização**

Execute o script SQL para atualizar seu banco:

```bash
# Localizar o script
backend/src/main/resources/db/update_bank_configs_table.sql

# Executar no Oracle
sqlplus usuario/senha@servidor @update_bank_configs_table.sql
```

### **Endpoints da API**

#### **Configurações do Usuário Autenticado**
```http
GET    /api/bank-config/my-configs           # Listar minhas configurações
GET    /api/bank-config/my-configs/active    # Configurações ativas
POST   /api/bank-config/my-configs           # Criar nova configuração
PUT    /api/bank-config/my-configs/{id}      # Atualizar configuração
DELETE /api/bank-config/my-configs/{id}      # Remover configuração
PATCH  /api/bank-config/my-configs/{id}/toggle # Ativar/desativar
POST   /api/bank-config/my-configs/{id}/test # Testar conexão
```

#### **Configurações Padrão**
```http
POST   /api/bank-config/my-configs/create-defaults # Criar configurações padrão
```

#### **Estatísticas**
```http
GET    /api/bank-config/stats/my-stats       # Estatísticas do usuário
GET    /api/bank-config/stats/my-banks       # Bancos configurados
```

## 🔐 Autenticação e Segurança

### **Sistema de Autenticação**
- **JWT Tokens**: Autenticação baseada em tokens
- **Google OAuth2**: Login via Google (opcional)
- **Isolamento**: Cada usuário vê apenas suas configurações

### **Segurança das Credenciais**
- **Criptografia**: Senhas e tokens são criptografados
- **Isolamento**: Credenciais são separadas por usuário
- **Auditoria**: Logs de todas as operações

### **Como Obter Token de Autenticação**

#### **Login Tradicional**
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "seu_email@exemplo.com",
  "password": "sua_senha"
}
```

#### **Login Google OAuth2**
```http
POST /api/auth/google
Content-Type: application/json

{
  "idToken": "token_do_google"
}
```

## 📱 Interface do Usuário

### **Dashboard Principal**
- **Estatísticas**: Total, ativas, falharam, pendentes
- **Configurações**: Lista de todas as configurações do usuário
- **Ações**: Criar, editar, testar, remover

### **Modal de Configuração**
- **Informações Básicas**: Nome do banco, código
- **Credenciais**: Client ID, Client Secret, User ID
- **URLs**: API, autorização, token, redirecionamento
- **Configurações**: Timeout, tentativas, ambiente

### **Responsividade**
- **Mobile**: Interface otimizada para dispositivos móveis
- **Desktop**: Layout completo com todas as funcionalidades
- **Bootstrap 5**: Design moderno e responsivo

## 🚨 Solução de Problemas

### **Erro: "Usuário não autenticado"**
```bash
# Verificar se o token está sendo enviado
# Adicionar no header das requisições:
Authorization: Bearer SEU_TOKEN_JWT
```

### **Erro: "Configuração não encontrada"**
```bash
# Verificar se a configuração existe para o usuário
# Usar endpoints /my-configs/ para buscar configurações do usuário
```

### **Erro: "Constraint violation"**
```bash
# Verificar se já existe configuração para o mesmo banco
# Cada usuário pode ter apenas uma configuração por banco
```

### **Problemas de Conexão**
```bash
# Verificar URLs das APIs
# Testar credenciais no portal do banco
# Verificar se o banco está em modo sandbox ou produção
```

## 🔄 Migração de Dados

### **Se Você Já Tem Configurações**

1. **Criar usuário padrão**:
```sql
INSERT INTO usuarios (username, email, nome, password, data_criacao) 
VALUES ('admin', 'admin@consumoesperto.com', 'Administrador', 'admin_password_hash', SYSDATE);
```

2. **Associar configurações existentes**:
```sql
UPDATE bank_api_configs 
SET usuario_id = (SELECT id FROM usuarios WHERE username = 'admin') 
WHERE usuario_id IS NULL;
```

3. **Verificar integridade**:
```sql
SELECT COUNT(*) FROM bank_api_configs WHERE usuario_id IS NULL;
```

## 📚 Recursos Adicionais

### **Documentação das APIs Bancárias**
- [Mercado Pago Developers](https://developers.mercadopago.com/)
- [Itaú Open Banking](https://openbanking.itau.com.br/)
- [Inter Open Banking](https://cdp.openbanking.bancointer.com.br/)
- [Nubank API](https://developers.nubank.com.br/)

### **Configuração do Google OAuth2**
- [Google Cloud Console](https://console.cloud.google.com/)
- [Google Identity Services](https://developers.google.com/identity)

### **Suporte e Contato**
- **Issues**: Abra uma issue no repositório
- **Documentação**: Consulte este README
- **Comunidade**: Participe das discussões

## 🎯 Próximos Passos

### **Funcionalidades Planejadas**
- [ ] **Sincronização automática** de dados bancários
- [ ] **Notificações** de mudanças de saldo
- [ ] **Relatórios personalizados** por usuário
- [ ] **Integração com mais bancos** brasileiros
- [ ] **API pública** para desenvolvedores

### **Melhorias de Segurança**
- [ ] **Criptografia adicional** para credenciais sensíveis
- [ ] **Auditoria completa** de todas as operações
- [ ] **Rate limiting** para prevenir abuso
- [ ] **Validação de IP** para acesso às APIs

---

## 📝 Notas de Versão

### **v2.0.0** - Configurações por Usuário
- ✅ Suporte a múltiplos usuários
- ✅ Interface web completa
- ✅ APIs RESTful por usuário
- ✅ Sistema de autenticação JWT
- ✅ Integração Google OAuth2
- ✅ Dashboard com estatísticas
- ✅ Teste de conexão em tempo real

### **v1.0.0** - Versão Inicial
- ✅ Integração básica com bancos
- ✅ Configurações estáticas
- ✅ APIs bancárias funcionais

---

**ConsumoEsperto** - Revolucionando a gestão financeira pessoal! 🚀
