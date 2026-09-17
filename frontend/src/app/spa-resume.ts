/**
 * Restaura o caminho Angular depois de um 302 do Spring (`/?ce_resume=...`)
 * quando o proxy enviou F5 / URL directa para o backend.
 */
export const SPA_RESUME_QUERY = 'ce_resume';

export function isSafeSpaResumePath(value: string): boolean {
  if (!value || value.length > 512) {
    return false;
  }
  if (!value.startsWith('/') || value.startsWith('//')) {
    return false;
  }
  if (value.includes('\\') || value.includes('\r') || value.includes('\n')) {
    return false;
  }
  return !value.includes('://');
}

export function restoreSpaResumePath(hrefSearch = typeof window === 'undefined' ? '' : window.location.search): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const resume = new URLSearchParams(hrefSearch).get(SPA_RESUME_QUERY);
  if (!resume || !isSafeSpaResumePath(resume)) {
    return null;
  }
  window.history.replaceState(window.history.state, '', resume);
  return resume;
}
