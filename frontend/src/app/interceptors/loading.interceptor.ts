import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Interceptor de loading desativado: o overlay global bloqueava formulários
 * durante polling, recargas silenciosas e GETs em paralelo sem atualizar a UI.
 *
 * Feedback de carregamento fica a cargo de cada tela (flags locais, spinners
 * nos botões) e do overlay de autenticação (login / Google) via LoadingService.
 */
export const LoadingInterceptor: HttpInterceptorFn = (request, next) => next(request);
