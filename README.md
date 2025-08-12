# 🚀 CONSUMO ESPERTO - Sistema de Gestão Financeira Inteligente

## 🎯 Visão Geral

O **Consumo Esperto** é uma aplicação completa para gestão financeira pessoal que integra com APIs bancárias reais para sincronização automática de dados financeiros. O sistema oferece uma interface web moderna para configuração de credenciais bancárias e visualização de dados financeiros.

## ✨ Funcionalidades Principais

- 🏦 **Integração com APIs Bancárias Reais**
  - Mercado Pago (cartões de crédito e transações)
  - Nubank (cartões e transações)
  - Itaú (Open Banking)
  - Inter (Open Banking)

- 🔄 **Sincronização Automática de Dados**
  - Categorias de gastos
  - Transações bancárias
  - Faturas de cartão
  - Compras parceladas
  - Parcelas individuais

- 👤 **Sistema de Usuários**
  - Login com Google OAuth2
  - Configurações bancárias por usuário
  - Dados isolados por usuário

- 💻 **Interface Web Moderna**
  - Configuração de credenciais bancárias
  - Visualização de dados financeiros
  - Dashboard responsivo

## 🛠️ Tecnologias Utilizadas

### Backend
- **Java 17** com Spring Boot 3.x
- **Spring Security** com OAuth2 e JWT
- **Spring Data JPA** com Hibernate
- **Maven** para gerenciamento de dependências
- **Oracle Database** (produção) / H2 (desenvolvimento)

### Frontend
- **HTML5, CSS3, JavaScript**
- **Bootstrap 5** para interface responsiva
- **Axios** para requisições HTTP
- **Bootstrap Icons** para ícones

### Infraestrutura
- **ngrok** para túnel de desenvolvimento
- **Maven** para build e dependências
- **Node.js** para dependências frontend

## 🚀 Setup Automatizado (RECOMENDADO)

### ⚡ Setup Completo com Um Comando

Execute apenas **UM** arquivo para configurar todo o ambiente:

```bash
setup-completo-projeto.bat
```

Este script faz **TUDO** automaticamente:
- ✅ Detecta e configura JDK 17 existente
- ✅ Baixa e instala Maven, Node.js, ngrok
- ✅ Configura IntelliJ IDEA automaticamente
- ✅ Cria arquivos .gitignore
- ✅ Instala dependências backend e frontend
- ✅ Configura banco Oracle (opcional)
- ✅ Cria documentação automática
- ✅ Configura Maven para JDK 17

### 🎯 O que o Setup Completo Faz

1. **Detecção Inteligente de JDK**
   - Procura JDKs já instalados no sistema
   - Usa JDK existente quando disponível
   - Baixa JDK 17 apenas se necessário

2. **Configuração Automática do IntelliJ**
   - Cria todos os arquivos `.idea/` necessários
   - Configura JDK 17 automaticamente
   - Configura módulos e workspace

3. **Configuração do Maven**
   - Cria `.mvn/jvm.config` para JDK 17
   - Configura compilador Java 17

4. **Arquivos .gitignore**
   - Cria .gitignore raiz, backend e frontend
   - Exclui arquivos desnecessários do Git
   - Mantém arquivos de configuração importantes

5. **Instalação de Dependências**
   - Backend: `mvn clean install`
   - Frontend: `npm install`

6. **Documentação Automática**
   - Cria READMEs específicos automaticamente
   - Documenta configurações realizadas

## 🔧 Setup Manual (Alternativo)

Se preferir configurar manualmente:

### Pré-requisitos
- Java 17 (JDK)
- Maven 3.9+
- Node.js 20+
- Oracle Database (opcional)

### Backend
```bash
cd backend
mvn clean install
```

### Frontend
```bash
cd frontend
npm install
```

## 🗄️ Banco de Dados

### Oracle (Produção)
O setup completo pode configurar automaticamente o banco Oracle, criando:
- Usuário `consumo_esperto`
- Todas as tabelas necessárias
- Índices e constraints

### H2 (Desenvolvimento)
Para desenvolvimento local, o sistema usa H2 Database com dados em memória.

## 🔑 Configuração de APIs Bancárias

### Interface Web
1. Execute o setup completo
2. Inicie o backend: `start-servicos.bat`
3. Acesse: `http://localhost:8080/bank-config.html`
4. Configure credenciais para cada banco

### APIs Suportadas
- **Mercado Pago**: Cartões e transações
- **Nubank**: Cartões e transações  
- **Itaú**: Open Banking
- **Inter**: Open Banking

## 📱 Executando o Sistema

### Iniciar Serviços
```bash
start-servicos.bat
```

### Verificar Status
```bash
status-servicos.bat
```

### Parar Serviços
```bash
parar-servicos.bat
```

## 🧹 Limpeza e Manutenção

### Limpar Arquivos Antigos
Se você tinha arquivos antigos antes da consolidação:
```bash
limpar-arquivos-antigos.bat
```

### Reconfigurar JDK
Para reconfigurar o JDK:
```bash
setup-completo-projeto.bat
```

## 📚 Documentação

### Documentação Automática
O setup completo cria automaticamente:
- `SOLUCAO_JDK_README.md` - Solução do problema JDK
- `CONFIGURACAO_BANCO_README.md` - Configuração do banco
- `CONFIGURACAO_APIS_BANCARIAS_README.md` - APIs bancárias

### Documentação Manual
- `CONFIGURACAO_BANCO_README.md` - Setup do banco Oracle
- `SINCRONIZACAO_AUTOMATICA_README.md` - Sistema de sincronização
- `bank-api-config-per-user-README.md` - Configuração de APIs por usuário

## 🐛 Troubleshooting

### Problema: "JDK isn't specified for module"
**Solução**: Execute `setup-completo-projeto.bat` - ele detecta e configura automaticamente!

### Problema: Erro de compilação Maven
**Solução**: Execute `setup-completo-projeto.bat` - ele configura Maven para JDK 17!

### Problema: IntelliJ não reconhece projeto
**Solução**: Execute `setup-completo-projeto.bat` - ele cria todas as configurações!

## 🎉 Vantagens do Setup Automatizado

- **Um comando para tudo**: `setup-completo-projeto.bat`
- **Detecção inteligente**: Usa JDKs já instalados
- **Configuração automática**: IntelliJ, Maven, .gitignore
- **Documentação automática**: READMEs criados automaticamente
- **Sem intervenção manual**: Tudo configurado automaticamente
- **Reutilizável**: Execute sempre que precisar reconfigurar

## 🔄 Atualizações

### Para Atualizar o Sistema
1. Execute `git pull` para obter as últimas mudanças
2. Execute `setup-completo-projeto.bat` para reconfigurar
3. Todas as configurações serão atualizadas automaticamente

## 📞 Suporte

### Problemas Comuns
- **JDK**: Execute `setup-completo-projeto.bat`
- **Maven**: Execute `setup-completo-projeto.bat`
- **IntelliJ**: Execute `setup-completo-projeto.bat`
- **Banco**: Execute `setup-completo-projeto.bat`

### Resumo
**Execute sempre `setup-completo-projeto.bat`** - ele resolve 99% dos problemas!

---

**Desenvolvido com ❤️ pela equipe Consumo Esperto**

> 💡 **Dica**: Execute `setup-completo-projeto.bat` sempre que tiver problemas - ele é a solução para tudo!
