# 🚀 Configuração OBRIGATÓRIA do Google OAuth

## ⚠️ **ATENÇÃO: A aplicação NÃO funcionará sem esta configuração!**

### **1. Acesse o Google Cloud Console**
- Vá para: https://console.cloud.google.com/
- Faça login com sua conta Google

### **2. Crie um Projeto**
- Clique em "Selecionar projeto" (topo da página)
- Clique em "Novo projeto"
- Nome: `ConsumoEsperto`
- Clique em "Criar"

### **3. Habilite a API**
- No menu lateral, clique em "APIs e Serviços" > "Biblioteca"
- Procure por: `Google Identity Services`
- Clique na API e depois em "Habilitar"

### **4. Configure OAuth**
- No menu lateral, clique em "APIs e Serviços" > "Credenciais"
- Clique em "Criar credenciais" > "ID do cliente OAuth"
- Tipo: `Aplicativo da Web`
- Nome: `ConsumoEsperto Web`
- URIs de redirecionamento:
  ```
  http://localhost:4200
  http://localhost:4200/login
  http://localhost:4200/dashboard
  ```
- Clique em "Criar"

### **5. Copie o Client ID**
- Anote o "ID do cliente" (formato: `123456789-abcdefghijklmnop.apps.googleusercontent.com`)

### **6. Atualize o arquivo OBRIGATORIAMENTE**
- Abra: `src/environments/environment.ts`
- Substitua a linha:
  ```typescript
  googleClientId: '' // ⚠️ CONFIGURE SEU CLIENT ID REAL AQUI
  ```
- Pelo seu Client ID real:
  ```typescript
  googleClientId: 'SEU_CLIENT_ID_REAL_AQUI.apps.googleusercontent.com'
  ```

### **7. Teste**
- Execute: `ng serve`
- Vá para login
- Clique em "Continuar com Google"
- Faça login com sua conta

## 🔧 **SOLUÇÃO DE PROBLEMAS**

### **Erro "Google OAuth não configurado"**
- ✅ Configure o `googleClientId` no `environment.ts`
- ✅ Verifique se o Client ID está correto
- ✅ Reinicie a aplicação após configurar

### **Erro "invalid_client"**
- ✅ Verifique se o Client ID está correto
- ✅ Verifique se as URIs de redirecionamento estão configuradas
- ✅ Aguarde 5 minutos após criar as credenciais

### **Erro "popup_closed_by_user"**
- ✅ Permita popups para localhost:4200
- ✅ Verifique se não há bloqueadores de anúncios

### **Erro "redirect_uri_mismatch"**
- ✅ Verifique se as URIs no Google Cloud Console são exatamente iguais às da sua aplicação

## 📱 **PARA PRODUÇÃO**

Quando for fazer deploy:
1. Adicione seu domínio real nas URIs de redirecionamento
2. Atualize `environment.prod.ts`
3. Publique a tela de consentimento OAuth

## ⚠️ **IMPORTANTE**

- **NUNCA** compartilhe seu Client ID
- Use variáveis de ambiente em produção
- Monitore o uso no Google Cloud Console
- **A aplicação NÃO funcionará sem o Client ID configurado**

## 🆘 **AJUDA**

Se ainda tiver problemas:
1. Verifique o console do navegador (F12)
2. Verifique se não há erros de CORS
3. Aguarde alguns minutos após configurar
4. Verifique se a API está habilitada
5. **Verifique se o Client ID está configurado corretamente**

---

**🎯 IMPORTANTE**: A aplicação agora usa APENAS autenticação real com Google. Não há mais modo simulado!
