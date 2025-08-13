# 🚀 Consumo Esperto - Setup Local

## 📋 Visão Geral

Este projeto usa **ferramentas locais** para garantir compatibilidade e isolamento. Todas as ferramentas são baixadas e configuradas localmente, sem afetar o sistema global.

## 🛠️ Ferramentas Locais

### ✅ Java JDK 17.0.2
- **Localização**: `tools/java/jdk-17.0.2/`
- **Download**: Automático via `setup-completo-projeto.bat`
- **Uso**: Configurado automaticamente no PATH durante execução

### ✅ Maven 3.9.6
- **Localização**: `tools/maven/apache-maven-3.9.6/`
- **Download**: Automático via `setup-completo-projeto.bat`
- **Uso**: Configurado automaticamente no PATH durante execução

### ✅ Node.js 18.19.0
- **Localização**: `tools/node/node-v18.19.0-win-x64/`
- **Download**: Automático via `setup-completo-projeto.bat`
- **Uso**: Configurado automaticamente no PATH durante execução

### ✅ Oracle Database 21c
- **Instalação**: Sistema (não local)
- **Configuração**: Automática via script
- **Banco**: `consumo_esperto` com usuário da máquina

## 🚀 Como Usar

### 1. Setup Inicial (Primeira Vez)
```bash
# Execute o script principal
setup-completo-projeto.bat
```

**O que acontece:**
- ✅ Baixa Java JDK 17.0.2 localmente
- ✅ Baixa Maven 3.9.6 localmente  
- ✅ Baixa Node.js 18.19.0 localmente
- ✅ Instala/configura Oracle Database 21c
- ✅ Cria banco e usuário Oracle
- ✅ Configura frontend Angular
- ✅ Configura NGROK para APIs bancárias

### 2. Testar Ferramentas (Opcional)
```bash
# Verificar se todas as ferramentas estão funcionando
testar-ferramentas.bat
```

### 3. Limpeza e Reinstalação (Se necessário)
```bash
# Limpar ferramentas corrompidas e reinstalar
limpar-e-reinstalar.bat
```

### 4. Iniciar Serviços
```bash
# Script interativo para escolher serviço
start-servicos.bat
```

**Opções disponíveis:**
1. **Backend Spring Boot** (Oracle)
2. **Frontend Angular**
3. **NGROK** para APIs bancárias
4. **Todos os serviços** (em janelas separadas)

### 3. Comandos Manuais

#### Backend (Spring Boot + Oracle)
```bash
cd backend
..\tools\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run -Dspring.profiles.active=dev
```

#### Frontend (Angular)
```bash
cd frontend
..\tools\node\node-v18.19.0-win-x64\npm.cmd install -g @angular/cli
..\tools\node\node-v18.19.0-win-x64\npx.cmd ng serve
```

#### NGROK (APIs Bancárias)
```bash
cd scripts
start-ngrok.bat
```

## 📁 Estrutura de Pastas

```
ConsumoEsperto/
├── tools/                          # Ferramentas locais
│   ├── java/jdk-17.0.2/           # Java JDK 17.0.2
│   ├── maven/apache-maven-3.9.6/  # Maven 3.9.6
│   └── node/node-v18.19.0-win-x64/ # Node.js 18.19.0
├── backend/                        # Spring Boot + Oracle
├── frontend/                       # Angular
├── scripts/                        # Scripts de automação
├── setup-completo-projeto.bat      # Setup principal
├── testar-ferramentas.bat         # Testar ferramentas
├── limpar-e-reinstalar.bat        # Limpeza e reinstalação
├── start-servicos.bat             # Iniciar serviços
└── README.md                      # Este arquivo
```

## 🔧 Configurações

### Banco Oracle
- **Host**: localhost
- **Porta**: 1521
- **Service**: XE
- **Usuário**: Nome da máquina
- **Senha**: admin123
- **Tablespace**: consumo_esperto_data

### URLs da Aplicação
- **Backend**: http://localhost:8080
- **Frontend**: http://localhost:4200
- **Swagger**: http://localhost:8080/swagger-ui/
- **H2 Console**: http://localhost:8080/h2-console (se usar H2)

### NGROK (APIs Bancárias)
- **API**: https://consumo-esperto-api.ngrok.io
- **Web**: https://consumo-esperto-web.ngrok.io
- **Config**: `scripts/ngrok-config.yml`

## 🚨 Solução de Problemas

### Problemas de Extração/Download
Se as ferramentas falharem durante download ou extração:

```bash
# 1. Limpar tudo e reinstalar
limpar-e-reinstalar.bat

# 2. Reexecutar setup
setup-completo-projeto.bat
```

### Java não encontrado
```bash
# Verificar se está na pasta correta
dir tools\java\jdk-17.0.2\bin\java.exe

# Reexecutar setup se necessário
setup-completo-projeto.bat
```

### Maven não encontrado
```bash
# Verificar se está na pasta correta
dir tools\maven\apache-maven-3.9.6\bin\mvn.cmd

# Reexecutar setup se necessário
setup-completo-projeto.bat
```

### Node.js não encontrado
```bash
# Verificar se está na pasta correta
dir tools\node\node-v18.19.0-win-x64\node.exe

# Reexecutar setup se necessário
setup-completo-projeto.bat
```

### Oracle não conecta
```bash
# Verificar se está rodando
netstat -an | findstr :1521

# Iniciar serviço se necessário
net start OracleServiceXE
```

## 📝 Notas Importantes

- **Ferramentas locais**: Não afetam sistema global
- **Versões específicas**: Garantem compatibilidade
- **Isolamento**: Cada projeto tem suas ferramentas
- **Portabilidade**: Funciona em qualquer máquina Windows
- **Git**: Pasta `tools/` não é versionada (`.gitignore`)

## 🆘 Suporte

Se encontrar problemas:

1. **Verifique logs** dos serviços
2. **Reexecute setup** se necessário
3. **Verifique permissões** de administrador
4. **Verifique conexão** com internet para downloads

---

**🎯 Sistema configurado para funcionar de forma isolada e portável!**
