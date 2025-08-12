# 🔐 Configuração do Google OAuth para ConsumoEsperto

## 📋 Pré-requisitos
- Conta Google (Gmail)
- Acesso ao Google Cloud Console

## 🚀 Passos para Configuração

### 1. Acessar Google Cloud Console
- Vá para [Google Cloud Console](https://console.cloud.google.com/)
- Faça login com sua conta Google

### 2. Criar Novo Projeto
- Clique no seletor de projeto no topo
- Clique em "Novo Projeto"
- Digite um nome (ex: "ConsumoEsperto")
- Clique em "Criar"

### 3. Habilitar Google Identity Services
- No menu lateral, vá em "APIs e Serviços" > "Biblioteca"
- Procure por "Google Identity Services" ou "Google+ API"
- Clique na API e depois em "Habilitar"

### 4. Configurar Tela de Consentimento
- Vá em "APIs e Serviços" > "Tela de consentimento OAuth"
- Selecione "Externo" e clique em "Criar"
- Preencha as informações básicas:
  - Nome do app: "ConsumoEsperto"
  - Email de suporte: seu email
  - Email de contato do desenvolvedor: seu email
- Clique em "Salvar e continuar"
- Na seção "Escopos", adicione:
  - `openid`
  - `profile`
  - `email`
- Clique em "Salvar e continuar"
- Na seção "Usuários de teste", adicione seu email
- Clique em "Salvar e continuar"

### 5. Criar Credenciais OAuth
- Vá em "APIs e Serviços" > "Credenciais"
- Clique em "Criar credenciais" > "ID do cliente OAuth"
- Selecione "Aplicativo da Web"
- Nome: "ConsumoEsperto Web Client"
- URIs de redirecionamento autorizados:
  - `http://localhost:4200`
  - `http://localhost:4200/login`
  - `http://localhost:4200/dashboard`
- Clique em "Criar"

### 6. Copiar Client ID
- Anote o "ID do cliente" gerado
- Formato: `123456789-abcdefghijklmnop.apps.googleusercontent.com`

### 7. Atualizar Configuração
- Abra o arquivo `src/environments/environment.ts`
- Substitua o valor de `googleClientId` pelo seu Client ID real:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  googleClientId: 'SEU_CLIENT_ID_AQUI.apps.googleusercontent.com'
};
```

### 8. Testar
- Execute `ng serve` para iniciar o projeto
- Vá para a página de login
- Clique em "Continuar com Google"
- Faça login com sua conta Google
- Verifique se seus dados reais aparecem

## 🔧 Solução de Problemas

### Erro: "popup_closed_by_user"
- Verifique se o popup não está sendo bloqueado pelo navegador
- Adicione `http://localhost:4200` aos sites permitidos

### Erro: "access_denied"
- Verifique se o email está na lista de usuários de teste
- Aguarde alguns minutos após adicionar usuários de teste

### Erro: "invalid_client"
- Verifique se o Client ID está correto
- Verifique se as URIs de redirecionamento estão configuradas

### Erro: "redirect_uri_mismatch"
- Verifique se as URIs de redirecionamento no Google Cloud Console correspondem exatamente às URLs da sua aplicação

## 📱 Para Produção

Quando for fazer deploy:
1. Atualize `src/environments/environment.prod.ts`
2. Adicione seu domínio real nas URIs de redirecionamento
3. Publique a tela de consentimento OAuth
4. Remova usuários de teste e adicione usuários finais

## 🔒 Segurança

- Nunca compartilhe seu Client ID
- Use variáveis de ambiente em produção
- Monitore o uso da API no Google Cloud Console
- Configure alertas de uso excessivo

## 📚 Recursos Adicionais

- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [Google Identity Services](https://developers.google.com/identity/gsi/web)
- [Google Cloud Console](https://console.cloud.google.com/)

## ⚠️ Importante

**ATENÇÃO**: A aplicação está configurada para usar autenticação real com Google. Se você não configurar o Client ID correto, o login com Google não funcionará e a aplicação usará o modo fallback (dados simulados).

Para usar com dados reais do Google:
1. Siga todos os passos acima
2. Substitua o `googleClientId` no `environment.ts`
3. Reinicie a aplicação com `ng serve`
4. Teste o login com Google

Se preferir manter o modo simulado por enquanto, deixe o `googleClientId` como está e a aplicação funcionará normalmente com dados mock.
