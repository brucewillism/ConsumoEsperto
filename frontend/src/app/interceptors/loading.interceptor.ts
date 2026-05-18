import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { LoadingService } from '../services/loading.service';

export const LoadingInterceptor: HttpInterceptorFn = (request, next) => {
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
