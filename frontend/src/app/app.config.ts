import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { routes } from './app.routes';

/**
 * Configuração principal da aplicação Angular ConsumoEsperto
 * 
 * Este arquivo configura todos os providers e serviços essenciais
 * para o funcionamento da aplicação, incluindo roteamento, HTTP,
 * animações e interceptors.
 * 
 * Configurações incluídas:
 * - Roteamento: define as rotas da aplicação
 * - HTTP Client: para comunicação com o backend
 * - Animações: para transições e efeitos visuais
 * - Interceptors: para interceptar requisições HTTP (JWT, etc.)
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export const appConfig: ApplicationConfig = {
  providers: [
    // Configura o roteamento da aplicação usando as rotas definidas
    provideRouter(routes),
    
    // Configura o cliente HTTP com suporte a interceptors
    // Útil para adicionar headers de autenticação automaticamente
    provideHttpClient(withInterceptorsFromDi()),
    
    // Habilita animações básicas do Angular
    // Permite transições suaves entre componentes
    provideAnimations(),
    
    // Habilita animações assíncronas para melhor performance
    // Útil para animações complexas e lazy loading
    provideAnimationsAsync()
  ]
};
