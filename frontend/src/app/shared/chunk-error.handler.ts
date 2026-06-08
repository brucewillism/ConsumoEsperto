import { ErrorHandler, Injectable } from '@angular/core';

/**
 * Detecta falhas de carregamento de chunk/módulo dinâmico — sintoma clássico de
 * build novo no servidor enquanto o navegador ainda referencia hashes antigos
 * (acontece após um deploy). Nesses casos o lazy-load 404 e a navegação falha
 * silenciosamente: o item do menu "não reage".
 */
export function isChunkLoadError(error: unknown): boolean {
  const e = error as { name?: string; message?: string } | null | undefined;
  const name = e?.name ?? '';
  const msg = e?.message ?? String(error ?? '');
  return (
    name === 'ChunkLoadError' ||
    /Loading chunk [\w-]+ failed/i.test(msg) ||
    /Failed to fetch dynamically imported module/i.test(msg) ||
    /error loading dynamically imported module/i.test(msg) ||
    /Importing a module script failed/i.test(msg)
  );
}

/** Recarrega no máximo uma vez por janela curta, evitando loop de reload. */
export function recarregarPorChunkDesatualizado(): void {
  try {
    const key = 'ce_chunk_reload_ts';
    const ultimo = Number(sessionStorage.getItem(key) ?? '0');
    const agora = Date.now();
    if (agora - ultimo < 10000) {
      return;
    }
    sessionStorage.setItem(key, String(agora));
  } catch {
    // sessionStorage indisponível: segue com o reload mesmo assim.
  }
  window.location.reload();
}

@Injectable()
export class ChunkErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    if (isChunkLoadError(error)) {
      recarregarPorChunkDesatualizado();
      return;
    }
    console.error(error);
  }
}
