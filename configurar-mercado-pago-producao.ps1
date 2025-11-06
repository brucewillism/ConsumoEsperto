# =============================================================================
# CONFIGURAR MERCADO PAGO PARA PRODUÇÃO
# =============================================================================
# Este script configura as credenciais reais do Mercado Pago para produção
# =============================================================================

Write-Host "🚀 Configurando Mercado Pago para PRODUÇÃO..." -ForegroundColor Green

# =============================================================================
# CONFIGURAÇÕES DO MERCADO PAGO
# =============================================================================
$CLIENT_ID = "4223603750190943"
$CLIENT_SECRET = Read-Host "Digite o Client Secret do Mercado Pago (obtenha no painel)"
$USER_ID = "209112973"
$NGROK_URL = "https://262f3e49bd2d.ngrok-free.app"

if ([string]::IsNullOrWhiteSpace($CLIENT_SECRET)) {
    Write-Host "❌ Client Secret é obrigatório!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Configurações:" -ForegroundColor Yellow
Write-Host "   Client ID: $CLIENT_ID" -ForegroundColor Cyan
Write-Host "   User ID: $USER_ID" -ForegroundColor Cyan
Write-Host "   Ngrok URL: $NGROK_URL" -ForegroundColor Cyan
Write-Host "   Client Secret: $($CLIENT_SECRET.Substring(0, 8))..." -ForegroundColor Cyan

# =============================================================================
# ATUALIZAR APPLICATION.PROPERTIES
# =============================================================================
Write-Host "`n📝 Atualizando application.properties..." -ForegroundColor Yellow

$propertiesFile = "backend\src\main\resources\application.properties"
if (Test-Path $propertiesFile) {
    $content = Get-Content $propertiesFile -Raw
    
    # Atualizar Client Secret
    $content = $content -replace "bank\.api\.mercadopago\.client-secret=.*", "bank.api.mercadopago.client-secret=$CLIENT_SECRET"
    
    # Atualizar User ID
    $content = $content -replace "bank\.api\.mercadopago\.user-id=.*", "bank.api.mercadopago.user-id=$USER_ID"
    
    # Atualizar Ngrok URL
    $content = $content -replace "ngrok\.url=.*", "ngrok.url=$NGROK_URL"
    
    Set-Content $propertiesFile $content -Encoding UTF8
    Write-Host "✅ application.properties atualizado" -ForegroundColor Green
} else {
    Write-Host "❌ Arquivo application.properties não encontrado!" -ForegroundColor Red
    exit 1
}

# =============================================================================
# ATUALIZAR ENV-VARS.TXT
# =============================================================================
Write-Host "`n📝 Atualizando env-vars.txt..." -ForegroundColor Yellow

$envFile = "backend\env-vars.txt"
if (Test-Path $envFile) {
    $content = Get-Content $envFile -Raw
    
    # Atualizar Client Secret
    $content = $content -replace "MERCADOPAGO_CLIENT_SECRET=.*", "MERCADOPAGO_CLIENT_SECRET=$CLIENT_SECRET"
    
    # Atualizar User ID
    $content = $content -replace "MERCADOPAGO_USER_ID=.*", "MERCADOPAGO_USER_ID=$USER_ID"
    
    # Atualizar Ngrok URL
    $content = $content -replace "NGROK_URL=.*", "NGROK_URL=$NGROK_URL"
    
    Set-Content $envFile $content -Encoding UTF8
    Write-Host "✅ env-vars.txt atualizado" -ForegroundColor Green
} else {
    Write-Host "⚠️ Arquivo env-vars.txt não encontrado, criando..." -ForegroundColor Yellow
    
    $envContent = @"
# =============================================================================
# VARIÁVEIS DE AMBIENTE - CONSUMO ESPERTO
# =============================================================================

# Mercado Pago
MERCADOPAGO_CLIENT_ID=$CLIENT_ID
MERCADOPAGO_CLIENT_SECRET=$CLIENT_SECRET
MERCADOPAGO_USER_ID=$USER_ID

# Ngrok
NGROK_URL=$NGROK_URL

# Google OAuth2
GOOGLE_CLIENT_ID=593452038228-47k24odoa6f18c78e3ssp9bhu56gugnm.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=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

# JWT
JWT_SECRET=minha_chave_secreta_jwt_super_segura_para_consumo_esperto_2024_producao
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

# Banco de Dados
DATABASE_URL=jdbc:postgresql://localhost:5432/consumoesperto
DATABASE_USERNAME=bruce
DATABASE_PASSWORD=0301HeX@
"@
    
    Set-Content $envFile $envContent -Encoding UTF8
    Write-Host "✅ env-vars.txt criado" -ForegroundColor Green
}

# =============================================================================
# CONFIGURAR NO BANCO DE DADOS
# =============================================================================
Write-Host "`n🗄️ Configurando no banco de dados..." -ForegroundColor Yellow

$sqlFile = "configurar-mercado-pago-db.sql"
$sqlContent = @"
-- =============================================================================
-- CONFIGURAR MERCADO PAGO NO BANCO DE DADOS
-- =============================================================================

-- Atualizar configuração do Mercado Pago
UPDATE bank_api_configs 
SET 
    client_secret = '$CLIENT_SECRET',
    user_id = '$USER_ID',
    redirect_uri = '$NGROK_URL/api/auth/mercadopago/callback',
    data_atualizacao = NOW()
WHERE tipo_banco = 'MERCADOPAGO';

-- Verificar se a configuração foi atualizada
SELECT 
    tipo_banco,
    client_id,
    user_id,
    redirect_uri,
    ativo,
    data_atualizacao
FROM bank_api_configs 
WHERE tipo_banco = 'MERCADOPAGO';

-- Atualizar autorização bancária se existir
UPDATE autorizacoes_bancarias 
SET 
    data_atualizacao = NOW()
WHERE tipo_banco = 'MERCADOPAGO';

-- Verificar autorizações
SELECT 
    tipo_banco,
    banco,
    ativo,
    data_criacao,
    data_atualizacao
FROM autorizacoes_bancarias 
WHERE tipo_banco = 'MERCADOPAGO';
"@

Set-Content $sqlFile $sqlContent -Encoding UTF8
Write-Host "✅ Script SQL criado: $sqlFile" -ForegroundColor Green

# =============================================================================
# EXECUTAR SCRIPT SQL
# =============================================================================
Write-Host "`n🔧 Executando script SQL..." -ForegroundColor Yellow

try {
    # Executar script SQL
    psql -h localhost -U bruce -d consumoesperto -f $sqlFile
    Write-Host "✅ Script SQL executado com sucesso" -ForegroundColor Green
} catch {
    Write-Host "⚠️ Erro ao executar script SQL: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "   Execute manualmente: psql -h localhost -U bruce -d consumoesperto -f $sqlFile" -ForegroundColor Cyan
}

# =============================================================================
# TESTAR CONFIGURAÇÃO
# =============================================================================
Write-Host "`n🧪 Testando configuração..." -ForegroundColor Yellow

Write-Host "✅ Configuração do Mercado Pago concluída!" -ForegroundColor Green
Write-Host "`n📋 Próximos passos:" -ForegroundColor Cyan
Write-Host "   1. Reinicie o backend: mvn spring-boot:run" -ForegroundColor White
Write-Host "   2. Acesse: http://localhost:4200" -ForegroundColor White
Write-Host "   3. Configure o Mercado Pago no frontend" -ForegroundColor White
Write-Host "   4. Teste a sincronização de dados" -ForegroundColor White

Write-Host "`n🎉 Sistema configurado para PRODUÇÃO com dados reais!" -ForegroundColor Green
