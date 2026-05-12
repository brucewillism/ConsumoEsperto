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
 * O feedback humanizado para outros códigos HTTP fica a cargo do {@link ErrorInterceptor}.
 */
export const AuthInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const toastService = inject(ToastService);

  const token = authService.getToken();

  if (token) {
    request = request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
        'ngrok-skip-browser-warning': 'true',
      },
    });
  } else {
    request = request.clone({
      setHeaders: {
        'ngrok-skip-browser-warning': 'true',
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
    });
  }

  if (!request.headers.has('ngrok-skip-browser-warning')) {
    request = request.clone({
      setHeaders: {
        'ngrok-skip-browser-warning': 'true',
      },
    });
  }

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && token) {
        toastService.sessionProtocolExpired();
        authService.logout();
        router.navigate(['/login']);
      }

      return throwError(() => error);
    })
  );
};
