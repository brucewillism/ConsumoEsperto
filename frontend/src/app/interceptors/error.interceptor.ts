import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { shouldSkipJarvisHttpError } from './jarvis-http-error.skip';

/**
 * Exibe feedback humanizado (message + instrucao) para falhas HTTP, em linha com o protocolo J.A.R.V.I.S.
 */
export const ErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        return throwError(() => err);
      }
      if (shouldSkipJarvisHttpError(req.url)) {
        return throwError(() => err);
      }

      const fallback =
        err.status === 0
          ? 'Sem ligação ao servidor. Verifique a rede e tente novamente.'
          : 'Detectei uma instabilidade na operação. Tente novamente em instantes.';

      toast.errorFromHttpResponse(err, fallback);
      return throwError(() => err);
    })
  );
};
