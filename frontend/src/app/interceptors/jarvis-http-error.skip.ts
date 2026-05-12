/**
 * URLs em que os componentes tratam o erro (evita toast global duplicado).
 */
export const JARVIS_HTTP_ERROR_SKIP_PATH_SUBSTRINGS: readonly string[] = [
  '/usuarios/perfil',
  '/usuarios/perfil-jarvis',
  '/usuarios/preferencia-tratamento',
  '/usuarios/whatsapp/vincular',
  '/usuarios/whatsapp/desvincular',
  '/auth/google',
  '/notificacoes',
  '/relatorios/alertas',
  '/projecoes',
  '/renda-config',
  '/score',
];

export function shouldSkipJarvisHttpError(url: string): boolean {
  return JARVIS_HTTP_ERROR_SKIP_PATH_SUBSTRINGS.some((s) => url.includes(s));
}
