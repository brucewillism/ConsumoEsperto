# 🚨 RESOLUÇÃO DE PROBLEMAS - CONSUMO ESPERTO

Este documento contém soluções para problemas comuns que podem ocorrer durante o desenvolvimento e execução do projeto.

## 🔧 PROBLEMAS DO BACKEND

### Erro: `UnsatisfiedDependencyException: No property 'ativo' found for type 'AutorizacaoBancaria'`

**Causa**: A entidade `AutorizacaoBancaria` está faltando o campo `ativo`.

**Solução**:
1. Execute o script de resolução: `resolver-backend.bat`
2. Ou manualmente:
   - Abra `backend/src/main/java/com/consumoesperto/model/AutorizacaoBancaria.java`
   - Adicione o campo: `private Boolean ativo = true;`
   - Recompile o projeto: `mvn clean compile`

### Erro: `Cannot find module '@angular/material/...'`

**Causa**: Dependências do Angular Material não instaladas ou corrompidas.

**Solução**:
1. Execute o script de reinstalação: `reinstalar-frontend.bat`
2. Ou manualmente:
   ```bash
   cd frontend
   rm -rf node_modules package-lock.json
   npm install
   ```

### Erro: `JDK isn't specified for module 'consumo-esperto-backend'`

**Causa**: IntelliJ IDEA não está configurado para usar JDK 17.

**Solução**:
1. Execute o setup completo: `setup-completo-projeto.bat`
2. Ou manualmente:
   - Abra o projeto no IntelliJ IDEA
   - Vá em File > Project Structure > Project
   - Configure Project SDK para JDK 17

### Erro: `Cannot resolve placeholder 'itau.client.id'`

**Causa**: Propriedades de configuração não encontradas.

**Solução**:
1. Verifique se os arquivos de configuração existem:
   - `backend/src/main/resources/application-h2.properties`
   - `backend/src/main/resources/bank-apis-config.properties`
   - `backend/src/main/resources/mercadopago-config.properties`
2. Execute: `setup-completo-projeto.bat`

## 🎨 PROBLEMAS DO FRONTEND

### Erro: `TS-991010: 'imports' must be an array...`

**Causa**: Módulos Angular Material não carregados corretamente.

**Solução**:
1. Execute: `reinstalar-frontend.bat`
2. Verifique se o `package.json` tem as dependências corretas
3. Limpe o cache: `npm cache clean --force`

### Erro: `Component HMR has been enabled` mas falha na compilação

**Causa**: Conflito de versões ou dependências corrompidas.

**Solução**:
1. Execute: `reinstalar-frontend.bat`
2. Verifique a versão do Node.js (deve ser 18+)
3. Verifique se não há proxy corporativo bloqueando

## 🗄️ PROBLEMAS DO BANCO DE DADOS

### Erro: `Table 'autorizacoes_bancarias' doesn't exist`

**Causa**: Tabelas não foram criadas no banco.

**Solução**:
1. Execute: `setup-database.bat`
2. Ou manualmente:
   - Execute `backend/src/main/resources/db/create-tables-only.sql`
   - Execute `backend/src/main/resources/db/update_autorizacoes_table.sql`

### Erro: `Column 'ativo' not found`

**Causa**: Estrutura da tabela desatualizada.

**Solução**:
1. Execute: `backend/src/main/resources/db/update_autorizacoes_table.sql`
2. Ou execute: `setup-database.bat`

## 🚀 SCRIPTS DE RESOLUÇÃO AUTOMÁTICA

### `resolver-backend.bat`
- Verifica Java e Maven
- Recompila o projeto
- Verifica entidades
- Verifica scripts de banco

### `reinstalar-frontend.bat`
- Remove dependências antigas
- Limpa cache
- Reinstala Angular Material
- Verifica instalação

### `setup-completo-projeto.bat`
- Configuração completa do ambiente
- Instala todas as dependências
- Configura IntelliJ IDEA
- Cria banco de dados

## 📋 CHECKLIST DE VERIFICAÇÃO

Antes de reportar um problema, verifique:

- [ ] Java 17 está instalado e configurado
- [ ] Maven está funcionando
- [ ] Node.js 18+ está instalado
- [ ] Dependências foram instaladas
- [ ] Banco de dados está acessível
- [ ] IntelliJ IDEA está configurado

## 🔍 LOGS E DEBUGGING

### Backend (Spring Boot)
- Logs aparecem no console
- Verifique erros de compilação: `mvn clean compile`
- Verifique dependências: `mvn dependency:tree`

### Frontend (Angular)
- Logs aparecem no console do navegador (F12)
- Verifique erros de compilação: `ng build`
- Verifique dependências: `npm list`

## 📞 SUPORTE

Se os problemas persistirem:

1. Execute os scripts de resolução automática
2. Verifique se todas as dependências estão corretas
3. Limpe e reinstale o projeto
4. Verifique se não há conflitos de versão

## 🎯 SOLUÇÕES RÁPIDAS

| Problema | Solução Rápida |
|----------|----------------|
| Backend não compila | `resolver-backend.bat` |
| Frontend não roda | `reinstalar-frontend.bat` |
| Banco não funciona | `setup-database.bat` |
| Tudo quebrado | `setup-completo-projeto.bat` |

---

**Desenvolvido com ❤️ pela equipe Consumo Esperto**
