import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * Interceptor HTTP para autenticação JWT (Angular 17+)
 * 
 * Este interceptor intercepta todas as requisições HTTP e:
 * - Adiciona automaticamente o token JWT no header Authorization
 * - Trata erros de autenticação (401 Unauthorized)
 * - Redireciona para login quando o token expira ou é inválido
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export const AuthInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const toastService = inject(ToastService);

  // Obtém o token JWT atual
  const token = authService.getToken();
  
  // Se há um token válido, adiciona ao header Authorization
  if (token) {
    request = request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
        'ngrok-skip-browser-warning': 'true'
      }
    });
  } else {
    // Adiciona header do ngrok mesmo sem token
    request = request.clone({
      setHeaders: {
        'ngrok-skip-browser-warning': 'true',
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      }
    });
  }
  
  // SEMPRE adiciona header do ngrok para todas as requisições
  if (!request.headers.has('ngrok-skip-browser-warning')) {
    request = request.clone({
      setHeaders: {
        'ngrok-skip-browser-warning': 'true'
      }
    });
  }

  // Processa a requisição e trata erros de autenticação
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      // Se receber 401 (Unauthorized), o token pode ter expirado
      if (error.status === 401) {
        toastService.warning('Sua sessão expirou. Faça login novamente.');
        
        // Limpa dados de autenticação
        authService.logout();
        
        // Redireciona para página de login
        router.navigate(['/login']);
      }
      
      const isHandledByComponent =
        request.url.includes('/usuarios/perfil') ||
        request.url.includes('/usuarios/perfil-jarvis') ||
        request.url.includes('/usuarios/preferencia-tratamento') ||
        request.url.includes('/usuarios/whatsapp/vincular') ||
        request.url.includes('/usuarios/whatsapp/desvincular') ||
        request.url.includes('/auth/google') ||
        request.url.includes('/notificacoes') ||
        request.url.includes('/relatorios/alertas') ||
        request.url.includes('/projecoes') ||
        request.url.includes('/renda-config') ||
        request.url.includes('/score');

      if (error.status !== 401 && !isHandledByComponent) {
        toastService.error('Erro na operação. Tente novamente.');
      }

      // Propaga o erro para ser tratado pelos componentes
      return throwError(() => error);
    })
  );
};
