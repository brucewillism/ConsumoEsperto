# ☕ Configuração Java 17 para Projeto ConsumoEsperto

## 🎯 **PROBLEMA RESOLVIDO**

Este projeto requer **Java 17**, mas seu sistema pode estar usando Java 8 ou outra versão. Os scripts criados configuram o Java 17 da pasta `tools/` **apenas para este projeto**, sem afetar o sistema.

## 🚀 **COMO USAR**

### **Opção 1: Script Batch (CMD) - RECOMENDADO**
```cmd
# Na pasta backend, execute:
setup-java17.bat
```

### **Opção 2: Script PowerShell**
```powershell
# Na pasta backend, execute:
.\setup-java17.ps1
```

## ✅ **O QUE OS SCRIPTS FAZEM**

1. **Configuram JAVA_HOME** para `..\tools\java\ms-17.0.15`
2. **Atualizam PATH** temporariamente com o Java 17
3. **Verificam a instalação** mostrando a versão do Java
4. **Mantêm a sessão ativa** para executar comandos Maven

## 🔧 **APÓS A CONFIGURAÇÃO**

### **Compilar o Projeto:**
```bash
mvn clean compile
```

### **Executar a Aplicação:**
```bash
mvn spring-boot:run
```

### **Executar Testes:**
```bash
mvn test
```

## 📋 **VERIFICAÇÕES**

### **Verificar Java:**
```bash
java -version
# Deve mostrar: openjdk version "17.0.15"
```

### **Verificar JAVA_HOME:**
```bash
echo %JAVA_HOME%  # CMD
echo $env:JAVA_HOME  # PowerShell
```

## ⚠️ **IMPORTANTE**

- **Configuração temporária**: Válida apenas para a sessão atual
- **Nova janela = Nova execução**: Execute o script novamente em cada nova janela
- **Não afeta o sistema**: Apenas para este projeto específico
- **Pasta tools obrigatória**: Certifique-se de que a pasta `tools/` existe

## 🚨 **SOLUÇÃO DE PROBLEMAS**

### **Erro: "Pasta tools não encontrada"**
```bash
# Execute primeiro o instalador de ferramentas:
cd ..
.\tools\install-tools.ps1 -Tool java
cd backend
```

### **Erro: "Java não reconhecido"**
```bash
# Execute o script de configuração novamente:
setup-java17.bat
# ou
.\setup-java17.ps1
```

### **Erro: "Maven não encontrado"**
```bash
# Configure também o Maven da pasta tools:
cd ..
.\tools\install-tools.ps1 -Tool maven
cd backend
```

## 🔄 **FLUXO COMPLETO**

1. **Instalar ferramentas** (se necessário):
   ```bash
   cd ..
   .\tools\install-tools.ps1 -Tool all
   cd backend
   ```

2. **Configurar Java 17**:
   ```bash
   setup-java17.bat
   ```

3. **Compilar projeto**:
   ```bash
   mvn clean compile
   ```

4. **Executar aplicação**:
   ```bash
   mvn spring-boot:run
   ```

## 📁 **ARQUIVOS CRIADOS**

- `setup-java17.bat` - Script para CMD
- `setup-java17.ps1` - Script para PowerShell
- `README-JAVA17-SETUP.md` - Este arquivo

## 🎉 **RESULTADO**

Após executar o script, você terá:
- ✅ Java 17 configurado para este projeto
- ✅ Maven funcionando com Java 17
- ✅ Projeto compilando sem erros
- ✅ Aplicação Spring Boot executando

**Lembre-se**: Execute o script sempre que abrir uma nova janela de comando para trabalhar no projeto!
