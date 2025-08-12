# Configuração das APIs Bancárias - ConsumoEsperto

Este documento explica como configurar e integrar as APIs dos bancos Itaú, Mercado Pago, Inter e Nubank no projeto ConsumoEsperto.

## 📋 Índice

1. [Pré-requisitos](#pré-requisitos)
2. [Configuração do ngrok](#configuração-do-ngrok)
3. [APIs Bancárias](#apis-bancárias)
   - [Itaú](#itaú)
   - [Mercado Pago](#mercado-pago)
   - [Inter](#inter)
   - [Nubank](#nubank)
4. [Configuração do Ambiente](#configuração-do-ambiente)
5. [Testando as APIs](#testando-as-apis)
6. [Troubleshooting](#troubleshooting)

## 🚀 Pré-requisitos

- Java 17+ instalado
- Maven instalado
- ngrok instalado ([Download](https://ngrok.com/download))
- Contas de desenvolvedor nos bancos (quando aplicável)

## 🌐 Configuração do ngrok

### 1. Instalar ngrok

```bash
# Windows (PowerShell)
# Baixe de https://ngrok.com/download e extraia para uma pasta no PATH
# Ou coloque o executável na pasta scripts/
```

### 2. Executar script automático

```powershell
# Na pasta scripts/
.\start-ngrok-and-configure.ps1
```

### 3. Configuração manual

```powershell
# Iniciar ngrok
ngrok http 8080

# Em outro terminal, configurar URLs
.\setup-ngrok.ps1 -NgrokUrl "https://seu-tunnel.ngrok.io"
```

## 🏦 APIs Bancárias

### Itaú

#### Configuração
- **Tipo**: Open Banking
- **URL Base**: https://openbanking.itau.com.br
- **Documentação**: https://developers.itau.com.br/

#### Passos para Configuração
1. Acesse https://developers.itau.com.br/
2. Crie uma conta de desenvolvedor
3. Registre sua aplicação
4. Obtenha `client_id` e `client_secret`
5. Configure as URLs de callback

#### Escopos Disponíveis
- `openid` - Identificação do usuário
- `profile` - Perfil do usuário
- `email` - Email do usuário
- `accounts` - Contas bancárias
- `transactions` - Transações

### Mercado Pago

#### Configuração
- **Tipo**: API Oficial
- **URL Base**: https://api.mercadopago.com
- **Documentação**: https://www.mercadopago.com.br/developers

#### Passos para Configuração
1. Acesse https://www.mercadopago.com.br/developers
2. Crie uma conta de desenvolvedor
3. Acesse o painel de desenvolvedores
4. Crie uma aplicação
5. Obtenha `client_id` e `client_secret`

#### Escopos Disponíveis
- `read` - Leitura de dados
- `write` - Escrita de dados

### Inter

#### Configuração
- **Tipo**: Open Banking
- **URL Base**: https://cdp.openbanking.bancointer.com.br
- **Documentação**: https://developers.bancointer.com.br/

#### Passos para Configuração
1. Acesse https://developers.bancointer.com.br/
2. Crie uma conta de desenvolvedor
3. Registre sua aplicação
4. Obtenha `client_id` e `client_secret`
5. Configure as URLs de callback

#### Escopos Disponíveis
- `openid` - Identificação do usuário
- `profile` - Perfil do usuário
- `email` - Email do usuário
- `accounts` - Contas bancárias
- `transactions` - Transações

### Nubank

#### ⚠️ Importante
O Nubank **NÃO disponibiliza API pública oficial**. As opções são:

1. **Aguardar Open Banking** (recomendado)
2. **Usar APIs não oficiais** (não recomendado para produção)
3. **Integração via web scraping** (não recomendado)

#### Configuração (quando disponível)
- **Tipo**: Open Banking (futuro)
- **URL Base**: https://api.nubank.com.br
- **Status**: Em desenvolvimento

## ⚙️ Configuração do Ambiente

### 1. Arquivo .env

Copie o arquivo `env-example.txt` para `.env` e configure suas credenciais:

```bash
# ITAÚ
ITAU_CLIENT_ID=seu_client_id_aqui
ITAU_CLIENT_SECRET=seu_client_secret_aqui

# MERCADO PAGO
MERCADOPAGO_CLIENT_ID=seu_client_id_aqui
MERCADOPAGO_CLIENT_SECRET=seu_client_secret_aqui

# INTER
INTER_CLIENT_ID=seu_client_id_aqui
INTER_CLIENT_SECRET=seu_client_secret_aqui

# NUBANK (quando disponível)
NUBANK_CLIENT_ID=seu_client_id_aqui
NUBANK_CLIENT_SECRET=seu_client_secret_aqui

# URL do ngrok (configurada automaticamente)
NGROK_URL=https://seu-tunnel.ngrok.io
```

### 2. Reiniciar Backend

Após configurar as credenciais, reinicie o backend:

```bash
cd backend
mvn spring-boot:run
```

## 🧪 Testando as APIs

### 1. Swagger UI

Acesse a documentação interativa:
```
https://seu-tunnel.ngrok.io/swagger-ui.html
```

### 2. Endpoints de Teste

#### Health Check
```bash
GET https://seu-tunnel.ngrok.io/actuator/health
```

#### Teste de Autenticação
```bash
POST https://seu-tunnel.ngrok.io/api/auth/login
Content-Type: application/json

{
  "username": "teste",
  "password": "senha123"
}
```

### 3. Teste das APIs Bancárias

#### Itaú
```bash
GET https://seu-tunnel.ngrok.io/api/bank/itau/accounts
Authorization: Bearer {seu_token_jwt}
```

#### Mercado Pago
```bash
GET https://seu-tunnel.ngrok.io/api/bank/mercadopago/accounts
Authorization: Bearer {seu_token_jwt}
```

## 🔧 Troubleshooting

### Problemas Comuns

#### 1. ngrok não inicia
```bash
# Verificar se a porta 8080 está livre
netstat -an | findstr :8080

# Verificar se o backend está rodando
curl http://localhost:8080/actuator/health
```

#### 2. Erro de CORS
```bash
# Verificar configuração no SecurityConfig.java
# Verificar origins permitidos
```

#### 3. Erro de Autenticação
```bash
# Verificar credenciais no arquivo .env
# Verificar se as URLs de callback estão corretas
# Verificar se o ngrok está rodando
```

#### 4. Timeout nas APIs
```bash
# Aumentar timeout no arquivo de configuração
bank.api.timeout=60000
```

### Logs

Para debug, verifique os logs:

```bash
# Backend
tail -f backend/logs/application.log

# ngrok
# Os logs aparecem na janela do ngrok
```

## 📚 Recursos Adicionais

- [Documentação Spring Boot](https://spring.io/projects/spring-boot)
- [Documentação Spring Security](https://spring.io/projects/spring-security)
- [Documentação ngrok](https://ngrok.com/docs)
- [Open Banking Brasil](https://openbankingbrasil.org.br/)

## 🆘 Suporte

Se encontrar problemas:

1. Verifique os logs do backend
2. Verifique se o ngrok está funcionando
3. Teste as APIs individualmente
4. Verifique as credenciais e URLs
5. Consulte a documentação oficial de cada banco

---

**Nota**: As configurações das APIs bancárias podem mudar. Sempre consulte a documentação oficial mais recente de cada banco.
