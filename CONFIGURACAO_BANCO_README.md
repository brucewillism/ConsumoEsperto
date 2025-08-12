# 🗄️ Configuração do Banco de Dados Oracle - ConsumoEsperto

## 📋 Visão Geral

Este documento explica como configurar o banco de dados Oracle para o projeto ConsumoEsperto, incluindo a criação do banco, usuário e todas as tabelas necessárias.

## 🎯 Objetivos

- ✅ Criar banco de dados Oracle com nome `consumo_esperto`
- ✅ Criar usuário `consumo_esperto` com privilégios adequados
- ✅ Criar todas as tabelas do sistema
- ✅ Inserir dados de teste iniciais
- ✅ Configurar índices para performance

## 🚀 Métodos de Configuração

### **Opção 1: Script Automatizado (Recomendado)**

#### **setup-completo-projeto.bat**
- Script principal que configura todo o projeto
- Inclui opção para criar o banco automaticamente
- Executa todos os passos em sequência

#### **setup-database.bat**
- Script específico para configuração do banco
- Mais detalhado e focado apenas no banco
- Ideal para configurações manuais ou troubleshooting

### **Opção 2: Configuração Manual**

Executar os scripts SQL individualmente via SQL*Plus ou Oracle SQL Developer.

## 🔧 Pré-requisitos

### **Software Necessário**
- ✅ Oracle Database (XE, Standard, Enterprise)
- ✅ Oracle Client ou SQL*Plus
- ✅ Windows (para scripts .bat)

### **Privilégios Necessários**
- ✅ Usuário SYSTEM ou SYS com privilégios de administrador
- ✅ Permissão para criar tablespaces
- ✅ Permissão para criar usuários
- ✅ Permissão para conceder privilégios

### **Configurações de Rede**
- ✅ Oracle Listener rodando
- ✅ Porta 1521 (padrão) acessível
- ✅ Firewall configurado adequadamente

## 📁 Estrutura de Arquivos

```
backend/src/main/resources/db/
├── init-oracle.sql          # Cria tablespace e usuário
├── create-tables.sql        # Cria todas as tabelas
├── insert-test-data.sql     # Insere dados de teste
└── update_bank_configs_table.sql  # Atualiza tabela existente
```

## 🗃️ Tabelas Criadas

### **1. USUARIOS**
- Armazena informações dos usuários do sistema
- Suporte a autenticação local e Google OAuth2

### **2. CATEGORIAS**
- Categorias de gastos personalizadas por usuário
- Cores, ícones e descrições configuráveis

### **3. CARTÕES_CREDITO**
- Cartões de crédito dos usuários
- Limites, datas de fechamento e vencimento

### **4. TRANSACOES**
- Transações financeiras dos usuários
- Categorização automática e manual

### **5. FATURAS**
- Faturas dos cartões de crédito
- Controle de pagamentos e vencimentos

### **6. COMPRAS_PARCELADAS**
- Compras parceladas dos usuários
- Controle de parcelas e valores

### **7. PARCELAS**
- Parcelas individuais das compras
- Status de pagamento e vencimentos

### **8. AUTORIZACOES_BANCARIAS**
- Tokens OAuth2 para APIs bancárias
- Controle de expiração e renovação

### **9. BANK_API_CONFIGS**
- Configurações das APIs bancárias por usuário
- Credenciais, URLs e parâmetros de conexão

## 🔐 Detalhes de Conexão

### **Banco Criado**
```
Nome: consumo_esperto
Tablespace: consumo_esperto_data
```

### **Usuário do Sistema**
```
Username: consumo_esperto
Password: consumo_esperto123
Privilégios: CONNECT, RESOURCE, CREATE TABLE, CREATE SEQUENCE
```

### **String de Conexão**
```
jdbc:oracle:thin:@localhost:1521:XE
```

## 📊 Índices Criados

### **Performance de Consultas**
- Índices em chaves estrangeiras
- Índices em campos de data
- Índices em campos de status
- Índices compostos para consultas complexas

### **Índices Principais**
```sql
-- Usuários
idx_usuarios_username, idx_usuarios_email

-- Categorias
idx_categorias_usuario, idx_categorias_ativo

-- Transações
idx_transacoes_usuario, idx_transacoes_data
idx_transacoes_categoria, idx_transacoes_tipo

-- Faturas
idx_faturas_cartao, idx_faturas_vencimento
idx_faturas_mes_ano, idx_faturas_status

-- APIs Bancárias
idx_bank_config_usuario_bank_code
idx_bank_config_ativo
```

## 🚨 Solução de Problemas

### **Erro: "SQL*Plus não encontrado"**
```bash
# Solução: Instalar Oracle Client ou adicionar ao PATH
# Exemplo de PATH: C:\oracle\product\12.2.0\client_1\bin
```

### **Erro: "Insufficient privileges"**
```bash
# Verificar se está conectado como SYSTEM ou SYS
# Verificar se tem privilégios de administrador
```

### **Erro: "Tablespace não encontrado"**
```bash
# Verificar se o Oracle está rodando
# Verificar se o listener está ativo
# Verificar configurações de rede
```

### **Erro: "Constraint violation"**
```bash
# Verificar se as tabelas já existem
# Executar DROP TABLE se necessário
# Verificar se há dados conflitantes
```

## 🔄 Migração de Dados

### **Se Você Já Tem um Banco**
1. **Backup**: Faça backup dos dados existentes
2. **Executar**: `update_bank_configs_table.sql`
3. **Verificar**: Constraints e índices

### **Se Você Quer Manter Dados Existentes**
1. **Executar**: Apenas `init-oracle.sql`
2. **Criar**: Apenas tabelas que não existem
3. **Migrar**: Dados existentes para nova estrutura

## 📝 Configuração da Aplicação

### **application.properties**
```properties
# Configuração Oracle
spring.datasource.url=jdbc:oracle:thin:@localhost:1521:XE
spring.datasource.username=consumo_esperto
spring.datasource.password=consumo_esperto123
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver

# Configuração JPA
spring.jpa.database-platform=org.hibernate.dialect.Oracle12cDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### **application-h2.properties (Desenvolvimento)**
```properties
# Configuração H2 para desenvolvimento
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
```

## 🧪 Testes e Verificação

### **Verificar Tabelas Criadas**
```sql
SELECT table_name, tablespace_name 
FROM user_tables 
ORDER BY table_name;
```

### **Verificar Constraints**
```sql
SELECT constraint_name, constraint_type, table_name 
FROM user_constraints 
ORDER BY table_name, constraint_name;
```

### **Verificar Índices**
```sql
SELECT index_name, table_name 
FROM user_indexes 
ORDER BY table_name, index_name;
```

### **Verificar Dados de Teste**
```sql
SELECT 'Usuários' as tabela, COUNT(*) as total FROM usuarios
UNION ALL
SELECT 'Categorias', COUNT(*) FROM categorias
UNION ALL
SELECT 'Cartões', COUNT(*) FROM cartoes_credito;
```

## 📚 Recursos Adicionais

### **Documentação Oracle**
- [Oracle Database Documentation](https://docs.oracle.com/en/database/)
- [Oracle SQL*Plus Reference](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqpug/)
- [Oracle JDBC Developer's Guide](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/)

### **Ferramentas Recomendadas**
- **Oracle SQL Developer**: Interface gráfica gratuita
- **Oracle Data Modeler**: Modelagem de dados
- **Oracle Enterprise Manager**: Monitoramento e administração

### **Comandos Úteis**
```sql
-- Verificar versão do Oracle
SELECT * FROM v$version;

-- Verificar tablespaces
SELECT tablespace_name, status FROM dba_tablespaces;

-- Verificar usuários
SELECT username, account_status FROM dba_users;

-- Verificar privilégios
SELECT * FROM dba_sys_privs WHERE grantee = 'CONSUMO_ESPERTO';
```

## 🎯 Próximos Passos

### **Após Configuração do Banco**
1. ✅ **Testar Conexão**: Verificar se a aplicação conecta
2. ✅ **Executar Aplicação**: Rodar o Spring Boot
3. ✅ **Testar APIs**: Verificar endpoints funcionando
4. ✅ **Configurar Credenciais**: APIs bancárias por usuário
5. ✅ **Testar Funcionalidades**: CRUD completo

### **Monitoramento**
- **Logs da Aplicação**: Verificar erros de conexão
- **Performance**: Monitorar consultas lentas
- **Backup**: Configurar backup automático
- **Segurança**: Revisar privilégios regularmente

---

## 📞 Suporte

### **Problemas Comuns**
- Consulte a seção "Solução de Problemas"
- Verifique logs da aplicação
- Teste conexão via SQL*Plus

### **Contato**
- **Issues**: Abra uma issue no repositório
- **Documentação**: Consulte este README
- **Comunidade**: Participe das discussões

---

**ConsumoEsperto** - Banco de dados configurado e pronto para uso! 🚀
