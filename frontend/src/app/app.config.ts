import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideNativeDateAdapter } from '@angular/material/core';

import { routes } from './app.routes';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { ErrorInterceptor } from './interceptors/error.interceptor';
import { LoadingInterceptor } from './interceptors/loading.interceptor';

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
    
    // Configura o cliente HTTP com interceptors personalizados
    // - AuthInterceptor: adiciona automaticamente token JWT nas requisições
    provideHttpClient(
      withInterceptors([LoadingInterceptor, ErrorInterceptor, AuthInterceptor])
    ),
    
    // Habilita animações básicas do Angular
    // Permite transições suaves entre componentes
    provideAnimations(),
    
    // Habilita animações assíncronas para melhor performance
    // Útil para animações complexas e lazy loading
    provideAnimationsAsync(),
    
    // Fornece adaptador de data nativo para Angular Material
    provideNativeDateAdapter()
  ]
};
