# TODO - ConsumoEsperto

## ✅ **Tarefas Concluídas:**

### **1. debug_auth_issue** - COMPLETED
- Analisar problema de autenticação no endpoint /api/bank/connected
- **Status**: ✅ Concluído

### **2. fix_jwt_filter** - COMPLETED  
- Corrigir filtro JWT que está falhando na autenticação
- **Status**: ✅ Concluído

### **3. check_token_validation** - COMPLETED
- Verificar se o token JWT está sendo validado corretamente
- **Status**: ✅ Concluído

### **4. test_auth_flow** - COMPLETED
- Testar fluxo completo de autenticação após correções
- **Status**: ✅ Concluído

### **5. fix_frontend_types** - COMPLETED
- Corrigir tipo LoginResponse para GoogleLoginResponse no frontend
- **Status**: ✅ Concluído

### **6. fix_backend_dto** - COMPLETED
- Criar DTO GoogleTokenRequest para receber dados do frontend
- **Status**: ✅ Concluído

### **7. fix_database_transaction** - COMPLETED
- Corrigir conflito de transações Hibernate vs HikariCP autoCommit
- **Status**: ✅ Concluído

### **8. fix_jwt_weak_key** - COMPLETED
- Corrigir WeakKeyException com geração automática de chave forte
- **Status**: ✅ Concluído

### **9. fix_jwt_username_conflict** - COMPLETED
- Corrigir conflito username vs ID no JWT (NumberFormatException)
- **Status**: ✅ Concluído

### **10. fix_user_profile_photo** - COMPLETED
- Corrigir foto do Google não carregando (campo fotoUrl ausente no UsuarioDTO)
- **Status**: ✅ Concluído

### **11. create_bank_integration_backend** - COMPLETED
- Criar backend para integração com Mercado Pago (DTOs, Service, Controller)
- **Status**: ✅ Concluído

### **12. create_bank_config_frontend** - COMPLETED
- Criar componente frontend para configuração de credenciais bancárias
- **Status**: ✅ Concluído

## 🔄 **Tarefas em Progresso:**

### **13. fix_backend_compilation** - IN_PROGRESS
- Corrigir erros de compilação no backend (conflitos de DTOs)
- **Status**: 🔄 Em progresso
- **Próximo**: Resolver conflitos entre DTOs antigos e novos

## 📋 **Próximas Tarefas:**

### **14. integrate_bank_data** - PENDING
- Integrar dados reais de cartões e faturas do Mercado Pago
- **Status**: ⏳ Pendente
- **Dependências**: fix_backend_compilation

### **15. setup_ngrok** - PENDING
- Configurar NGROK para desenvolvimento local com APIs externas
- **Status**: ⏳ Pendente
- **Dependências**: integrate_bank_data

### **16. test_bank_integration** - PENDING
- Testar integração completa com Mercado Pago
- **Status**: ⏳ Pendente
- **Dependências**: setup_ngrok

---

## 🎯 **Status Geral:**
- **✅ Concluídas**: 12 tarefas
- **🔄 Em Progresso**: 1 tarefa  
- **⏳ Pendentes**: 3 tarefas
- **📊 Progresso**: 75% completo

## 🚀 **Próximo Passo:**
Resolver conflitos de compilação no backend para permitir integração bancária completa.
