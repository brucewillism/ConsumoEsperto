import { HttpInterceptorFn } from '@angular/common/http';
import { ecoEdgeHeaders, EcoMode, ECO_HEADERS } from '../shared/eco/eco-envelope';

function isEcoEdge(url: string): boolean {
  return url.includes('/ia-chat') || url.includes('/edith') || url.includes('/capabilities');
}

function modeFor(url: string, body: unknown): EcoMode {
  if (url.includes('/tasks/') && url.includes('/events')) {
    return 'DEEP';
  }
  if (body && typeof body === 'object' && 'capability' in body) {
    const cap = (body as { capability?: string }).capability;
    if (cap && cap.trim()) {
      return 'INTERACTIVE';
    }
  }
  if (url.includes('/ia-chat') || url.includes('/messages')) {
    return 'BALANCED';
  }
  if (url.includes('/capabilities') && url.includes('invoke')) {
    return 'INTERACTIVE';
  }
  return 'BALANCED';
}

export const EcoEnvelopeInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isEcoEdge(request.url) || request.headers.has(ECO_HEADERS.TRACE_ID)) {
    return next(request);
  }
  const mode = modeFor(request.url, request.body);
  return next(request.clone({ setHeaders: ecoEdgeHeaders(mode) }));
};
