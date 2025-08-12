# Configuração do Oracle Database para ConsumoEsperto

## Pré-requisitos

1. **Oracle Database** instalado e rodando (versão 11g, 12c, 18c, 19c ou 21c)
2. **Oracle JDBC Driver** (já incluído no pom.xml)
3. **Java 17** (já configurado)

## Passos para Configuração

### 1. Instalar Oracle Database

#### Opção A: Oracle Database Express Edition (XE) - Gratuito
- Download: https://www.oracle.com/database/technologies/xe-downloads.html
- Instalar seguindo as instruções do instalador
- Porta padrão: 1521
- SID padrão: XE

#### Opção B: Oracle Database Standard/Enterprise
- Download: https://www.oracle.com/database/technologies/oracle-database-software-downloads.html
- Instalar seguindo as instruções do instalador

### 2. Configurar o Banco

#### 2.1. Conectar como SYSTEM
```sql
sqlplus system/password@localhost:1521:XE
```

#### 2.2. Executar o script de configuração
```sql
@sql/oracle-setup.sql
```

#### 2.3. Verificar se o usuário foi criado
```sql
SELECT username, account_status FROM dba_users WHERE username = 'CONSUMO_ESPERTO';
```

### 3. Configurar o Aplicativo

#### 3.1. Perfil de Desenvolvimento (padrão)
- Usar `application-dev.properties`
- `ddl-auto=create-drop` (cria as tabelas automaticamente)

#### 3.2. Perfil de Produção
- Usar `application-prod.properties`
- `ddl-auto=validate` (valida as tabelas existentes)

### 4. Variáveis de Ambiente (Produção)

```bash
export DB_HOST=localhost
export DB_PORT=1521
export DB_SID=XE
export DB_USERNAME=consumo_esperto
export DB_PASSWORD=consumo_esperto123
```

### 5. Testar a Conexão

#### 5.1. Verificar se o Oracle está rodando
```bash
# Windows
netstat -ano | findstr :1521

# Linux/Mac
netstat -an | grep :1521
```

#### 5.2. Testar conexão JDBC
```bash
# Usar o aplicativo Spring Boot
mvn spring-boot:run -Dspring.profiles.active=dev
```

## Configurações de Conexão

### Desenvolvimento
- **URL**: `jdbc:oracle:thin:@localhost:1521:XE`
- **Username**: `consumo_esperto`
- **Password**: `consumo_esperto123`
- **Dialect**: `Oracle12cDialect`

### Produção
- **URL**: `jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${DB_SID}`
- **Username**: `${DB_USERNAME}`
- **Password**: `${DB_PASSWORD}`
- **Dialect**: `Oracle12cDialect`

## Solução de Problemas

### Erro: ORA-12541: TNS:no listener
- Verificar se o Oracle Listener está rodando
- Verificar se a porta 1521 está aberta

### Erro: ORA-01017: invalid username/password
- Verificar credenciais do usuário
- Verificar se o usuário foi criado corretamente

### Erro: ORA-00942: table or view does not exist
- Verificar se `ddl-auto=create-drop` está configurado
- Verificar se o usuário tem privilégios para criar tabelas

### Erro: ORA-12514: TNS:listener does not currently know of service
- Verificar se o SID está correto
- Verificar se o banco está rodando

## Comandos Úteis

### Verificar status do banco
```sql
SELECT status FROM v$instance;
```

### Verificar serviços disponíveis
```sql
SELECT name, value FROM v$parameter WHERE name = 'service_names';
```

### Verificar conexões ativas
```sql
SELECT username, machine, program FROM v$session WHERE username IS NOT NULL;
```

## Migração do H2 para Oracle

1. **Backup dos dados** (se necessário)
2. **Executar script de configuração** do Oracle
3. **Alterar perfil** para `dev` ou `prod`
4. **Reiniciar aplicação**
5. **Verificar logs** para confirmar conexão

## Notas Importantes

- **Dialect**: Usar `Oracle12cDialect` para compatibilidade com versões mais recentes
- **Encoding**: Configurar UTF-8 para suporte a caracteres especiais
- **Pool de Conexões**: Configurado automaticamente pelo HikariCP
- **DDL**: Em desenvolvimento, as tabelas são criadas automaticamente
- **Backup**: Sempre fazer backup antes de alterar configurações de banco
