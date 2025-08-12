# Migração do H2 para Oracle - ConsumoEsperto

## Resumo das Alterações Realizadas

### 1. Dependências Maven (pom.xml)
- ✅ Removido: `com.h2database:h2`
- ✅ Adicionado: `com.oracle.database.jdbc:ojdbc8`

### 2. Arquivos de Configuração
- ✅ `application.properties` - Configuração base
- ✅ `application-dev.properties` - Desenvolvimento com Oracle
- ✅ `application-prod.properties` - Produção com Oracle
- ✅ `application-docker.properties` - Docker com Oracle
- ✅ `application-test.properties` - Testes com Oracle

### 3. Scripts SQL
- ✅ `sql/oracle-setup.sql` - Configuração inicial do usuário
- ✅ `sql/oracle-sequences.sql` - Criação das sequências

### 4. Scripts de Inicialização
- ✅ `start-oracle-dev.bat` - Windows Batch
- ✅ `start-oracle-dev.ps1` - PowerShell
- ✅ `docker-compose.yml` - Docker Oracle

### 5. Documentação
- ✅ `ORACLE_SETUP.md` - Configuração completa
- ✅ `MIGRATION_H2_TO_ORACLE.md` - Este arquivo

## Passos para Migração

### Passo 1: Instalar Oracle Database

#### Opção A: Oracle Express Edition (XE) - Gratuito
```bash
# Download: https://www.oracle.com/database/technologies/xe-downloads.html
# Instalar seguindo as instruções do instalador
# Porta padrão: 1521
# SID padrão: XE
```

#### Opção B: Docker (Recomendado para desenvolvimento)
```bash
# Na pasta backend
docker-compose up -d oracle

# Aguardar inicialização (pode demorar alguns minutos)
docker-compose logs -f oracle
```

### Passo 2: Configurar Banco Oracle

#### 2.1. Conectar como SYSTEM
```sql
sqlplus system/oracle123@localhost:1521:XE
```

#### 2.2. Executar script de configuração
```sql
@sql/oracle-setup.sql
```

#### 2.3. Conectar como usuário da aplicação
```sql
sqlplus consumo_esperto/consumo_esperto123@localhost:1521:XE
```

#### 2.4. Criar sequências (opcional)
```sql
@sql/oracle-sequences.sql
```

### Passo 3: Testar Conexão

#### 3.1. Verificar se Oracle está rodando
```bash
# Windows
netstat -ano | findstr :1521

# Linux/Mac
netstat -an | grep :1521
```

#### 3.2. Testar aplicação
```bash
# Usar script de inicialização
./start-oracle-dev.bat
# ou
./start-oracle-dev.ps1

# Ou manualmente
mvn spring-boot:run -Dspring.profiles.active=dev
```

### Passo 4: Verificar Migração

#### 4.1. Logs da aplicação
- Verificar se não há erros de conexão
- Verificar se as tabelas foram criadas
- Verificar se o endpoint de teste responde

#### 4.2. Testar endpoints
```bash
# Teste de saúde
curl http://localhost:8080/api/test/health

# Teste de registro
curl -X POST http://localhost:8080/api/auth/registro \
  -H "Content-Type: application/json" \
  -d '{"username":"teste","password":"123456","email":"teste@teste.com","nome":"Usuario Teste"}'
```

## Configurações por Perfil

### Desenvolvimento (`dev`)
- **DDL**: `create-drop` (cria tabelas automaticamente)
- **Logs**: Detalhados
- **Pool**: Configuração básica

### Produção (`prod`)
- **DDL**: `validate` (valida tabelas existentes)
- **Logs**: Reduzidos
- **Pool**: Configuração otimizada

### Docker (`docker`)
- **DDL**: `create-drop`
- **Logs**: Detalhados
- **Pool**: Configuração para container

### Testes (`test`)
- **DDL**: `create-drop`
- **Logs**: Detalhados
- **Pool**: Configuração mínima

## Solução de Problemas Comuns

### Erro: ORA-12541: TNS:no listener
```bash
# Verificar se Oracle está rodando
docker-compose ps oracle

# Reiniciar se necessário
docker-compose restart oracle
```

### Erro: ORA-01017: invalid username/password
```sql
-- Verificar usuário
SELECT username, account_status FROM dba_users WHERE username = 'CONSUMO_ESPERTO';

-- Recriar se necessário
DROP USER consumo_esperto CASCADE;
-- Executar oracle-setup.sql novamente
```

### Erro: ORA-00942: table or view does not exist
```properties
# Verificar configuração
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.OracleDialect
```

### Erro: ORA-12514: TNS:listener does not currently know of service
```sql
-- Verificar SID
SELECT name, value FROM v$parameter WHERE name = 'service_names';
```

## Verificação da Migração

### 1. Status dos Serviços
- ✅ Backend rodando na porta 8080
- ✅ Oracle rodando na porta 1521
- ✅ Conexão JDBC funcionando

### 2. Funcionalidades
- ✅ Endpoints respondendo
- ✅ Banco de dados acessível
- ✅ Tabelas criadas automaticamente
- ✅ Autenticação funcionando

### 3. Logs
- ✅ Sem erros de conexão
- ✅ Hibernate criando tabelas
- ✅ Queries SQL executando

## Rollback para H2 (se necessário)

### 1. Reverter dependências
```xml
<!-- Remover Oracle -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Adicionar H2 -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 2. Reverter configurações
```properties
# application.properties
spring.profiles.active=h2

# application-h2.properties
spring.datasource.url=jdbc:h2:mem:consumo_esperto
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
```

## Próximos Passos

1. **Testar aplicação** com Oracle
2. **Verificar performance** das consultas
3. **Configurar backup** do banco Oracle
4. **Implementar monitoramento** do banco
5. **Documentar procedimentos** de manutenção

## Suporte

- **Documentação Oracle**: https://docs.oracle.com/
- **Spring Boot + Oracle**: https://spring.io/guides/gs/accessing-data-jpa/
- **Hibernate + Oracle**: https://hibernate.org/orm/documentation/
- **Docker Oracle**: https://github.com/oracle/docker-images
