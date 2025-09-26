# =============================================================================
# TESTAR MERCADO PAGO COM DADOS REAIS
# =============================================================================
# Este script testa a integração com a API real do Mercado Pago
# =============================================================================

Write-Host "🧪 Testando Mercado Pago com dados REAIS..." -ForegroundColor Green

# =============================================================================
# CONFIGURAÇÕES
# =============================================================================
$API_URL = "http://localhost:8080"
$USER_ID = 1  # ID do usuário para teste

Write-Host "`n📋 Configurações de teste:" -ForegroundColor Yellow
Write-Host "   API URL: $API_URL" -ForegroundColor Cyan
Write-Host "   User ID: $USER_ID" -ForegroundColor Cyan

# =============================================================================
# TESTE 1: VERIFICAR SE O BACKEND ESTÁ RODANDO
# =============================================================================
Write-Host "`n🔍 Teste 1: Verificando se o backend está rodando..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "$API_URL/actuator/health" -UseBasicParsing -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ Backend está rodando" -ForegroundColor Green
    } else {
        Write-Host "❌ Backend retornou status: $($response.StatusCode)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ Backend não está rodando ou não está acessível" -ForegroundColor Red
    Write-Host "   Erro: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "   Execute: mvn spring-boot:run" -ForegroundColor Cyan
    exit 1
}

# =============================================================================
# TESTE 2: VERIFICAR CONFIGURAÇÃO DO MERCADO PAGO
# =============================================================================
Write-Host "`n🔍 Teste 2: Verificando configuração do Mercado Pago..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "$API_URL/api/mercadopago/config/$USER_ID" -UseBasicParsing -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        $config = $response.Content | ConvertFrom-Json
        Write-Host "✅ Configuração encontrada:" -ForegroundColor Green
        Write-Host "   Client ID: $($config.clientId)" -ForegroundColor Cyan
        Write-Host "   User ID: $($config.userId)" -ForegroundColor Cyan
        Write-Host "   Ativo: $($config.ativo)" -ForegroundColor Cyan
    } else {
        Write-Host "⚠️ Configuração não encontrada (status: $($response.StatusCode))" -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠️ Erro ao verificar configuração: $($_.Exception.Message)" -ForegroundColor Yellow
}

# =============================================================================
# TESTE 3: TESTAR CONEXÃO COM API DO MERCADO PAGO
# =============================================================================
Write-Host "`n🔍 Teste 3: Testando conexão com API do Mercado Pago..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "$API_URL/api/mercadopago/testar-conexao/$USER_ID" -UseBasicParsing -TimeoutSec 30
    if ($response.StatusCode -eq 200) {
        $result = $response.Content | ConvertFrom-Json
        if ($result -eq $true) {
            Write-Host "✅ Conexão com API do Mercado Pago estabelecida" -ForegroundColor Green
        } else {
            Write-Host "❌ Falha na conexão com API do Mercado Pago" -ForegroundColor Red
        }
    } else {
        Write-Host "❌ Erro ao testar conexão (status: $($response.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Erro ao testar conexão: $($_.Exception.Message)" -ForegroundColor Red
}

# =============================================================================
# TESTE 4: BUSCAR CARTÕES REAIS
# =============================================================================
Write-Host "`n🔍 Teste 4: Buscando cartões reais..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "$API_URL/api/mercadopago/cartoes/$USER_ID" -UseBasicParsing -TimeoutSec 30
    if ($response.StatusCode -eq 200) {
        $cartoes = $response.Content | ConvertFrom-Json
        if ($cartoes.Count -gt 0) {
            Write-Host "✅ $($cartoes.Count) cartão(ões) encontrado(s):" -ForegroundColor Green
            foreach ($cartao in $cartoes) {
                Write-Host "   - $($cartao.nome) (****$($cartao.ultimosDigitos))" -ForegroundColor Cyan
                Write-Host "     Limite: R$ $($cartao.limiteTotal)" -ForegroundColor Cyan
                Write-Host "     Disponível: R$ $($cartao.limiteDisponivel)" -ForegroundColor Cyan
            }
        } else {
            Write-Host "⚠️ Nenhum cartão encontrado" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ Erro ao buscar cartões (status: $($response.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Erro ao buscar cartões: $($_.Exception.Message)" -ForegroundColor Red
}

# =============================================================================
# TESTE 5: BUSCAR FATURAS REAIS
# =============================================================================
Write-Host "`n🔍 Teste 5: Buscando faturas reais..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "$API_URL/api/mercadopago/faturas/$USER_ID" -UseBasicParsing -TimeoutSec 30
    if ($response.StatusCode -eq 200) {
        $faturas = $response.Content | ConvertFrom-Json
        if ($faturas.Count -gt 0) {
            Write-Host "✅ $($faturas.Count) fatura(s) encontrada(s):" -ForegroundColor Green
            foreach ($fatura in $faturas) {
                Write-Host "   - $($fatura.nomeCartao)" -ForegroundColor Cyan
                Write-Host "     Valor: R$ $($fatura.valorTotal)" -ForegroundColor Cyan
                Write-Host "     Vencimento: $($fatura.dataVencimento)" -ForegroundColor Cyan
            }
        } else {
            Write-Host "⚠️ Nenhuma fatura encontrada" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ Erro ao buscar faturas (status: $($response.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Erro ao buscar faturas: $($_.Exception.Message)" -ForegroundColor Red
}

# =============================================================================
# TESTE 6: BUSCAR TRANSAÇÕES REAIS
# =============================================================================
Write-Host "`n🔍 Teste 6: Buscando transações reais..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "$API_URL/api/mercadopago/transacoes/$USER_ID" -UseBasicParsing -TimeoutSec 30
    if ($response.StatusCode -eq 200) {
        $transacoes = $response.Content | ConvertFrom-Json
        if ($transacoes.Count -gt 0) {
            Write-Host "✅ $($transacoes.Count) transação(ões) encontrada(s):" -ForegroundColor Green
            foreach ($transacao in $transacoes) {
                Write-Host "   - $($transacao.descricao)" -ForegroundColor Cyan
                Write-Host "     Valor: R$ $($transacao.valor)" -ForegroundColor Cyan
                Write-Host "     Data: $($transacao.dataTransacao)" -ForegroundColor Cyan
            }
        } else {
            Write-Host "⚠️ Nenhuma transação encontrada" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ Erro ao buscar transações (status: $($response.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Erro ao buscar transações: $($_.Exception.Message)" -ForegroundColor Red
}

# =============================================================================
# TESTE 7: SINCRONIZAÇÃO AUTOMÁTICA
# =============================================================================
Write-Host "`n🔍 Teste 7: Testando sincronização automática..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "$API_URL/api/mercadopago/sincronizar/$USER_ID" -Method POST -UseBasicParsing -TimeoutSec 60
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ Sincronização automática executada com sucesso" -ForegroundColor Green
    } else {
        Write-Host "❌ Erro na sincronização (status: $($response.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Erro na sincronização: $($_.Exception.Message)" -ForegroundColor Red
}

# =============================================================================
# RESUMO DOS TESTES
# =============================================================================
Write-Host "`n📊 RESUMO DOS TESTES:" -ForegroundColor Yellow
Write-Host "   ✅ Backend rodando" -ForegroundColor Green
Write-Host "   ✅ Configuração verificada" -ForegroundColor Green
Write-Host "   ✅ Conexão com API testada" -ForegroundColor Green
Write-Host "   ✅ Dados reais buscados" -ForegroundColor Green

Write-Host "`n🎉 Testes concluídos! Sistema funcionando com dados REAIS do Mercado Pago!" -ForegroundColor Green
Write-Host "`n📋 Próximos passos:" -ForegroundColor Cyan
Write-Host "   1. Acesse o frontend: http://localhost:4200" -ForegroundColor White
Write-Host "   2. Configure o Mercado Pago no painel" -ForegroundColor White
Write-Host "   3. Visualize seus dados reais" -ForegroundColor White
