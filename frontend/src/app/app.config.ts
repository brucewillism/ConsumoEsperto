import { ApplicationConfig, ErrorHandler } from '@angular/core';
import { Overlay } from '@angular/cdk/overlay';
import { provideRouter, withNavigationErrorHandler } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE } from '@angular/material/core';
import { MAT_DIALOG_DEFAULT_OPTIONS } from '@angular/material/dialog';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { BR_DATE_FORMATS, BrDateAdapter } from './shared/locale/br-date.adapter';
import { provideCeOverlayScrollSupport } from './shared/ce-overlay-scroll.util';

import { provideCharts, withDefaultRegisterables } from 'ng2-charts';

import { routes } from './app.routes';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { ErrorInterceptor } from './interceptors/error.interceptor';
import { LoadingInterceptor } from './interceptors/loading.interceptor';
import {
  ChunkErrorHandler,
  isChunkLoadError,
  recarregarPorChunkDesatualizado,
} from './shared/chunk-error.handler';

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
    // Configura o roteamento da aplicação usando as rotas definidas.
    // Se a navegação falhar por chunk lazy desatualizado (deploy novo), recarrega
    // para o navegador buscar o index.html e os chunks atuais.
    provideRouter(
      routes,
      withNavigationErrorHandler((evento: unknown) => {
        const erro = (evento as { error?: unknown })?.error ?? evento;
        if (isChunkLoadError(erro)) {
          recarregarPorChunkDesatualizado();
        }
      })
    ),
    
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
    
    // Calendário Material — locale e formato Brasil (dd/MM/yyyy)
    { provide: MAT_DATE_LOCALE, useValue: 'pt-BR' },
    { provide: DateAdapter, useClass: BrDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: BR_DATE_FORMATS },

    // Labels sempre no notch (evita estado quebrado ao abrir modais)
    {
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: {
        appearance: 'outline',
        floatLabel: 'always',
        subscriptSizing: 'fixed'
      }
    },

    // block: CDK bloqueia scroll da página mas permite rolar dentro de cdkScrollable
    {
      provide: MAT_DIALOG_DEFAULT_OPTIONS,
      deps: [Overlay],
      useFactory: (overlay: Overlay) => ({
        maxHeight: '90vh',
        disableClose: true,
        scrollStrategy: overlay.scrollStrategies.block(),
      }),
    },

    provideCeOverlayScrollSupport(),

    // Chart.js (ng2-charts v8) — registra controllers/plugins (line, doughnut, filler…)
    provideCharts(withDefaultRegisterables()),

    // Erros de chunk fora do fluxo de navegação também forçam reload controlado.
    { provide: ErrorHandler, useClass: ChunkErrorHandler }
  ]
};
