import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

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

  // Obtém o token JWT atual
  const token = authService.getToken();
  
  // Debug: log da requisição e token
  console.log(`[AuthInterceptor] Interceptando requisição para: ${request.url}`);
  console.log(`[AuthInterceptor] Token disponível: ${token ? 'SIM' : 'NÃO'}`);
  
  // Se há um token válido, adiciona ao header Authorization
  if (token) {
    request = request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    console.log(`[AuthInterceptor] Token adicionado ao header: Bearer ${token.substring(0, 20)}...`);
  } else {
    console.warn(`[AuthInterceptor] Nenhum token encontrado para requisição: ${request.url}`);
  }

  // Processa a requisição e trata erros de autenticação
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      console.error(`[AuthInterceptor] Erro na requisição ${request.url}:`, error);
      
      // Se receber 401 (Unauthorized), o token pode ter expirado
      if (error.status === 401) {
        console.warn('[AuthInterceptor] Token JWT expirado ou inválido. Redirecionando para login...');
        
        // Limpa dados de autenticação
        authService.logout();
        
        // Redireciona para página de login
        router.navigate(['/login']);
      }
      
      // Propaga o erro para ser tratado pelos componentes
      return throwError(() => error);
    })
  );
};
