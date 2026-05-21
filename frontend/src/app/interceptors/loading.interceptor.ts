import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { LoadingService } from '../services/loading.service';

/** Pedidos sem overlay global (login tem spinner no botão; popup Google não precisa de segundo loading). */
function skipLoadingOverlay(url: string): boolean {
  return /\/auth\/(login|google|registro|refresh)/i.test(url);
}

export const LoadingInterceptor: HttpInterceptorFn = (request, next) => {
  if (skipLoadingOverlay(request.url)) {
    return next(request);
  }

  const loadingService = inject(LoadingService);
  loadingService.show();

  let downstream;
  try {
    downstream = next(request);
  } catch (e) {
    loadingService.hide();
    throw e;
  }

  return downstream.pipe(
    finalize(() => loadingService.hide())
  );
};
