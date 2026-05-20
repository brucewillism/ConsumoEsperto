/**
 * Evolution API às vezes devolve estruturas aninhadas ({ base64 }, { response: { message } })
 * onde o esperado é uma string simples — evitar "[object Object]" no template Angular.
 */

function pickNested(obj: Record<string, unknown>, path: string[]): unknown {
  let cur: unknown = obj;
  for (const k of path) {
    if (cur == null || typeof cur !== 'object') return undefined;
    cur = (cur as Record<string, unknown>)[k];
  }
  return cur;
}

/** Texto útil para avisos (banner, pairing code como string legada). */
export function coerceEvolutionUserFacingText(value: unknown): string | undefined {
  if (value == null) return undefined;
  if (typeof value === 'string') {
    const t = value.trim();
    return t.length ? t : undefined;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  if (Array.isArray(value)) {
    const parts = value
      .map((x) => coerceEvolutionUserFacingText(x))
      .filter((x): x is string => !!x?.length);
    return parts.length ? parts.join(' ') : undefined;
  }
  if (typeof value === 'object') {
    const o = value as Record<string, unknown>;
    const candidates: unknown[] = [
      pickNested(o, ['response', 'message']),
      o['message'],
      o['error'],
      o['text'],
      o['description'],
      pickNested(o, ['response', 'error']),
    ];
    for (const c of candidates) {
      const s = coerceEvolutionUserFacingText(c);
      if (s) return s;
    }
    try {
      return JSON.stringify(value);
    } catch {
      return undefined;
    }
  }
  return undefined;
}

/** Data URI PNG/JPEG ou string pura já no formato esperado pelo <img [src]>. */
export function coerceEvolutionQrDataUri(value: unknown): string | undefined {
  if (value == null) return undefined;

  const asDataUri = (rawBase64OrUri: string, mime?: string): string | undefined => {
    const u = rawBase64OrUri.trim();
    if (!u.length || u === '[object Object]') return undefined;
    if (u.startsWith('data:image/')) return u;
    const m = mime && mime.includes('/') ? mime : 'image/png';
    return `data:${m};base64,${u}`;
  };

  if (typeof value === 'string') {
    return asDataUri(value, undefined);
  }
  if (typeof value === 'object' && value !== null) {
    const o = value as Record<string, unknown>;
    const b64Candidates = ['base64', 'pngBase64', 'jpegBase64', 'data'];
    for (const k of b64Candidates) {
      const raw = o[k];
      if (typeof raw === 'string' && raw.trim().length > 80) {
        const mime =
          coerceEvolutionUserFacingText(o['mimeType'] ?? o['mimetype'] ?? o['mime']) ??
          undefined;
        return asDataUri(raw, mime);
      }
    }
    const nested = o['qrcode'] ?? o['qrCode'] ?? o['qr'] ?? o['response'];
    if (nested != null && typeof nested === 'object') {
      return coerceEvolutionQrDataUri(nested);
    }
  }
  return undefined;
}
