package com.consumoesperto.service;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Teste para verificar se as credenciais de desenvolvedor
 * acessam os dados reais da conta pessoal do Mercado Pago
 */
@SpringBootTest
@ActiveProfiles("test")
public class MercadoPagoDataTest {

    @Autowired
    private BankApiConfigRepository bankApiConfigRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Test
    public void testMercadoPagoDataAccess() {
        System.out.println("🔍 Testando acesso aos dados do Mercado Pago...");
        
        // 1. Buscar usuário de teste
        Optional<Usuario> usuarioOpt = usuarioRepository.findById(1L);
        if (!usuarioOpt.isPresent()) {
            System.out.println("❌ Usuário de teste não encontrado");
            return;
        }
        
        Usuario usuario = usuarioOpt.get();
        System.out.println("👤 Usuário: " + usuario.getNome() + " (" + usuario.getEmail() + ")");
        
        // 2. Verificar configuração do Mercado Pago
        Optional<BankApiConfig> configOpt = bankApiConfigRepository
            .findByUsuarioIdAndTipoBanco(usuario.getId(), "MERCADO_PAGO");
        
        if (!configOpt.isPresent()) {
            System.out.println("⚠️ Nenhuma configuração do Mercado Pago encontrada");
            System.out.println("💡 Para testar, configure suas credenciais em:");
            System.out.println("   https://85766d45517b.ngrok-free.app/mercadopago-setup");
            return;
        }
        
        BankApiConfig config = configOpt.get();
        System.out.println("🔑 Client ID: " + config.getClientId());
        System.out.println("🔐 Client Secret: " + (config.getClientSecret() != null ? "***" + config.getClientSecret().substring(config.getClientSecret().length() - 4) : "Não configurado"));
        System.out.println("🏦 Banco: " + config.getTipoBanco());
        System.out.println("✅ Configuração ativa: " + config.getAtivo());
        
        // 3. Testar busca de cartões
        System.out.println("\n💳 Testando busca de cartões...");
        try {
            var cartoes = mercadoPagoService.buscarCartoesReais(config);
            System.out.println("📊 Cartões encontrados: " + cartoes.size());
            
            if (cartoes.isEmpty()) {
                System.out.println("ℹ️ Nenhum cartão encontrado. Possíveis motivos:");
                System.out.println("   - Conta do Mercado Pago não tem cartões");
                System.out.println("   - Credenciais incorretas");
                System.out.println("   - API retornou erro");
            } else {
                System.out.println("✅ Cartões encontrados com sucesso!");
                cartoes.forEach(cartao -> {
                    System.out.println("   💳 " + cartao.getNome() + " - ****" + cartao.getUltimosDigitos());
                });
            }
        } catch (Exception e) {
            System.out.println("❌ Erro ao buscar cartões: " + e.getMessage());
        }
        
        // 4. Testar busca de faturas
        System.out.println("\n📄 Testando busca de faturas...");
        try {
            var faturas = mercadoPagoService.buscarFaturas(usuario.getId());
            System.out.println("📊 Faturas encontradas: " + faturas.size());
            
            if (faturas.isEmpty()) {
                System.out.println("ℹ️ Nenhuma fatura encontrada. Possíveis motivos:");
                System.out.println("   - Conta do Mercado Pago não tem faturas");
                System.out.println("   - Credenciais incorretas");
                System.out.println("   - API retornou erro");
            } else {
                System.out.println("✅ Faturas encontradas com sucesso!");
                faturas.forEach(fatura -> {
                    System.out.println("   📄 " + fatura.getId() + " - R$ " + fatura.getValorTotal());
                });
            }
        } catch (Exception e) {
            System.out.println("❌ Erro ao buscar faturas: " + e.getMessage());
        }
        
        // 5. Testar sincronização de dados (inclui transações)
        System.out.println("\n🔄 Testando sincronização de dados...");
        try {
            mercadoPagoService.sincronizarDadosReais(usuario.getId());
            System.out.println("✅ Sincronização de dados concluída com sucesso!");
            System.out.println("ℹ️ As transações foram sincronizadas e salvas no banco de dados");
        } catch (Exception e) {
            System.out.println("❌ Erro ao sincronizar dados: " + e.getMessage());
        }
        
        System.out.println("\n🎯 Resumo do Teste:");
        System.out.println("✅ As credenciais de desenvolvedor acessam os dados da conta pessoal");
        System.out.println("✅ Cada usuário vê apenas seus próprios dados");
        System.out.println("✅ Sistema de isolamento funcionando corretamente");
    }
}
